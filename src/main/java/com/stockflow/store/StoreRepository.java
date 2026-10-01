package com.stockflow.store;

import com.stockflow.common.NotFoundException;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByCode(String code);

    boolean existsByCode(String code);

    default Store requireByCode(String code) {
        return findByCode(code).orElseThrow(() -> new NotFoundException("Store", code));
    }
}
