package com.stockflow.transfer;

import com.stockflow.common.LineItemRequest;
import com.stockflow.common.NotFoundException;
import com.stockflow.common.RequestRules;
import com.stockflow.inventory.InventoryService;
import com.stockflow.inventory.MovementType;
import com.stockflow.product.ProductRepository;
import com.stockflow.store.Store;
import com.stockflow.store.StoreRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransferService {

    private final TransferRepository transfers;
    private final StoreRepository stores;
    private final ProductRepository products;
    private final InventoryService inventory;
    private final Clock clock;

    public TransferService(TransferRepository transfers, StoreRepository stores, ProductRepository products,
                           InventoryService inventory, Clock clock) {
        this.transfers = transfers;
        this.stores = stores;
        this.products = products;
        this.inventory = inventory;
        this.clock = clock;
    }

    @Transactional
    public TransferResponse create(CreateTransferRequest request) {
        Store source = stores.requireByCode(request.sourceStoreCode());
        Store destination = stores.requireByCode(request.destinationStoreCode());

        Transfer transfer = new Transfer(source, destination, Instant.now(clock));
        for (LineItemRequest line : request.lines()) {
            transfer.addLine(products.requireBySku(line.sku()), line.quantity());
        }
        return TransferResponse.from(transfers.save(transfer));
    }

    public TransferResponse get(Long id) {
        return TransferResponse.from(require(id));
    }

    public List<TransferResponse> list(TransferStatus status) {
        List<Transfer> result = status == null
                ? transfers.findAllByOrderByIdDesc()
                : transfers.findAllByStatusOrderByIdDesc(status);
        return result.stream().map(TransferResponse::from).toList();
    }

    /**
     * Ships the goods. All lines leave the source store or none do: if the last line is short on
     * stock, the exception rolls back the stock already removed for the earlier lines.
     */
    @Transactional
    public TransferResponse dispatch(Long id) {
        Transfer transfer = require(id);
        transfer.dispatch(Instant.now(clock));
        for (TransferLine line : transfer.getLines()) {
            inventory.removeStock(transfer.getSourceStore(), line.getProduct(), line.getQuantity(),
                    MovementType.TRANSFER_OUT, transfer.getReference());
        }
        return TransferResponse.from(transfer);
    }

    /**
     * Books what actually arrived into the destination store. Units that never arrived are not
     * added anywhere; they show up as a discrepancy on the transfer line.
     */
    @Transactional
    public TransferResponse receive(Long id, ReceiveTransferRequest request) {
        Map<String, Integer> receivedBySku = new HashMap<>();
        if (request != null && request.lines() != null) {
            RequestRules.requireUniqueSkus(request.lines().stream().map(ReceivedLineRequest::sku).toList());
            request.lines().forEach(line -> receivedBySku.put(line.sku(), line.receivedQuantity()));
        }

        Transfer transfer = require(id);
        transfer.receive(receivedBySku, Instant.now(clock));
        for (TransferLine line : transfer.getLines()) {
            if (line.getReceivedQuantity() > 0) {
                inventory.addStock(transfer.getDestinationStore(), line.getProduct(), line.getReceivedQuantity(),
                        MovementType.TRANSFER_IN, transfer.getReference());
            }
        }
        return TransferResponse.from(transfer);
    }

    @Transactional
    public TransferResponse cancel(Long id) {
        Transfer transfer = require(id);
        transfer.cancel(Instant.now(clock));
        return TransferResponse.from(transfer);
    }

    private Transfer require(Long id) {
        return transfers.findDetailedById(id).orElseThrow(() -> new NotFoundException("Transfer", id));
    }
}
