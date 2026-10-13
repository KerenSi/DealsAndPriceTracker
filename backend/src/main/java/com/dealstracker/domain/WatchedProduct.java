package com.dealstracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A product a user marked as interesting. Drives which "cheapest" insights they see. */
@Entity
@Table(name = "watched_products")
public class WatchedProduct {

    @EmbeddedId
    private Key id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected WatchedProduct() {
    }

    public WatchedProduct(UUID userId, UUID productId) {
        this.id = new Key(userId, productId);
    }

    public Key getId() { return id; }

    @Embeddable
    public record Key(@Column(name = "user_id") UUID userId,
                      @Column(name = "product_id") UUID productId) implements Serializable {
    }
}
