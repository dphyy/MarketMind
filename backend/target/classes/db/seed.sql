-- Mock seed data. This is the guaranteed fallback that makes the demo work
-- before any live sponsor API is wired in.

-- ---------- Products (seller's SKUs) ----------
INSERT INTO products
    (id, name, category, your_price, price_floor, price_ceiling, stock, stock_reserve_min, max_daily_price_change_pct, current_mode, ad_bid)
VALUES
    ('SKU-001', 'ProSound Wireless Earbuds X3', 'wireless_earbuds',  49.90, 38.00, 65.00, 120, 30, 0.1500, 'HOLD',    1.20),
    ('SKU-002', 'UltraCharge 20000mAh Power Bank', 'power_banks',    34.90, 26.00, 45.00,  45, 15, 0.1500, 'HOLD',    0.85),
    ('SKU-003', 'FlexGrip Phone Stand Pro', 'phone_accessories',     18.90, 12.00, 25.00, 200, 50, 0.1500, 'HARVEST', 0.60);

-- ---------- Competitor snapshots ----------
INSERT INTO competitor_snapshots
    (product_id, competitor_name, price, stock_indicator, stock_level, visual_signals, data_source, scraped_at)
VALUES
    ('SKU-001', 'AudioZone SG',  47.50, 'Only 2 left!', 'CRITICALLY_LOW', ARRAY['clearance_banner']::text[],  'MOCK', '2025-06-12T02:47:00'),
    ('SKU-001', 'TechDealsSG',   51.00, 'In Stock',     'NORMAL',         ARRAY[]::text[],                    'MOCK', '2025-06-12T02:47:00'),
    ('SKU-002', 'PowerUp Store', 32.90, 'In Stock',     'NORMAL',         ARRAY['flash_sale_banner']::text[], 'MOCK', '2025-06-12T02:47:00');

-- ---------- Sentiment events ----------
INSERT INTO sentiment_events
    (category, score_24h, trend, virality_flag, virality_source, top_signal, recorded_at)
VALUES
    ('wireless_earbuds',  0.780, 'ACCELERATING_POSITIVE', TRUE,  'tiktok_hashtag_spike',
        'Multiple viral TikTok videos reviewing true wireless earbuds under $60 — high purchase intent comments detected.', '2025-06-12T02:45:00'),
    ('power_banks',       0.120, 'NEUTRAL',               FALSE, NULL,
        'No significant social signals detected in the last 24 hours.', '2025-06-12T02:45:00'),
    ('phone_accessories', 0.450, 'MILD_POSITIVE',         FALSE, NULL,
        'Steady positive mentions around work-from-home desk setups featuring phone stands.', '2025-06-12T02:45:00');

-- ---------- Pre-baked overnight action log (morning-brief fallback) ----------
INSERT INTO action_log
    (product_id, action_type, from_value, to_value, executed, guardrail_blocked, block_reason, kimi_explanation, created_at)
VALUES
    ('SKU-001', 'MODE_TRANSITION', 'HOLD', 'HARVEST', TRUE, FALSE, NULL,
        'Competitor AudioZone SG dropped to critically low stock (2 units). Combined with a positive TikTok sentiment spike of +0.78, confidence crossed the Harvest threshold. Transitioning to Harvest Mode.',
        '2025-06-12T02:47:00'),
    ('SKU-001', 'PRICE_UPDATE', '49.90', '54.90', TRUE, FALSE, NULL,
        'Raising price by 10% to capture demand while competitor is stocked out. Within daily velocity cap and above margin floor.',
        '2025-06-12T02:48:00'),
    ('SKU-001', 'PRICE_UPDATE', '54.90', '62.80', FALSE, TRUE,
        'Proposed cumulative daily change of +25.9% (from day-open $49.90) exceeds the 15% velocity cap. Action blocked.',
        NULL, '2025-06-12T03:10:00'),
    ('SKU-001', 'AD_BID_UPDATE', '1.20', '1.50', TRUE, FALSE, NULL,
        'Increasing ad bid by 25% to maximise visibility while competitor is offline. Demand signal is strong.',
        '2025-06-12T03:15:00'),
    ('SKU-002', 'MODE_TRANSITION', 'HOLD', 'BUNKER', TRUE, FALSE, NULL,
        'SKU-002 stock at 45 units, approaching reserve minimum of 15. Sell-through rate has accelerated. Entering Bunker Mode to protect reserve.',
        '2025-06-12T04:30:00');

-- Mirror the blocked action into the guardrail_blocks table for the guardrail screen
INSERT INTO guardrail_blocks
    (product_id, action_type, proposed_value, block_reason, created_at)
VALUES
    ('SKU-001', 'PRICE_UPDATE', '62.80',
        'Proposed cumulative daily change of +25.9% (from day-open $49.90) exceeds the 15% velocity cap. Action blocked.',
        '2025-06-12T03:10:00');
