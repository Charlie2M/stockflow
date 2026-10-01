package com.stockflow.product;

import java.time.Instant;

public record ProductResponse(Long id, String sku, String name, Instant createdAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getSku(), product.getName(), product.getCreatedAt());
    }
}
