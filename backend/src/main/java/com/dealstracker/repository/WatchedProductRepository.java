package com.dealstracker.repository;

import com.dealstracker.domain.WatchedProduct;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WatchedProductRepository extends JpaRepository<WatchedProduct, WatchedProduct.Key> {

    @Query("select w.id.productId from WatchedProduct w where w.id.userId = :userId")
    List<UUID> findProductIdsByUserId(UUID userId);
}
