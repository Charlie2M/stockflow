package com.stockflow.support;

import com.stockflow.product.Product;
import com.stockflow.store.Store;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Factories for entities in unit tests, where there is no database to assign ids.
 */
public final class TestEntities {

    public static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final AtomicLong IDS = new AtomicLong(1000);

    private TestEntities() {
    }

    public static Store store(String code) {
        return withId(new Store(code, "Store " + code, NOW), IDS.incrementAndGet());
    }

    public static Product product(String sku) {
        return withId(new Product(sku, "Product " + sku, NOW), IDS.incrementAndGet());
    }

    /** Sets the database id the way Hibernate would after an insert. */
    public static <T> T withId(T entity, long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot set id on " + entity.getClass().getSimpleName(), e);
        }
    }
}
