-- R3: order confirmed != customer payment confirmed.
-- New records fail closed until an authorized operator records externally verified payment evidence.
ALTER TABLE biz_order ADD COLUMN IF NOT EXISTS payment_status VARCHAR(24) NOT NULL DEFAULT 'UNVERIFIED';
ALTER TABLE biz_order ADD COLUMN IF NOT EXISTS payment_reference VARCHAR(255);
ALTER TABLE biz_order ADD COLUMN IF NOT EXISTS payment_verified_at TIMESTAMPTZ;
ALTER TABLE biz_order ADD COLUMN IF NOT EXISTS payment_verified_by BIGINT REFERENCES sys_user(id);
ALTER TABLE biz_order ADD COLUMN IF NOT EXISTS payment_note VARCHAR(1000);
ALTER TABLE biz_order DROP CONSTRAINT IF EXISTS ck_biz_order_payment_status;
ALTER TABLE biz_order ADD CONSTRAINT ck_biz_order_payment_status
  CHECK (payment_status IN ('UNVERIFIED','PAID_CONFIRMED'));
ALTER TABLE biz_order DROP CONSTRAINT IF EXISTS ck_biz_order_payment_evidence;
ALTER TABLE biz_order ADD CONSTRAINT ck_biz_order_payment_evidence CHECK (
    payment_status<>'PAID_CONFIRMED' OR
    (NULLIF(BTRIM(payment_reference),'') IS NOT NULL AND payment_verified_at IS NOT NULL AND payment_verified_by IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_biz_order_payment_ready
  ON biz_order(payment_status,status,created_at DESC) WHERE deleted=FALSE;

-- Supplier purchase may be recorded for existing inventory; new customer-specific purchased packages
-- require a paid customer order as an audit source (enforced by service layer).
ALTER TABLE res_supplier_purchase ADD COLUMN IF NOT EXISTS source_customer_order_id BIGINT REFERENCES biz_order(id);
CREATE INDEX IF NOT EXISTS idx_supplier_purchase_source_order
  ON res_supplier_purchase(source_customer_order_id) WHERE deleted=FALSE;
COMMENT ON COLUMN res_supplier_purchase.source_customer_order_id IS
  'Optional for legacy/pre-stock purchases; new customer-specific purchases must have a payment-confirmed customer order';
COMMENT ON COLUMN res_supplier_purchase.purchase_cost IS
  'Optional reference only; not a price, credit, quota deduction or downstream eligibility rule';
COMMENT ON COLUMN res_supplier_purchase.package_quota IS 'Optional manual note; not a mandatory or automatic quota guard';
COMMENT ON COLUMN res_supplier_purchase.remaining_quota IS 'Optional manual note; not deducted by order or service lifecycle';

WITH parent AS (SELECT id FROM sys_permission
  WHERE permission_code='order-service:access' AND deleted=FALSE LIMIT 1)
INSERT INTO sys_permission(permission_code,permission_name,permission_type,parent_id,sort_order,status)
SELECT 'order:payment:verify','核实订单外部收款凭证','ACTION',parent.id,15,'ACTIVE'
FROM parent WHERE NOT EXISTS(SELECT 1 FROM sys_permission
  WHERE permission_code='order:payment:verify' AND deleted=FALSE);
INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code='SUPER_ADMIN' AND r.deleted=FALSE AND p.deleted=FALSE
  AND p.permission_code='order:payment:verify'
ON CONFLICT DO NOTHING;
COMMENT ON COLUMN biz_order.payment_status IS
  'PAID_CONFIRMED is a separately audited record of externally confirmed customer payment; order confirmation alone is not payment';
