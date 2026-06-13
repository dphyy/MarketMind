-- MarketMind schema. Dropped + recreated on every boot (spring.sql.init.mode=always)
-- so the demo always starts from a known, clean state.

DROP TABLE IF EXISTS guardrail_blocks CASCADE;
DROP TABLE IF EXISTS action_log CASCADE;
DROP TABLE IF EXISTS sentiment_events CASCADE;
DROP TABLE IF EXISTS competitor_snapshots CASCADE;
DROP TABLE IF EXISTS products CASCADE;

-- Seller's own SKUs
CREATE TABLE products (
    id                          VARCHAR(50) PRIMARY KEY,
    name                        VARCHAR(255) NOT NULL,
    category                    VARCHAR(100),
    your_price                  DECIMAL(10,2),
    price_floor                 DECIMAL(10,2),
    price_ceiling               DECIMAL(10,2),
    stock                       INTEGER,
    stock_reserve_min           INTEGER,
    max_daily_price_change_pct  DECIMAL(5,4),
    current_mode                VARCHAR(20) DEFAULT 'HOLD',
    ad_bid                      DECIMAL(10,2),
    updated_at                  TIMESTAMP DEFAULT NOW()
);

-- Competitor listings, append-only time series
CREATE TABLE competitor_snapshots (
    id               BIGSERIAL PRIMARY KEY,
    product_id       VARCHAR(50) REFERENCES products(id),
    competitor_name  VARCHAR(255),
    price            DECIMAL(10,2),
    stock_indicator  TEXT,
    stock_level      VARCHAR(30),
    visual_signals   TEXT[],
    image_url        TEXT,
    data_source      VARCHAR(20) DEFAULT 'LIVE',   -- LIVE or MOCK
    scraped_at       TIMESTAMP DEFAULT NOW()
);

-- Social sentiment per product category
CREATE TABLE sentiment_events (
    id              BIGSERIAL PRIMARY KEY,
    category        VARCHAR(100),
    score_24h       DECIMAL(4,3),
    trend           VARCHAR(50),
    virality_flag   BOOLEAN DEFAULT FALSE,
    virality_source VARCHAR(100),
    top_signal      TEXT,
    recorded_at     TIMESTAMP DEFAULT NOW()
);

-- Everything the agent did or tried to do
CREATE TABLE action_log (
    id                BIGSERIAL PRIMARY KEY,
    product_id        VARCHAR(50) REFERENCES products(id),
    action_type       VARCHAR(50),
    from_value        TEXT,
    to_value          TEXT,
    executed          BOOLEAN DEFAULT FALSE,
    guardrail_blocked BOOLEAN DEFAULT FALSE,
    block_reason      TEXT,
    kimi_explanation  TEXT,
    daytona_job_id    VARCHAR(100),
    created_at        TIMESTAMP DEFAULT NOW()
);

-- Blocked actions, denormalised for the guardrail-log screen
CREATE TABLE guardrail_blocks (
    id             BIGSERIAL PRIMARY KEY,
    product_id     VARCHAR(50) REFERENCES products(id),
    action_type    VARCHAR(50),
    proposed_value TEXT,
    block_reason   TEXT,
    created_at     TIMESTAMP DEFAULT NOW()
);
