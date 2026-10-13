-- Accounts, login sessions and each user's interest in products.

CREATE TABLE users (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                 VARCHAR(255) NOT NULL UNIQUE, -- stored lower-cased
    password_hash         VARCHAR(100) NOT NULL,        -- BCrypt
    show_similar_products BOOLEAN NOT NULL DEFAULT TRUE, -- "cheapest" insights also compare other brands
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE user_sessions (
    token_hash VARCHAR(64) PRIMARY KEY,          -- SHA-256 hex of the bearer token; the token itself is never stored
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_user_sessions_user ON user_sessions (user_id);

CREATE TABLE product_interests (
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    interest   VARCHAR(20) NOT NULL, -- WATCHED, NOT_INTERESTED
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, product_id)
);
