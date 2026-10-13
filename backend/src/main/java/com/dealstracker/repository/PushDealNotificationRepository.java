package com.dealstracker.repository;

import com.dealstracker.domain.ProductInterest;
import com.dealstracker.domain.PushDealNotification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushDealNotificationRepository
        extends JpaRepository<PushDealNotification, ProductInterest.Key> {
}
