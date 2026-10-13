package com.dealstracker.repository;

import com.dealstracker.domain.ProductListing;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductListingRepository extends JpaRepository<ProductListing, UUID> {

    Optional<ProductListing> findByProductIdAndStoreId(UUID productId, UUID storeId);

    @EntityGraph(attributePaths = {"product", "store"})
    List<ProductListing> findByProductId(UUID productId);

    @EntityGraph(attributePaths = {"product", "store"})
    List<ProductListing> findAllBy();
}
