package com.dealstracker.repository;

import com.dealstracker.domain.WebPushKeys;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebPushKeysRepository extends JpaRepository<WebPushKeys, Short> {
}
