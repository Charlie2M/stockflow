package com.stockflow.inventory;

import com.stockflow.product.Product;
import com.stockflow.store.Store;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @Query("select m from StockMovement m join fetch m.product where m.store = :store order by m.id desc")
    List<StockMovement> findAllByStoreNewestFirst(@Param("store") Store store);

    @Query("""
            select m from StockMovement m join fetch m.product
            where m.store = :store and m.product = :product
            order by m.id desc
            """)
    List<StockMovement> findAllByStoreAndProductNewestFirst(@Param("store") Store store,
                                                            @Param("product") Product product);
}
