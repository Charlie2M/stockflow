package com.stockflow.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** "This many units of this SKU". Used by goods receipts and transfer requests. */
public record LineItemRequest(
        @NotBlank String sku,
        @NotNull @Positive Integer quantity) {
}
