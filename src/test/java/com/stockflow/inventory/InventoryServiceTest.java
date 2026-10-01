package com.stockflow.inventory;

import static com.stockflow.support.TestEntities.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.stockflow.common.BusinessRuleViolationException;
import com.stockflow.common.LineItemRequest;
import com.stockflow.product.Product;
import com.stockflow.product.ProductRepository;
import com.stockflow.store.Store;
import com.stockflow.store.StoreRepository;
import com.stockflow.support.TestEntities;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private StoreRepository stores;
    @Mock
    private ProductRepository products;
    @Mock
    private StockItemRepository stockItems;
    @Mock
    private StockMovementRepository movements;

    private InventoryService service;

    private final Store store = TestEntities.store("ST-001");
    private final Product mugs = TestEntities.product("MUG-01");

    @BeforeEach
    void setUp() {
        service = new InventoryService(stores, products, stockItems, movements, TestEntities.CLOCK);
    }

    @Test
    void firstGoodsReceiptCreatesStockAndLedgerEntry() {
        when(stores.requireByCode("ST-001")).thenReturn(store);
        when(products.requireBySku("MUG-01")).thenReturn(mugs);
        when(stockItems.findByStoreAndProduct(store, mugs)).thenReturn(Optional.empty());

        GoodsReceiptResponse response = service.receiveGoods("ST-001",
                new GoodsReceiptRequest("PO-1001", List.of(new LineItemRequest("MUG-01", 12))));

        assertThat(response.stock()).extracting(StockLevelResponse::quantity).containsExactly(12);
        verify(stockItems).save(any(StockItem.class));
        StockMovement movement = savedMovement();
        assertThat(movement.getType()).isEqualTo(MovementType.GOODS_RECEIPT);
        assertThat(movement.getQuantityChange()).isEqualTo(12);
        assertThat(movement.getReference()).isEqualTo("PO-1001");
        assertThat(movement.getOccurredAt()).isEqualTo(NOW);
    }

    @Test
    void goodsReceiptAddsToExistingStock() {
        StockItem existing = stockOf(5);
        when(stores.requireByCode("ST-001")).thenReturn(store);
        when(products.requireBySku("MUG-01")).thenReturn(mugs);
        when(stockItems.findByStoreAndProduct(store, mugs)).thenReturn(Optional.of(existing));

        service.receiveGoods("ST-001", new GoodsReceiptRequest("PO-1002", List.of(new LineItemRequest("MUG-01", 3))));

        assertThat(existing.getQuantity()).isEqualTo(8);
    }

    @Test
    void duplicateSkusInOneReceiptAreRejectedBeforeAnyLookup() {
        GoodsReceiptRequest request = new GoodsReceiptRequest("PO-1003",
                List.of(new LineItemRequest("MUG-01", 1), new LineItemRequest("MUG-01", 2)));

        assertThatThrownBy(() -> service.receiveGoods("ST-001", request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("MUG-01");
        verifyNoMoreInteractions(stores, products, stockItems, movements);
    }

    @Test
    void removeStockRecordsNegativeMovementWithReference() {
        StockItem existing = stockOf(10);
        when(stockItems.findByStoreAndProduct(store, mugs)).thenReturn(Optional.of(existing));

        service.removeStock(store, mugs, 4, MovementType.TRANSFER_OUT, "TRF-9");

        assertThat(existing.getQuantity()).isEqualTo(6);
        StockMovement movement = savedMovement();
        assertThat(movement.getType()).isEqualTo(MovementType.TRANSFER_OUT);
        assertThat(movement.getQuantityChange()).isEqualTo(-4);
        assertThat(movement.getReference()).isEqualTo("TRF-9");
    }

    @Test
    void removingStockThatWasNeverReceivedReportsZeroAvailable() {
        when(stockItems.findByStoreAndProduct(store, mugs)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeStock(store, mugs, 1, MovementType.TRANSFER_OUT, "TRF-10"))
                .isInstanceOfSatisfying(InsufficientStockException.class,
                        ex -> assertThat(ex.getAvailable()).isZero());
        verify(movements, never()).save(any());
    }

    @Test
    void removingMoreThanAvailableWritesNoLedgerEntry() {
        StockItem existing = stockOf(2);
        when(stockItems.findByStoreAndProduct(store, mugs)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.removeStock(store, mugs, 3, MovementType.TRANSFER_OUT, "TRF-11"))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(existing.getQuantity()).isEqualTo(2);
        verify(movements, never()).save(any());
    }

    private StockItem stockOf(int quantity) {
        StockItem item = new StockItem(store, mugs, NOW);
        item.increase(quantity, NOW);
        return item;
    }

    private StockMovement savedMovement() {
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(movements).save(captor.capture());
        return captor.getValue();
    }
}
