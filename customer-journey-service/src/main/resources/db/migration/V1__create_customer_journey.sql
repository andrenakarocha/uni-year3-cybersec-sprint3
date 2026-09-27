CREATE TABLE customer_journeys (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    vin VARCHAR(17) NOT NULL,
    status VARCHAR(32) NOT NULL,
    next_action VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_customer_journeys_customer ON customer_journeys(customer_id);
CREATE UNIQUE INDEX idx_customer_journeys_active_vin
    ON customer_journeys(vin)
    WHERE status NOT IN ('COMPLETED', 'CANCELLED');

