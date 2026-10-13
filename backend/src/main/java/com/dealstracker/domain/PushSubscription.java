package com.dealstracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A browser that allowed deal notifications: its push service endpoint and encryption keys. */
@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true, length = 1024)
    private String endpoint;

    @Column(nullable = false)
    private String p256dh;

    @Column(nullable = false)
    private String auth;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected PushSubscription() {
    }

    public PushSubscription(UUID userId, String endpoint, String p256dh, String auth) {
        this.userId = userId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getEndpoint() { return endpoint; }
    public String getP256dh() { return p256dh; }
    public String getAuth() { return auth; }

    /** A browser can be re-subscribed by another account (shared computer) or rotate its keys. */
    public void update(UUID userId, String p256dh, String auth) {
        this.userId = userId;
        this.p256dh = p256dh;
        this.auth = auth;
    }
}
