package com.dealstracker.repository;

import com.dealstracker.domain.Store;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, UUID> {

    List<Store> findByChainAndEnabledTrue(String chain);

    List<Store> findByEnabledTrueOrderByChain();
}
