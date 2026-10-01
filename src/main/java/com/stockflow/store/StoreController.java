package com.stockflow.store;

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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/stores")
@Tag(name = "Stores", description = "Physical stores of the chain")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @PostMapping
    @Operation(summary = "Register a new store")
    public ResponseEntity<StoreResponse> create(@Valid @RequestBody CreateStoreRequest request) {
        StoreResponse created = storeService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{code}")
                .buildAndExpand(created.code())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "List all stores, ordered by code")
    public List<StoreResponse> list() {
        return storeService.findAll();
    }

    @GetMapping("/{code}")
    @Operation(summary = "Get one store by code")
    public StoreResponse get(@PathVariable String code) {
        return storeService.findByCode(code);
    }
}
