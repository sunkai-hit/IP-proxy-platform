-- 2026-10-14 BIZ-06: LONG_IP replacement is human-approved only.
-- Preserve old contracts and historical configuration for explicit review.
-- NOT VALID avoids reclassifying historic rows without audit, but protects all NEW/UPDATED rows.
ALTER TABLE prd_long_ip_config ALTER COLUMN auto_replace SET DEFAULT FALSE;
ALTER TABLE prd_long_ip_config DROP CONSTRAINT IF EXISTS ck_v12_long_manual_failover_only;
ALTER TABLE prd_long_ip_config ADD CONSTRAINT ck_v12_long_manual_failover_only
  CHECK (auto_replace=FALSE AND bind_type='LINE') NOT VALID;
COMMENT ON COLUMN prd_long_ip_config.auto_replace IS
 'V1.2 manual-only: old TRUE rows are historical exceptions needing review, not approval for automatic failover';
COMMENT ON CONSTRAINT ck_v12_long_manual_failover_only ON prd_long_ip_config IS
 'Enforced for new/updated configs; existing legacy data NOT VALID pending audited reconciliation';
