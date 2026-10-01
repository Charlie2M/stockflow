package com.stockflow.product;

import com.stockflow.common.ConflictException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository products;
    private final Clock clock;

    public ProductService(ProductRepository products, Clock clock) {
        this.products = products;
        this.clock = clock;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        if (products.existsBySku(request.sku())) {
            throw new ConflictException("Duplicate product",
                    "A product with SKU '%s' already exists".formatted(request.sku()));
        }
        Product product = products.save(new Product(request.sku(), request.name().trim(), Instant.now(clock)));
        return ProductResponse.from(product);
    }

    public List<ProductResponse> findAll() {
        return products.findAll(Sort.by("sku")).stream().map(ProductResponse::from).toList();
    }

    public ProductResponse findBySku(String sku) {
        return ProductResponse.from(products.requireBySku(sku));
    }
}
