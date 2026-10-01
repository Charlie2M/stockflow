package com.stockflow.transfer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers", description = "Move stock between stores: REQUESTED, DISPATCHED, RECEIVED")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @Operation(summary = "Request a transfer between two stores")
    public ResponseEntity<TransferResponse> create(@Valid @RequestBody CreateTransferRequest request) {
        TransferResponse created = transferService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "List transfers, newest first, optionally filtered by status")
    public List<TransferResponse> list(@RequestParam(required = false) TransferStatus status) {
        return transferService.list(status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one transfer")
    public TransferResponse get(@PathVariable Long id) {
        return transferService.get(id);
    }

    @PostMapping("/{id}/dispatch")
    @Operation(summary = "Ship the goods: removes the stock from the source store")
    public TransferResponse dispatch(@PathVariable Long id) {
        return transferService.dispatch(id);
    }

    @PostMapping("/{id}/receive")
    @Operation(summary = "Book the goods into the destination store. Body is optional; "
            + "only report lines that arrived short")
    public TransferResponse receive(@PathVariable Long id,
                                    @Valid @RequestBody(required = false) ReceiveTransferRequest request) {
        return transferService.receive(id, request);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a transfer that has not been dispatched yet")
    public TransferResponse cancel(@PathVariable Long id) {
        return transferService.cancel(id);
    }
}
