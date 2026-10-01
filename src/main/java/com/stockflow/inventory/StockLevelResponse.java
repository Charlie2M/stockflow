package com.stockflow.inventory;

import java.time.Instant;

public record StockLevelResponse(String sku, String productName, int quantity, Instant updatedAt) {

    public static StockLevelResponse from(StockItem item) {
        return new StockLevelResponse(item.getProduct().getSku(), item.getProduct().getName(),
                item.getQuantity(), item.getUpdatedAt());
    }
}
