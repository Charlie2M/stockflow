package com.stockflow.inventory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeCode}")
@Tag(name = "Inventory", description = "Goods receipts, stock levels and the stock movement ledger")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/goods-receipts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Book goods delivered by a supplier into a store")
    public GoodsReceiptResponse receiveGoods(@PathVariable String storeCode,
                                             @Valid @RequestBody GoodsReceiptRequest request) {
        return inventoryService.receiveGoods(storeCode, request);
    }

    @GetMapping("/stock")
    @Operation(summary = "Current stock levels of a store")
    public List<StockLevelResponse> stock(@PathVariable String storeCode) {
        return inventoryService.getStock(storeCode);
    }

    @GetMapping("/movements")
    @Operation(summary = "Stock ledger of a store, newest first, optionally filtered by SKU")
    public List<StockMovementResponse> movements(@PathVariable String storeCode,
                                                 @RequestParam(required = false) String sku) {
        return inventoryService.getMovements(storeCode, sku);
    }
}
