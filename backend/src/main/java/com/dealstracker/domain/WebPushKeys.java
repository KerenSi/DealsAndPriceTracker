package com.dealstracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** The server's generated VAPID key pair (a single row). */
@Entity
@Table(name = "web_push_keys")
public class WebPushKeys {

    public static final short ID = 1;

    @Id
    private Short id = ID;

    @Column(name = "public_key", nullable = false)
    private String publicKey;

    @Column(name = "private_key", nullable = false)
    private String privateKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected WebPushKeys() {
    }

    public WebPushKeys(String publicKey, String privateKey) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    public String getPublicKey() { return publicKey; }
    public String getPrivateKey() { return privateKey; }
}
