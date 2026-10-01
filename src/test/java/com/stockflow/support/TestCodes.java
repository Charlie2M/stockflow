package com.stockflow.support;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integration tests share one Spring context and one in-memory database, so every test creates
 * its own stores and products with unique codes instead of cleaning up after itself.
 */
public final class TestCodes {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private TestCodes() {
    }

    public static String storeCode() {
        return "ST-%05d".formatted(SEQUENCE.incrementAndGet());
    }

    public static String sku() {
        return "SKU-%05d".formatted(SEQUENCE.incrementAndGet());
    }
}
