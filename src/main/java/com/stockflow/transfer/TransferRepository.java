package com.stockflow.transfer;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    /** Loads the transfer, both stores and all lines with their products in a single query. */
    @EntityGraph(attributePaths = {"sourceStore", "destinationStore", "lines", "lines.product"})
    Optional<Transfer> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"sourceStore", "destinationStore", "lines", "lines.product"})
    List<Transfer> findAllByOrderByIdDesc();

    @EntityGraph(attributePaths = {"sourceStore", "destinationStore", "lines", "lines.product"})
    List<Transfer> findAllByStatusOrderByIdDesc(TransferStatus status);
}
