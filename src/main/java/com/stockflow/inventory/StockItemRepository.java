package com.stockflow.inventory;

import com.stockflow.product.Product;
import com.stockflow.store.Store;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockItemRepository extends JpaRepository<StockItem, Long> {

    Optional<StockItem> findByStoreAndProduct(Store store, Product product);

    /** "join fetch" loads the products in the same query, avoiding one extra query per row (N+1). */
    @Query("select s from StockItem s join fetch s.product p where s.store = :store order by p.sku")
    List<StockItem> findAllByStoreWithProduct(@Param("store") Store store);
}
