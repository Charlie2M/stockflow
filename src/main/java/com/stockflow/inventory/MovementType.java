package com.stockflow.inventory;

/** Why a stock level changed. Every row in the ledger has exactly one of these. */
public enum MovementType {
    /** Goods arrived from a supplier. */
    GOODS_RECEIPT,
    /** Goods left this store as part of a transfer. */
    TRANSFER_OUT,
    /** Goods arrived at this store as part of a transfer. */
    TRANSFER_IN
}
