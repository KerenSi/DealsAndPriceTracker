package com.dealstracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "alert_rules")
public class AlertRule {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "target_discount_percentage", nullable = false)
    private BigDecimal targetDiscountPercentage = new BigDecimal("15.00");

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_channel", nullable = false)
    private NotificationChannel notificationChannel;

    @Column(name = "channel_recipient_id", nullable = false)
    private String channelRecipientId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_notified_at")
    private OffsetDateTime lastNotifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected AlertRule() {
    }

    public AlertRule(Product product, NotificationChannel channel, String recipientId) {
        this.product = product;
        this.notificationChannel = channel;
        this.channelRecipientId = recipientId;
    }

    public UUID getId() { return id; }
    public Product getProduct() { return product; }
    public BigDecimal getTargetDiscountPercentage() { return targetDiscountPercentage; }
    public void setTargetDiscountPercentage(BigDecimal v) { this.targetDiscountPercentage = v; }
    public NotificationChannel getNotificationChannel() { return notificationChannel; }
    public String getChannelRecipientId() { return channelRecipientId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public OffsetDateTime getLastNotifiedAt() { return lastNotifiedAt; }
    public void setLastNotifiedAt(OffsetDateTime v) { this.lastNotifiedAt = v; }
}
