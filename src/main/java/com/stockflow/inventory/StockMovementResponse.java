package com.stockflow.inventory;

import java.time.Instant;

public record StockMovementResponse(Long id, String sku, MovementType type, int quantityChange,
                                    String reference, Instant occurredAt) {

    public static StockMovementResponse from(StockMovement movement) {
        return new StockMovementResponse(movement.getId(), movement.getProduct().getSku(), movement.getType(),
                movement.getQuantityChange(), movement.getReference(), movement.getOccurredAt());
    }
}
