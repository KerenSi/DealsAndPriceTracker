-- Core schema for the Deals & Price Tracker MVP.
-- Adapted from the design doc: a product is a canonical item (identified by barcode)
-- and each store that sells it has a listing; price snapshots hang off listings so
-- the same product can be compared across stores.

CREATE TABLE stores (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chain       VARCHAR(50)  NOT NULL, -- e.g. YOCHANANOF, OSHER_AD, CARREFOUR, SUPER_PHARM, GOOD_PHARM, TIV_TAAM
    branch_name VARCHAR(150) NOT NULL,
    city        VARCHAR(100) NOT NULL,
    address     VARCHAR(255),
    chain_code  VARCHAR(20),           -- ChainId in the Price Transparency Law files
    store_code  VARCHAR(20),           -- StoreId in the Price Transparency Law files
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (chain, store_code)
);

CREATE TABLE products (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    barcode       VARCHAR(32)  NOT NULL UNIQUE,
    title         VARCHAR(255) NOT NULL,
    brand         VARCHAR(100),
    category      VARCHAR(50)  NOT NULL, -- DIAPERS, BABY_FOOD
    size_label    VARCHAR(50),           -- e.g. "Size 4" for diapers, "Stage 1" for formula
    unit_quantity NUMERIC(10, 2),        -- 44 (diapers), 700 (grams)
    unit_type     VARCHAR(20),           -- UNITS, GRAMS, ML
    image_url     TEXT,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_products_category ON products (category);

CREATE TABLE product_listings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id  UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    store_id    UUID NOT NULL REFERENCES stores (id) ON DELETE CASCADE,
    product_url TEXT,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (product_id, store_id)
);

CREATE TABLE price_snapshots (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id               UUID NOT NULL REFERENCES product_listings (id) ON DELETE CASCADE,
    price                    NUMERIC(10, 2) NOT NULL,
    original_reference_price NUMERIC(10, 2),
    is_promotion             BOOLEAN NOT NULL DEFAULT FALSE,
    promotion_description    TEXT,
    promo_min_quantity       INTEGER,        -- multi-buy: "3 for 50" -> 3
    promo_total_price        NUMERIC(10, 2), -- multi-buy: "3 for 50" -> 50
    scraped_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_price_snapshots_listing_time ON price_snapshots (listing_id, scraped_at DESC);

CREATE TABLE alert_rules (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id                 UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    target_discount_percentage NUMERIC(5, 2) NOT NULL DEFAULT 15.00,
    notification_channel       VARCHAR(50)  NOT NULL, -- TELEGRAM, WHATSAPP, WEB_PUSH
    channel_recipient_id       VARCHAR(255) NOT NULL,
    is_active                  BOOLEAN NOT NULL DEFAULT TRUE,
    last_notified_at           TIMESTAMP WITH TIME ZONE,
    created_at                 TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
