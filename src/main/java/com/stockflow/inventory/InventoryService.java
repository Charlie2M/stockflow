package com.stockflow.inventory;

import com.stockflow.common.LineItemRequest;
import com.stockflow.common.RequestRules;
import com.stockflow.product.Product;
import com.stockflow.product.ProductRepository;
import com.stockflow.store.Store;
import com.stockflow.store.StoreRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns every change to stock levels. {@link #addStock} and {@link #removeStock} are the only code
 * paths that modify a StockItem, and both always write a ledger entry in the same transaction,
 * so the ledger and the stock levels cannot drift apart.
 */
@Service
@Transactional(readOnly = true)
public class InventoryService {

    private final StoreRepository stores;
    private final ProductRepository products;
    private final StockItemRepository stockItems;
    private final StockMovementRepository movements;
    private final Clock clock;

    public InventoryService(StoreRepository stores, ProductRepository products, StockItemRepository stockItems,
                            StockMovementRepository movements, Clock clock) {
        this.stores = stores;
        this.products = products;
        this.stockItems = stockItems;
        this.movements = movements;
        this.clock = clock;
    }

    /** All lines are booked or none are: an unknown SKU on the last line rolls back the whole receipt. */
    @Transactional
    public GoodsReceiptResponse receiveGoods(String storeCode, GoodsReceiptRequest request) {
        RequestRules.requireUniqueSkus(request.lines().stream().map(LineItemRequest::sku).toList());
        Store store = stores.requireByCode(storeCode);

        List<StockLevelResponse> updated = new ArrayList<>();
        for (LineItemRequest line : request.lines()) {
            Product product = products.requireBySku(line.sku());
            StockItem item = addStock(store, product, line.quantity(), MovementType.GOODS_RECEIPT, request.reference());
            updated.add(StockLevelResponse.from(item));
        }
        return new GoodsReceiptResponse(store.getCode(), request.reference(), updated);
    }

    public List<StockLevelResponse> getStock(String storeCode) {
        Store store = stores.requireByCode(storeCode);
        return stockItems.findAllByStoreWithProduct(store).stream().map(StockLevelResponse::from).toList();
    }

    public List<StockMovementResponse> getMovements(String storeCode, String sku) {
        Store store = stores.requireByCode(storeCode);
        List<StockMovement> result = (sku == null || sku.isBlank())
                ? movements.findAllByStoreNewestFirst(store)
                : movements.findAllByStoreAndProductNewestFirst(store, products.requireBySku(sku));
        return result.stream().map(StockMovementResponse::from).toList();
    }

    /**
     * Adds stock and records why. MANDATORY means "only call me inside an existing transaction":
     * the stock change must commit or roll back together with the business action that caused it.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockItem addStock(Store store, Product product, int quantity, MovementType type, String reference) {
        Instant now = Instant.now(clock);
        StockItem item = stockItems.findByStoreAndProduct(store, product)
                .orElseGet(() -> new StockItem(store, product, now));
        item.increase(quantity, now);
        stockItems.save(item);
        movements.save(new StockMovement(store, product, type, quantity, reference, now));
        return item;
    }

    /** Removes stock and records why. Throws {@link InsufficientStockException} if there is not enough. */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockItem removeStock(Store store, Product product, int quantity, MovementType type, String reference) {
        Instant now = Instant.now(clock);
        StockItem item = stockItems.findByStoreAndProduct(store, product)
                .orElseThrow(() -> new InsufficientStockException(store.getCode(), product.getSku(), quantity, 0));
        item.decrease(quantity, now);
        movements.save(new StockMovement(store, product, type, -quantity, reference, now));
        return item;
    }
}
