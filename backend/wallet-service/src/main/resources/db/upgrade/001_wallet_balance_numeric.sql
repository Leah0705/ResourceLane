-- Run once on an existing PostgreSQL database before deploying the BigDecimal wallet code.
-- Back up the database first. New databases created from the entity already use NUMERIC(19,2).
ALTER TABLE wallets
    ALTER COLUMN balance TYPE NUMERIC(19, 2)
    USING ROUND(balance::numeric, 2);

-- Resolve any duplicate user_id rows before applying this constraint.
ALTER TABLE wallets ADD CONSTRAINT uq_wallet_user_id UNIQUE (user_id);
