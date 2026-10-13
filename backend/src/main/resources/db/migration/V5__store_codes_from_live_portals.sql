-- Store codes looked up in the chains' live Stores files (13/10/2026).

-- Yochananof store 027 "יד אליהו", Yigal Alon 57.
UPDATE stores SET address = 'יגאל אלון 57', store_code = '027'
WHERE chain = 'YOCHANANOF';

-- Carrefour store 205 "קרפור מרקט לה גרדיה", La Guardia 78. The chain spells it without an aleph.
UPDATE stores SET address = 'לה גרדיה 78', store_code = '205', store_match = 'לה גרדיה'
WHERE chain = 'CARREFOUR';

-- Good Pharm's files carry ChainId 7290058197699.
UPDATE stores SET chain_code = '7290058197699'
WHERE chain = 'GOOD_PHARM';
