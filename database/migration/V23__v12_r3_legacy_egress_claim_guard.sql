-- V23 R3: prevent new IPv4 claims from bypassing existing legacy exclusive allocations.
-- Keep V22 immutable after CI has applied it. This adds a compatibility guard; full historical backfill requires a separate audited migration.
CREATE OR REPLACE FUNCTION fn_validate_svc_egress_claim() RETURNS TRIGGER AS $$
DECLARE real_customer BIGINT; real_type VARCHAR(20);
BEGIN
  SELECT customer_id,product_type INTO real_customer,real_type FROM svc_instance
    WHERE id=NEW.service_id AND deleted=FALSE;
  IF real_customer IS NULL OR real_customer<>NEW.customer_id THEN
     RAISE EXCEPTION 'EGRESS_SERVICE_CUSTOMER_MISMATCH' USING ERRCODE='23514';
  END IF;
  IF NEW.claim_mode='EXCLUSIVE' AND real_type<>'EXCLUSIVE_IP' THEN
     RAISE EXCEPTION 'EGRESS_MODE_MISMATCH' USING ERRCODE='23514';
  END IF;
  IF NEW.claim_status IN ('RESERVED','ACTIVE','GRACE_HELD','RELEASE_PENDING') THEN
    PERFORM pg_advisory_xact_lock(hashtext(host(NEW.egress_ipv4))::bigint);
    IF EXISTS (
      SELECT 1 FROM svc_egress_claim c
      WHERE c.id<>COALESCE(NEW.id,-1) AND c.egress_ipv4=NEW.egress_ipv4
        AND c.claim_status IN ('RESERVED','ACTIVE','GRACE_HELD','RELEASE_PENDING')
        AND (NEW.claim_mode='EXCLUSIVE' OR c.claim_mode='EXCLUSIVE')
    ) THEN
      RAISE EXCEPTION 'EGRESS_IPV4_EXCLUSIVE_CONFLICT' USING ERRCODE='23505';
    END IF;
    -- Legacy V1-V21 allocations may predate the egress claim table.
    -- Same-service legacy allocation is the expected transitional write in R3.
    IF EXISTS (
      SELECT 1 FROM res_exclusive_allocation legacy
      WHERE legacy.current_ip_snapshot=NEW.egress_ipv4
        AND legacy.service_id IS DISTINCT FROM NEW.service_id
        AND legacy.deleted=FALSE
        AND legacy.status IN ('LOCKED','ALLOCATED')
    ) THEN
      RAISE EXCEPTION 'EGRESS_IPV4_LEGACY_EXCLUSIVE_CONFLICT' USING ERRCODE='23505';
    END IF;
  END IF;
  NEW.updated_at=now();
  RETURN NEW;
END; $$ LANGUAGE plpgsql;
COMMENT ON FUNCTION fn_validate_svc_egress_claim() IS
 'R3 global IPv4 exclusive guard; checks new and legacy holdings. A legacy backfill audit is still mandatory.';
