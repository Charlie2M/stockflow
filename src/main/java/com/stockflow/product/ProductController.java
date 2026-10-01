package com.stockflow.product;

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
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Product catalogue")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @Operation(summary = "Register a new product")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse created = productService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{sku}")
                .buildAndExpand(created.sku())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "List all products, ordered by SKU")
    public List<ProductResponse> list() {
        return productService.findAll();
    }

    @GetMapping("/{sku}")
    @Operation(summary = "Get one product by SKU")
    public ProductResponse get(@PathVariable String sku) {
        return productService.findBySku(sku);
    }
}
