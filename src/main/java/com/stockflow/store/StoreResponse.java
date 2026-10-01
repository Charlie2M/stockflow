package com.stockflow.store;

import java.time.Instant;

public record StoreResponse(Long id, String code, String name, Instant createdAt) {

    public static StoreResponse from(Store store) {
        return new StoreResponse(store.getId(), store.getCode(), store.getName(), store.getCreatedAt());
    }
}
