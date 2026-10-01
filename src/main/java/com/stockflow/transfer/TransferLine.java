package com.stockflow.transfer;

import com.stockflow.common.BusinessRuleViolationException;
import com.stockflow.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "transfer_lines")
public class TransferLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false)
    private Transfer transfer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Units requested, which are also the units dispatched (dispatch is all or nothing). */
    @Column(nullable = false)
    private int quantity;

    /** Units that actually arrived. Null until the transfer is received. */
    @Column(name = "received_quantity")
    private Integer receivedQuantity;

    protected TransferLine() {
        // required by JPA
    }

    TransferLine(Transfer transfer, Product product, int quantity) {
        if (quantity <= 0) {
            throw new BusinessRuleViolationException("Quantity for %s must be positive".formatted(product.getSku()));
        }
        this.transfer = transfer;
        this.product = product;
        this.quantity = quantity;
    }

    void checkReceivable(int received) {
        if (received < 0 || received > quantity) {
            throw new BusinessRuleViolationException(
                    "Received quantity for %s must be between 0 and %d but was %d"
                            .formatted(getSku(), quantity, received));
        }
    }

    void markReceived(int received) {
        checkReceivable(received);
        this.receivedQuantity = received;
    }

    public boolean isFullyReceived() {
        return receivedQuantity != null && receivedQuantity == quantity;
    }

    /** Units lost between stores, or null if the transfer has not been received yet. */
    public Integer getDiscrepancy() {
        return receivedQuantity == null ? null : quantity - receivedQuantity;
    }

    public String getSku() {
        return product.getSku();
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public Integer getReceivedQuantity() {
        return receivedQuantity;
    }
}
