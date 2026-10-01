package com.stockflow.inventory;

import com.stockflow.product.Product;
import com.stockflow.store.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One immutable line in the stock ledger. The sum of quantityChange for a store and product
 * always equals the current StockItem quantity, which makes every number auditable.
 */
@Entity
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 32)
    private MovementType type;

    @Column(name = "quantity_change", nullable = false)
    private int quantityChange;

    @Column(nullable = false, length = 64)
    private String reference;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected StockMovement() {
        // required by JPA
    }

    public StockMovement(Store store, Product product, MovementType type, int quantityChange,
                         String reference, Instant occurredAt) {
        if (quantityChange == 0) {
            throw new IllegalArgumentException("A stock movement must change the quantity");
        }
        this.store = store;
        this.product = product;
        this.type = type;
        this.quantityChange = quantityChange;
        this.reference = reference;
        this.occurredAt = occurredAt;
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

    public MovementType getType() {
        return type;
    }

    public int getQuantityChange() {
        return quantityChange;
    }

    public String getReference() {
        return reference;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
