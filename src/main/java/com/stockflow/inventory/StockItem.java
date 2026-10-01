package com.stockflow.inventory;

import com.stockflow.product.Product;
import com.stockflow.store.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/** How many units of one product one store currently holds. */
@Entity
@Table(name = "stock_items")
public class StockItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    /**
     * Optimistic locking. Hibernate adds "WHERE version = ?" to every UPDATE of this row, so if two
     * transactions read the same stock and both try to change it, the second one fails instead of
     * silently overwriting the first. That is what prevents overselling under concurrent requests.
     */
    @Version
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockItem() {
        // required by JPA
    }

    public StockItem(Store store, Product product, Instant createdAt) {
        this.store = store;
        this.product = product;
        this.quantity = 0;
        this.updatedAt = createdAt;
    }

    public void increase(int amount, Instant at) {
        requirePositive(amount);
        quantity = Math.addExact(quantity, amount);
        updatedAt = at;
    }

    public void decrease(int amount, Instant at) {
        requirePositive(amount);
        if (amount > quantity) {
            throw new InsufficientStockException(store.getCode(), product.getSku(), amount, quantity);
        }
        quantity -= amount;
        updatedAt = at;
    }

    private static void requirePositive(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive but was " + amount);
        }
    }

    public Long getId() {
        return id;
    }

    public Store getStore() {
        return store;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
