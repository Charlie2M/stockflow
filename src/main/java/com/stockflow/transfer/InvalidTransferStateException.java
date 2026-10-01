package com.stockflow.transfer;

import com.stockflow.common.ConflictException;
import java.util.LinkedHashMap;
import java.util.Map;

public class InvalidTransferStateException extends ConflictException {

    private final TransferStatus currentStatus;

    public InvalidTransferStateException(Long transferId, TransferStatus currentStatus, String action) {
        super("Invalid transfer state",
                "Cannot %s transfer %s because it is %s".formatted(action, transferId, currentStatus),
                details(transferId, currentStatus));
        this.currentStatus = currentStatus;
    }

    public TransferStatus getCurrentStatus() {
        return currentStatus;
    }

    private static Map<String, Object> details(Long transferId, TransferStatus currentStatus) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("transferId", transferId);
        details.put("currentStatus", currentStatus.name());
        return details;
    }
}
