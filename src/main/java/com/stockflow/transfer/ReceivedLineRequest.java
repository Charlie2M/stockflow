package com.stockflow.transfer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ReceivedLineRequest(
        @NotBlank String sku,
        @NotNull @PositiveOrZero Integer receivedQuantity) {
}
