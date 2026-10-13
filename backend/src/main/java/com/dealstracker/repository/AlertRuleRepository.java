package com.dealstracker.repository;

import com.dealstracker.domain.AlertRule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {

    @EntityGraph(attributePaths = "product")
    List<AlertRule> findByActiveTrue();

    @EntityGraph(attributePaths = "product")
    List<AlertRule> findAllBy();
}
