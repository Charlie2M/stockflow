package com.stockflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockflow.common.ConflictException;
import com.stockflow.common.LineItemRequest;
import com.stockflow.inventory.GoodsReceiptRequest;
import com.stockflow.inventory.InventoryService;
import com.stockflow.inventory.StockLevelResponse;
import com.stockflow.inventory.StockMovementResponse;
import com.stockflow.product.CreateProductRequest;
import com.stockflow.product.ProductService;
import com.stockflow.store.CreateStoreRequest;
import com.stockflow.store.StoreService;
import com.stockflow.support.TestCodes;
import com.stockflow.transfer.CreateTransferRequest;
import com.stockflow.transfer.TransferService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.ConcurrencyFailureException;

/**
 * Two clerks dispatch at the same moment and both want 8 of the 10 units on the shelf.
 * Whatever the timing, exactly one of them may succeed and the stock may never go negative.
 * <p>
 * Depending on how the two transactions interleave, the loser fails either with
 * "Insufficient stock" (it saw the updated quantity) or with an optimistic locking error
 * (it read the old quantity, but the version check caught it). Both are reported as 409.
 */
@SpringBootTest
class ConcurrentDispatchIntegrationTest {

    @Autowired
    private StoreService storeService;
    @Autowired
    private ProductService productService;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private TransferService transferService;

    @Test
    void twoTransfersCompetingForTheSameStockCannotBothShip() throws Exception {
        String warehouse = store();
        String mugs = product();
        inventoryService.receiveGoods(warehouse,
                new GoodsReceiptRequest("PO-1", List.of(new LineItemRequest(mugs, 10))));
        long first = transferService.create(transfer(warehouse, store(), mugs, 8)).id();
        long second = transferService.create(transfer(warehouse, store(), mugs, 8)).id();

        List<Throwable> failures = runAtTheSameTime(
                () -> transferService.dispatch(first),
                () -> transferService.dispatch(second));

        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).isInstanceOfAny(ConflictException.class, ConcurrencyFailureException.class);
        assertStockAndLedgerAgree(warehouse, mugs, 2);
    }

    @Test
    void theSameTransferCannotBeDispatchedTwice() throws Exception {
        String warehouse = store();
        String mugs = product();
        inventoryService.receiveGoods(warehouse,
                new GoodsReceiptRequest("PO-2", List.of(new LineItemRequest(mugs, 10))));
        long transferId = transferService.create(transfer(warehouse, store(), mugs, 8)).id();

        List<Throwable> failures = runAtTheSameTime(
                () -> transferService.dispatch(transferId),
                () -> transferService.dispatch(transferId));

        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).isInstanceOfAny(ConflictException.class, ConcurrencyFailureException.class);
        assertStockAndLedgerAgree(warehouse, mugs, 2);
    }

    private void assertStockAndLedgerAgree(String storeCode, String sku, int expected) {
        List<StockLevelResponse> stock = inventoryService.getStock(storeCode);
        assertThat(stock).hasSize(1);
        assertThat(stock.get(0).quantity()).isEqualTo(expected);

        int ledgerSum = inventoryService.getMovements(storeCode, sku).stream()
                .mapToInt(StockMovementResponse::quantityChange)
                .sum();
        assertThat(ledgerSum).isEqualTo(expected);
    }

    /** Starts both tasks together and returns the exceptions of the ones that failed. */
    private static List<Throwable> runAtTheSameTime(Callable<?> a, Callable<?> b) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startSignal = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Callable<?> task : List.of(a, b)) {
                futures.add(pool.submit(() -> {
                    startSignal.await();
                    return task.call();
                }));
            }
            startSignal.countDown();

            List<Throwable> failures = new ArrayList<>();
            for (Future<?> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                } catch (TimeoutException e) {
                    throw new AssertionError("Dispatch did not finish in time", e);
                }
            }
            return failures;
        } finally {
            pool.shutdownNow();
        }
    }

    private String store() {
        String code = TestCodes.storeCode();
        storeService.create(new CreateStoreRequest(code, "Store " + code));
        return code;
    }

    private String product() {
        String sku = TestCodes.sku();
        productService.create(new CreateProductRequest(sku, "Product " + sku));
        return sku;
    }

    private static CreateTransferRequest transfer(String from, String to, String sku, int quantity) {
        return new CreateTransferRequest(from, to, List.of(new LineItemRequest(sku, quantity)));
    }
}
