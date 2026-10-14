-- V26: A paid RENEW order targets exactly one existing service.
-- Order confirmation alone is never evidence of payment or valid renewal.
ALTER TABLE biz_order ADD COLUMN IF NOT EXISTS renewal_service_id BIGINT REFERENCES svc_instance(id);
CREATE INDEX IF NOT EXISTS idx_biz_order_renewal_service
    ON biz_order(renewal_service_id,status) WHERE deleted=FALSE AND renewal_service_id IS NOT NULL;
ALTER TABLE biz_order DROP CONSTRAINT IF EXISTS ck_biz_order_renewal_target;
ALTER TABLE biz_order ADD CONSTRAINT ck_biz_order_renewal_target
    CHECK (order_type='RENEW' OR renewal_service_id IS NULL);
COMMENT ON COLUMN biz_order.renewal_service_id IS
    'Renewal order target. RENEW creation requires a specific same-customer service; existing legacy records need explicit reconciliation before execution.';
