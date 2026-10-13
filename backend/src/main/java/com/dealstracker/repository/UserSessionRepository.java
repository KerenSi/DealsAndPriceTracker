package com.dealstracker.repository;

import com.dealstracker.domain.UserSession;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {

    @EntityGraph(attributePaths = "user")
    Optional<UserSession> findByTokenHashAndExpiresAtAfter(String tokenHash, OffsetDateTime now);

    @Modifying
    @Transactional
    @Query("delete from UserSession s where s.expiresAt <= :now")
    int deleteExpired(OffsetDateTime now);
}
