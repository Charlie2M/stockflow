package com.stockflow.transfer;

import com.stockflow.common.BusinessRuleViolationException;
import com.stockflow.product.Product;
import com.stockflow.store.Store;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A request to move stock from one store to another.
 * <p>
 * The state machine lives here, in the entity, instead of in the service: whatever code calls
 * {@link #dispatch}, {@link #receive} or {@link #cancel}, an illegal transition is impossible.
 * See {@link TransferStatus} for the allowed transitions.
 */
@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_store_id", nullable = false)
    private Store sourceStore;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_store_id", nullable = false)
    private Store destinationStore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TransferStatus status;

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<TransferLine> lines = new ArrayList<>();

    /** Optimistic locking: two clients dispatching the same transfer at once cannot both succeed. */
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Transfer() {
        // required by JPA
    }

    public Transfer(Store sourceStore, Store destinationStore, Instant createdAt) {
        if (sourceStore.getCode().equals(destinationStore.getCode())) {
            throw new BusinessRuleViolationException("Source and destination store must be different");
        }
        this.sourceStore = sourceStore;
        this.destinationStore = destinationStore;
        this.status = TransferStatus.REQUESTED;
        this.createdAt = createdAt;
    }

    public void addLine(Product product, int quantity) {
        requireStatus(TransferStatus.REQUESTED, "add lines to");
        boolean alreadyPresent = lines.stream().anyMatch(line -> line.getSku().equals(product.getSku()));
        if (alreadyPresent) {
            throw new BusinessRuleViolationException(
                    "Product %s appears more than once in the transfer".formatted(product.getSku()));
        }
        lines.add(new TransferLine(this, product, quantity));
    }

    /** Marks the goods as shipped. The service removes the stock from the source store. */
    public void dispatch(Instant at) {
        requireStatus(TransferStatus.REQUESTED, "dispatch");
        if (lines.isEmpty()) {
            throw new BusinessRuleViolationException("A transfer without lines cannot be dispatched");
        }
        status = TransferStatus.DISPATCHED;
        dispatchedAt = at;
    }

    /**
     * Records what actually arrived at the destination.
     *
     * @param receivedBySku received units per SKU; SKUs that are not listed are assumed to have
     *                      arrived in full
     */
    public void receive(Map<String, Integer> receivedBySku, Instant at) {
        requireStatus(TransferStatus.DISPATCHED, "receive");
        for (String sku : receivedBySku.keySet()) {
            if (lines.stream().noneMatch(line -> line.getSku().equals(sku))) {
                throw new BusinessRuleViolationException(
                        "Product %s is not part of transfer %s".formatted(sku, id));
            }
        }
        // Validate every line first so a bad value on the last line leaves nothing half-updated.
        lines.forEach(line -> line.checkReceivable(receivedFor(line, receivedBySku)));
        lines.forEach(line -> line.markReceived(receivedFor(line, receivedBySku)));

        boolean complete = lines.stream().allMatch(TransferLine::isFullyReceived);
        status = complete ? TransferStatus.RECEIVED : TransferStatus.RECEIVED_WITH_DISCREPANCY;
        completedAt = at;
    }

    public void cancel(Instant at) {
        requireStatus(TransferStatus.REQUESTED, "cancel");
        status = TransferStatus.CANCELLED;
        completedAt = at;
    }

    /** Written into the stock ledger so every movement can be traced back to its transfer. */
    public String getReference() {
        return "TRF-" + id;
    }

    private static int receivedFor(TransferLine line, Map<String, Integer> receivedBySku) {
        return receivedBySku.getOrDefault(line.getSku(), line.getQuantity());
    }

    private void requireStatus(TransferStatus expected, String action) {
        if (status != expected) {
            throw new InvalidTransferStateException(id, status, action);
        }
    }

    public Long getId() {
        return id;
    }

    public Store getSourceStore() {
        return sourceStore;
    }

    public Store getDestinationStore() {
        return destinationStore;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public List<TransferLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
