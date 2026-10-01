package com.stockflow.transfer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * What arrived at the destination store. The whole body is optional: no body, or no line for a
 * SKU, means that SKU arrived in full. Only shortages need to be reported.
 */
public record ReceiveTransferRequest(List<@NotNull @Valid ReceivedLineRequest> lines) {
}
