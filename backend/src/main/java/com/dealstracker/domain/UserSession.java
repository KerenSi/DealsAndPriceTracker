package com.dealstracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** A logged-in browser. Only the SHA-256 of the bearer token is kept, so a DB leak can't be replayed. */
@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    @Column(name = "token_hash")
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    protected UserSession() {
    }

    public UserSession(String tokenHash, User user, OffsetDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.user = user;
        this.expiresAt = expiresAt;
    }

    public String getTokenHash() { return tokenHash; }
    public User getUser() { return user; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
}
