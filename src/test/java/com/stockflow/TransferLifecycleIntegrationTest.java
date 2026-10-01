package com.stockflow;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.stockflow.support.TestCodes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Runs the real application (Flyway schema, JPA, transactions, JSON) against an in-memory
 * database and drives it only through the HTTP API, the way a client would.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TransferLifecycleIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void shortDeliveryIsBookedAsDiscrepancyAndTheLedgerStillAddsUp() throws Exception {
        String warehouse = createStore();
        String mall = createStore();
        String mugs = createProduct();
        String candles = createProduct();
        receiveGoods(warehouse, mugs, 20);
        receiveGoods(warehouse, candles, 5);

        long transferId = createTransfer(warehouse, mall, mugs, 8, candles, 5);

        mvc.perform(post("/api/v1/transfers/{id}/dispatch", transferId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"));
        mvc.perform(get("/api/v1/stores/{code}/stock", warehouse))
                .andExpect(jsonPath("$[?(@.sku == '%s')].quantity".formatted(mugs), contains(12)))
                .andExpect(jsonPath("$[?(@.sku == '%s')].quantity".formatted(candles), contains(0)));

        // Two mugs broke on the way. Candles are not mentioned, so they all arrived.
        mvc.perform(post("/api/v1/transfers/{id}/receive", transferId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "lines": [ { "sku": "%s", "receivedQuantity": 6 } ] }
                        """.formatted(mugs)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED_WITH_DISCREPANCY"))
                .andExpect(jsonPath("$.lines[0].discrepancy").value(2))
                .andExpect(jsonPath("$.lines[1].discrepancy").value(0));

        mvc.perform(get("/api/v1/stores/{code}/stock", mall))
                .andExpect(jsonPath("$[?(@.sku == '%s')].quantity".formatted(mugs), contains(6)))
                .andExpect(jsonPath("$[?(@.sku == '%s')].quantity".formatted(candles), contains(5)));

        // The ledger explains the stock level: +20 received, -8 sent away = 12 on the shelf.
        mvc.perform(get("/api/v1/stores/{code}/movements", warehouse).param("sku", mugs))
                .andExpect(jsonPath("$[*].type", contains("TRANSFER_OUT", "GOODS_RECEIPT")))
                .andExpect(jsonPath("$[*].quantityChange", contains(-8, 20)));
        mvc.perform(get("/api/v1/stores/{code}/movements", mall))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].type", everyItem(is("TRANSFER_IN"))))
                .andExpect(jsonPath("$[*].reference", everyItem(is("TRF-" + transferId))));
    }

    @Test
    void dispatchIsAllOrNothingWhenOneLineIsShortOnStock() throws Exception {
        String warehouse = createStore();
        String mall = createStore();
        String mugs = createProduct();
        String candles = createProduct();
        receiveGoods(warehouse, mugs, 10);
        receiveGoods(warehouse, candles, 1);
        long transferId = createTransfer(warehouse, mall, mugs, 5, candles, 3);

        mvc.perform(post("/api/v1/transfers/{id}/dispatch", transferId))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Insufficient stock"))
                .andExpect(jsonPath("$.sku").value(candles))
                .andExpect(jsonPath("$.requested").value(3))
                .andExpect(jsonPath("$.available").value(1));

        // The mugs line was processed first, but its stock removal was rolled back.
        mvc.perform(get("/api/v1/stores/{code}/stock", warehouse))
                .andExpect(jsonPath("$[?(@.sku == '%s')].quantity".formatted(mugs), contains(10)));
        mvc.perform(get("/api/v1/stores/{code}/movements", warehouse))
                .andExpect(jsonPath("$[*].type", contains("GOODS_RECEIPT", "GOODS_RECEIPT")));
        mvc.perform(get("/api/v1/transfers/{id}", transferId))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void cancelledTransferCannotBeDispatched() throws Exception {
        String warehouse = createStore();
        String mall = createStore();
        String mugs = createProduct();
        receiveGoods(warehouse, mugs, 10);
        long transferId = createTransfer(warehouse, mall, mugs, 5);

        mvc.perform(post("/api/v1/transfers/{id}/cancel", transferId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/v1/transfers/{id}/dispatch", transferId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("CANCELLED"));
        mvc.perform(get("/api/v1/stores/{code}/stock", warehouse))
                .andExpect(jsonPath("$[0].quantity").value(10));
    }

    @Test
    void receivingMoreThanWasShippedIsRejected() throws Exception {
        String warehouse = createStore();
        String mall = createStore();
        String mugs = createProduct();
        receiveGoods(warehouse, mugs, 10);
        long transferId = createTransfer(warehouse, mall, mugs, 5);
        mvc.perform(post("/api/v1/transfers/{id}/dispatch", transferId)).andExpect(status().isOk());

        mvc.perform(post("/api/v1/transfers/{id}/receive", transferId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "lines": [ { "sku": "%s", "receivedQuantity": 6 } ] }
                        """.formatted(mugs)))
                .andExpect(status().isUnprocessableContent());

        mvc.perform(get("/api/v1/stores/{code}/stock", mall)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/transfers/{id}", transferId)).andExpect(jsonPath("$.status").value("DISPATCHED"));
    }

    @Test
    void goodsReceiptWithAnUnknownProductBooksNothing() throws Exception {
        String store = createStore();
        String mugs = createProduct();

        mvc.perform(post("/api/v1/stores/{code}/goods-receipts", store)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "reference": "PO-2026-0002",
                          "lines": [
                            { "sku": "%s", "quantity": 5 },
                            { "sku": "DOES-NOT-EXIST", "quantity": 1 }
                          ]
                        }
                        """.formatted(mugs)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product 'DOES-NOT-EXIST' was not found"));

        mvc.perform(get("/api/v1/stores/{code}/stock", store)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/stores/{code}/movements", store)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void duplicateProductIsAConflict() throws Exception {
        String sku = createProduct();

        mvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "sku": "%s", "name": "Another name" }
                        """.formatted(sku)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate product"));
    }

    @Test
    void invalidProductReturnsProblemDetailWithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "sku": "not a sku", "name": "" }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/v1/products"))
                .andExpect(jsonPath("$.errors[*].field", contains("name", "sku")));
    }

    @Test
    void unknownStoreIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/stores/{code}/stock", "NO-SUCH-STORE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    private String createStore() throws Exception {
        String code = TestCodes.storeCode();
        mvc.perform(post("/api/v1/stores")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "code": "%s", "name": "Store %s" }
                        """.formatted(code, code)))
                .andExpect(status().isCreated());
        return code;
    }

    private String createProduct() throws Exception {
        String sku = TestCodes.sku();
        mvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "sku": "%s", "name": "Product %s" }
                        """.formatted(sku, sku)))
                .andExpect(status().isCreated());
        return sku;
    }

    private void receiveGoods(String storeCode, String sku, int quantity) throws Exception {
        mvc.perform(post("/api/v1/stores/{code}/goods-receipts", storeCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "reference": "PO-2026-0001", "lines": [ { "sku": "%s", "quantity": %d } ] }
                        """.formatted(sku, quantity)))
                .andExpect(status().isCreated());
    }

    private long createTransfer(String from, String to, String sku, int quantity) throws Exception {
        return createTransfer(from, to, """
                [ { "sku": "%s", "quantity": %d } ]
                """.formatted(sku, quantity));
    }

    private long createTransfer(String from, String to, String sku1, int quantity1, String sku2, int quantity2)
            throws Exception {
        return createTransfer(from, to, """
                [ { "sku": "%s", "quantity": %d }, { "sku": "%s", "quantity": %d } ]
                """.formatted(sku1, quantity1, sku2, quantity2));
    }

    private long createTransfer(String from, String to, String linesJson) throws Exception {
        String body = mvc.perform(post("/api/v1/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "sourceStoreCode": "%s", "destinationStoreCode": "%s", "lines": %s }
                        """.formatted(from, to, linesJson)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
