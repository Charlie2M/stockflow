package com.stockflow.inventory;

import com.stockflow.common.LineItemRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Goods delivered by a supplier.
 *
 * @param reference the supplier's delivery note or purchase order number, stored in the ledger
 */
public record GoodsReceiptRequest(
        @NotBlank @Size(max = 64) String reference,
        @NotEmpty List<@NotNull @Valid LineItemRequest> lines) {
}
