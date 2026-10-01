package com.stockflow.transfer;

public record TransferLineResponse(String sku, String productName, int quantity,
                                   Integer receivedQuantity, Integer discrepancy) {

    public static TransferLineResponse from(TransferLine line) {
        return new TransferLineResponse(line.getSku(), line.getProduct().getName(), line.getQuantity(),
                line.getReceivedQuantity(), line.getDiscrepancy());
    }
}
