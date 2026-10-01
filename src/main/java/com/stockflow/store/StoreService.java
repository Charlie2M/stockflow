package com.stockflow.store;

import com.stockflow.common.ConflictException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StoreService {

    private final StoreRepository stores;
    private final Clock clock;

    public StoreService(StoreRepository stores, Clock clock) {
        this.stores = stores;
        this.clock = clock;
    }

    @Transactional
    public StoreResponse create(CreateStoreRequest request) {
        if (stores.existsByCode(request.code())) {
            throw new ConflictException("Duplicate store",
                    "A store with code '%s' already exists".formatted(request.code()));
        }
        Store store = stores.save(new Store(request.code(), request.name().trim(), Instant.now(clock)));
        return StoreResponse.from(store);
    }

    public List<StoreResponse> findAll() {
        return stores.findAll(Sort.by("code")).stream().map(StoreResponse::from).toList();
    }

    public StoreResponse findByCode(String code) {
        return StoreResponse.from(stores.requireByCode(code));
    }
}
