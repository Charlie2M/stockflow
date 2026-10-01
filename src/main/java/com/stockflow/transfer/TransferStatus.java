package com.stockflow.transfer;

/**
 * Lifecycle of a transfer:
 * <pre>
 * REQUESTED --dispatch--> DISPATCHED --receive--> RECEIVED
 *     |                                      \--> RECEIVED_WITH_DISCREPANCY
 *     \--cancel--> CANCELLED
 * </pre>
 * Every other move is rejected with {@link InvalidTransferStateException}.
 */
public enum TransferStatus {
    REQUESTED,
    DISPATCHED,
    RECEIVED,
    RECEIVED_WITH_DISCREPANCY,
    CANCELLED
}
