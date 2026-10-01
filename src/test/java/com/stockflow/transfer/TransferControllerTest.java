package com.stockflow.transfer;

import static com.stockflow.support.TestEntities.NOW;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockflow.common.BusinessRuleViolationException;
import com.stockflow.common.NotFoundException;
import com.stockflow.inventory.InsufficientStockException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web layer only: JSON mapping, validation and the error format. The service is mocked.
 */
@WebMvcTest(TransferController.class)
class TransferControllerTest {

    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TransferService transferService;

    @Test
    void createReturns201WithLocationOfTheNewTransfer() throws Exception {
        when(transferService.create(any())).thenReturn(response(42L, TransferStatus.REQUESTED));

        mvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON).content("""
                        {
                          "sourceStoreCode": "ST-SRC",
                          "destinationStoreCode": "ST-DST",
                          "lines": [ { "sku": "MUG-01", "quantity": 10 } ]
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/transfers/42"))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.lines[0].sku").value("MUG-01"));
    }

    @Test
    void invalidRequestReturnsEveryFieldErrorAtOnce() throws Exception {
        mvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON).content("""
                        {
                          "sourceStoreCode": "",
                          "destinationStoreCode": "ST-DST",
                          "lines": [ { "sku": "MUG-01", "quantity": 0 } ]
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("lines[0].quantity", "sourceStoreCode")));

        verifyNoInteractions(transferService);
    }

    @Test
    void businessRuleViolationReturns422() throws Exception {
        when(transferService.create(any()))
                .thenThrow(new BusinessRuleViolationException("Source and destination store must be different"));

        mvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON).content("""
                        {
                          "sourceStoreCode": "ST-SRC",
                          "destinationStoreCode": "ST-SRC",
                          "lines": [ { "sku": "MUG-01", "quantity": 1 } ]
                        }
                        """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Business rule violated"))
                .andExpect(jsonPath("$.detail").value("Source and destination store must be different"));
    }

    @Test
    void illegalTransitionReturns409WithCurrentStatus() throws Exception {
        when(transferService.dispatch(42L))
                .thenThrow(new InvalidTransferStateException(42L, TransferStatus.CANCELLED, "dispatch"));

        mvc.perform(post("/api/v1/transfers/42/dispatch"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid transfer state"))
                .andExpect(jsonPath("$.detail").value("Cannot dispatch transfer 42 because it is CANCELLED"))
                .andExpect(jsonPath("$.transferId").value(42))
                .andExpect(jsonPath("$.currentStatus").value("CANCELLED"));
    }

    @Test
    void insufficientStockReturns409WithTheNumbers() throws Exception {
        when(transferService.dispatch(42L)).thenThrow(new InsufficientStockException("ST-SRC", "MUG-01", 10, 3));

        mvc.perform(post("/api/v1/transfers/42/dispatch"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Insufficient stock"))
                .andExpect(jsonPath("$.sku").value("MUG-01"))
                .andExpect(jsonPath("$.requested").value(10))
                .andExpect(jsonPath("$.available").value(3));
    }

    @Test
    void unknownTransferReturns404() throws Exception {
        when(transferService.get(99L)).thenThrow(new NotFoundException("Transfer", 99L));

        mvc.perform(get("/api/v1/transfers/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.detail").value("Transfer '99' was not found"));
    }

    @Test
    void receiveWorksWithoutABody() throws Exception {
        when(transferService.receive(42L, null)).thenReturn(response(42L, TransferStatus.RECEIVED));

        mvc.perform(post("/api/v1/transfers/42/receive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        verify(transferService).receive(42L, null);
    }

    @Test
    void unknownStatusFilterIsABadRequest() throws Exception {
        mvc.perform(get("/api/v1/transfers").param("status", "LOST_IN_SPACE"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));

        verifyNoInteractions(transferService);
    }

    private static TransferResponse response(Long id, TransferStatus status) {
        return new TransferResponse(id, "ST-SRC", "ST-DST", status,
                List.of(new TransferLineResponse("MUG-01", "Ceramic mug", 10, null, null)),
                NOW, null, null);
    }
}
