-- VIN vinculado a um cliente é dado pessoal (LGPD): passa a ser gravado cifrado (AES-256-GCM,
-- "v1:" + base64, ~64 caracteres). A busca por VIN ativo e a unicidade usam o blind index
-- vin_hash (HMAC-SHA256 em hex), já que o texto cifrado muda a cada gravação.
-- Linhas antigas em texto puro não são decifráveis: ambiente de POC recriado com `make clean`.
ALTER TABLE customer_journeys ALTER COLUMN vin TYPE VARCHAR(128);
ALTER TABLE customer_journeys ADD COLUMN vin_hash CHAR(64) NOT NULL;

DROP INDEX idx_customer_journeys_active_vin;
CREATE UNIQUE INDEX idx_customer_journeys_active_vin_hash
    ON customer_journeys(vin_hash)
    WHERE status NOT IN ('COMPLETED', 'CANCELLED');
