package com.stockflow.inventory;

import com.stockflow.common.ConflictException;
import java.util.LinkedHashMap;
import java.util.Map;

public class InsufficientStockException extends ConflictException {

    private final int requested;
    private final int available;

    public InsufficientStockException(String storeCode, String sku, int requested, int available) {
        super("Insufficient stock",
                "Store %s has %d unit(s) of %s but %d were requested".formatted(storeCode, available, sku, requested),
                details(storeCode, sku, requested, available));
        this.requested = requested;
        this.available = available;
    }

    public int getRequested() {
        return requested;
    }

    public int getAvailable() {
        return available;
    }

    private static Map<String, Object> details(String storeCode, String sku, int requested, int available) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("storeCode", storeCode);
        details.put("sku", sku);
        details.put("requested", requested);
        details.put("available", available);
        return details;
    }
}
