package com.stockflow.transfer;

import java.time.Instant;
import java.util.List;

public record TransferResponse(
        Long id,
        String sourceStoreCode,
        String destinationStoreCode,
        TransferStatus status,
        List<TransferLineResponse> lines,
        Instant createdAt,
        Instant dispatchedAt,
        Instant completedAt) {

    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getSourceStore().getCode(),
                transfer.getDestinationStore().getCode(),
                transfer.getStatus(),
                transfer.getLines().stream().map(TransferLineResponse::from).toList(),
                transfer.getCreatedAt(),
                transfer.getDispatchedAt(),
                transfer.getCompletedAt());
    }
}
