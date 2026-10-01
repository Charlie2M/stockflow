package com.stockflow.product;

import com.stockflow.common.NotFoundException;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    default Product requireBySku(String sku) {
        return findBySku(sku).orElseThrow(() -> new NotFoundException("Product", sku));
    }
}
