-- Browser push notifications (standard Web Push with VAPID).

-- The server's VAPID key pair, generated on first start unless VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY are set.
-- Subscriptions are bound to the public key, so it must not change once browsers have subscribed.
CREATE TABLE web_push_keys (
    id          SMALLINT PRIMARY KEY CHECK (id = 1),
    public_key  VARCHAR(100) NOT NULL, -- base64url uncompressed P-256 point
    private_key VARCHAR(100) NOT NULL, -- base64url raw scalar
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- One row per browser that allowed notifications.
CREATE TABLE push_subscriptions (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    endpoint   VARCHAR(1024) NOT NULL UNIQUE,
    p256dh     VARCHAR(200) NOT NULL,
    auth       VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_push_subscriptions_user ON push_subscriptions (user_id);

-- The last deal each user was pushed for each product, so an ongoing deal isn't announced every day.
CREATE TABLE push_deal_notifications (
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    product_id      UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    effective_price NUMERIC(10, 2) NOT NULL,
    notified_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id, product_id)
);
