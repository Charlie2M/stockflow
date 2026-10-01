package com.stockflow.inventory;

import java.util.List;

public record GoodsReceiptResponse(String storeCode, String reference, List<StockLevelResponse> stock) {
}
