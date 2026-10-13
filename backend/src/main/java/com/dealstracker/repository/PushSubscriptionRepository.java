package com.dealstracker.repository;

import com.dealstracker.domain.PushSubscription;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByUserId(UUID userId);

    @Transactional
    long deleteByUserIdAndEndpoint(UUID userId, String endpoint);
}
