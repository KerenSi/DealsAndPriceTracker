package com.dealstracker.repository;

import com.dealstracker.domain.ProductInterest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductInterestRepository extends JpaRepository<ProductInterest, ProductInterest.Key> {

    List<ProductInterest> findByIdUserId(UUID userId);
}
