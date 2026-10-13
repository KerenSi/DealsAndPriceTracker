-- MVP scope: local branches in Yad Eliyahu, Tel Aviv.
-- chain_code / store_code are left NULL: fill them from each chain's published Stores file
-- (Price Transparency Law) before enabling the real transparency feeds.
INSERT INTO stores (chain, branch_name, city, address, chain_code, store_code) VALUES
    ('YOCHANANOF',  'Yochananof Yad Eliyahu',  'Tel Aviv', NULL, NULL, NULL),
    ('OSHER_AD',    'Osher Ad Yad Eliyahu',    'Tel Aviv', NULL, NULL, NULL),
    ('CARREFOUR',   'Carrefour Yad Eliyahu',   'Tel Aviv', NULL, NULL, NULL),
    ('SUPER_PHARM', 'Super-Pharm Yad Eliyahu', 'Tel Aviv', NULL, NULL, NULL),
    ('GOOD_PHARM',  'Good Pharm Yad Eliyahu',  'Tel Aviv', NULL, NULL, NULL),
    ('TIV_TAAM',    'Tiv Taam Yad Eliyahu',    'Tel Aviv', NULL, NULL, NULL);
