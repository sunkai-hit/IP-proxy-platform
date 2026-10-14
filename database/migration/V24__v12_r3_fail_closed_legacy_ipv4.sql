-- V24 V1.2-R3：存量独享出口迁移前的保守阻断。
-- V22/V23已按不可变Flyway历史使用，本变更仅追加，不回改旧迁移。
-- 任何缺乏固定出口IPv4快照的未释放旧独享记录，都不能证明对应IP没有被复用；
-- 在业务核对/脱敏导出/产品出口验证之前，停止新出口授权，而非静默重复售卖。
CREATE OR REPLACE FUNCTION fn_validate_svc_egress_claim() RETURNS TRIGGER AS $$
DECLARE
  real_customer BIGINT;
  real_type VARCHAR(20);
BEGIN
  SELECT customer_id,product_type INTO real_customer,real_type
    FROM svc_instance WHERE id=NEW.service_id AND deleted=FALSE;
  IF real_customer IS NULL OR real_customer<>NEW.customer_id THEN
    RAISE EXCEPTION 'EGRESS_SERVICE_CUSTOMER_MISMATCH' USING ERRCODE='23514';
  END IF;
  IF NEW.claim_mode='EXCLUSIVE' AND real_type<>'EXCLUSIVE_IP' THEN
    RAISE EXCEPTION 'EGRESS_MODE_MISMATCH' USING ERRCODE='23514';
  END IF;
  IF NEW.claim_status IN ('RESERVED','ACTIVE','GRACE_HELD','RELEASE_PENDING') THEN
    -- A legacy LINE record's current_public_ip is only a runtime observation, not evidence
    -- of a contracted fixed IP. Require reconciliation rather than trust the current address.
    IF EXISTS (
      SELECT 1 FROM res_exclusive_allocation legacy
      WHERE legacy.deleted=FALSE
        AND legacy.status IN ('LOCKED','ALLOCATED','PENDING_RECYCLE')
        AND legacy.current_ip_snapshot IS NULL
        AND legacy.service_id IS DISTINCT FROM NEW.service_id
    ) THEN
      RAISE EXCEPTION 'EGRESS_LEGACY_RECONCILIATION_REQUIRED'
        USING ERRCODE='23514';
    END IF;

    PERFORM pg_advisory_xact_lock(hashtext(host(NEW.egress_ipv4))::bigint);
    IF EXISTS (
      SELECT 1 FROM svc_egress_claim c
      WHERE c.id<>COALESCE(NEW.id,-1)
        AND c.egress_ipv4=NEW.egress_ipv4
        AND c.claim_status IN ('RESERVED','ACTIVE','GRACE_HELD','RELEASE_PENDING')
        AND (NEW.claim_mode='EXCLUSIVE' OR c.claim_mode='EXCLUSIVE')
    ) THEN
      RAISE EXCEPTION 'EGRESS_IPV4_EXCLUSIVE_CONFLICT' USING ERRCODE='23505';
    END IF;
    IF EXISTS (
      SELECT 1 FROM res_exclusive_allocation legacy
      WHERE legacy.deleted=FALSE
        AND legacy.status IN ('LOCKED','ALLOCATED','PENDING_RECYCLE')
        AND legacy.service_id IS DISTINCT FROM NEW.service_id
        AND legacy.current_ip_snapshot=NEW.egress_ipv4
    ) THEN
      RAISE EXCEPTION 'EGRESS_IPV4_LEGACY_EXCLUSIVE_CONFLICT'
        USING ERRCODE='23505';
    END IF;
  END IF;
  NEW.updated_at=now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
COMMENT ON FUNCTION fn_validate_svc_egress_claim() IS
  'V24 fails closed for active legacy exclusive records with unverified IP snapshots. Requires historical reconciliation and real delivery confirmation.';
