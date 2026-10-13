-- The branches we actually track: around Yigal Alon / La Guardia, Tel Aviv.
-- chain_code is the ChainId in the Price Transparency Law files. store_code is filled in where it
-- was looked up from the chain's Stores file; the rest are resolved automatically on the first
-- ingestion by matching store_match against the chain's Stores file (StoreName + Address + City).

ALTER TABLE stores ADD COLUMN store_match VARCHAR(100);

UPDATE stores SET branch_name = 'Yochananof Yigal Alon', chain_code = '7290803800003',
                  store_match = 'יגאל אלון'
WHERE chain = 'YOCHANANOF';

-- Osher Ad's only Tel Aviv branch is "תל אביב", Kreminitzky 3 (off Yigal Alon).
UPDATE stores SET branch_name = 'Osher Ad Yigal Alon', address = 'קרמינצקי 3', chain_code = '7290103152017',
                  store_code = '024', store_match = 'קרמינצקי'
WHERE chain = 'OSHER_AD';

UPDATE stores SET branch_name = 'Carrefour La Guardia', chain_code = '7290055700007',
                  store_match = 'לה גארדיה'
WHERE chain = 'CARREFOUR';

UPDATE stores SET branch_name = 'Super-Pharm Yigal Alon 1', chain_code = '7290172900007',
                  store_match = 'יגאל אלון'
WHERE chain = 'SUPER_PHARM';

UPDATE stores SET branch_name = 'Good Pharm La Guardia', chain_code = '7290058108879',
                  store_match = 'לה גארדיה'
WHERE chain = 'GOOD_PHARM';

-- Tiv Taam store 080 "יגאל אלון דלי", Yigal Alon 94.
UPDATE stores SET branch_name = 'Tiv Taam Yigal Alon', address = 'יגאל אלון 94', chain_code = '7290873255550',
                  store_code = '080', store_match = 'יגאל אלון'
WHERE chain = 'TIV_TAAM';

INSERT INTO stores (chain, branch_name, city, chain_code, store_match) VALUES
    ('SUPER_PHARM', 'Super-Pharm Yigal Alon 2',      'Tel Aviv', '7290172900007', 'יגאל אלון'),
    ('SHUFERSAL',   'Shufersal Deal Yigal Alon',     'Tel Aviv', '7290027600007', 'דיל יגאל אלון');
