package com.stockflow.transfer;

import com.stockflow.common.LineItemRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateTransferRequest(
        @NotBlank String sourceStoreCode,
        @NotBlank String destinationStoreCode,
        @NotEmpty List<@NotNull @Valid LineItemRequest> lines) {
}
