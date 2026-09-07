-- IP代理管理平台 V17 - V1.2 关键业务一致性约束
-- 防止绕过服务层写入与V1.2真实业务不一致的数据。

ALTER TABLE svc_instance DROP CONSTRAINT IF EXISTS ck_svc_delivery_mode;
ALTER TABLE svc_instance ADD CONSTRAINT ck_svc_delivery_mode CHECK (delivery_mode IN ('SELF_OWNED','EXTERNAL_PROXY'));

ALTER TABLE res_ros DROP CONSTRAINT IF EXISTS ck_res_ros_ha_role;
ALTER TABLE res_ros ADD CONSTRAINT ck_res_ros_ha_role CHECK (ha_role IN ('STANDALONE','PRIMARY','BACKUP'));
ALTER TABLE res_ros DROP CONSTRAINT IF EXISTS ck_res_ros_replacement_not_self;
ALTER TABLE res_ros ADD CONSTRAINT ck_res_ros_replacement_not_self CHECK (replacement_ros_id IS NULL OR replacement_ros_id<>id);
ALTER TABLE res_ros DROP CONSTRAINT IF EXISTS ck_res_ros_prefix_counts;
ALTER TABLE res_ros ADD CONSTRAINT ck_res_ros_prefix_counts CHECK (b_prefix_count>=0 AND c_prefix_count>=0 AND bas_count>=0);

CREATE OR REPLACE FUNCTION fn_validate_reverse_proxy_customer() RETURNS trigger AS $$
DECLARE purchase_customer BIGINT;
BEGIN
  SELECT customer_id INTO purchase_customer FROM res_supplier_purchase WHERE id=NEW.purchase_id AND deleted=FALSE;
  IF purchase_customer IS NOT NULL AND purchase_customer<>NEW.customer_id THEN
    RAISE EXCEPTION 'reverse proxy customer must match supplier purchase customer';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_validate_reverse_proxy_customer ON res_reverse_proxy;
CREATE TRIGGER trg_validate_reverse_proxy_customer BEFORE INSERT OR UPDATE OF purchase_id,customer_id ON res_reverse_proxy
FOR EACH ROW EXECUTE FUNCTION fn_validate_reverse_proxy_customer();

CREATE OR REPLACE FUNCTION fn_validate_long_monitor() RETURNS trigger AS $$
DECLARE svc_customer BIGINT; svc_type VARCHAR(20); svc_delivery VARCHAR(24); line_customer BIGINT; proxy_customer BIGINT; bot_customer BIGINT;
BEGIN
  SELECT customer_id,product_type,delivery_mode INTO svc_customer,svc_type,svc_delivery
    FROM svc_instance WHERE id=NEW.service_id AND deleted=FALSE;
  IF svc_customer IS NULL THEN RAISE EXCEPTION 'long monitor service does not exist'; END IF;
  IF svc_customer<>NEW.customer_id THEN RAISE EXCEPTION 'long monitor customer must match service customer'; END IF;
  IF svc_type<>'LONG_IP' THEN RAISE EXCEPTION 'long monitor only supports LONG_IP service'; END IF;

  IF NEW.line_id IS NOT NULL THEN
    SELECT customer_id INTO line_customer FROM res_line WHERE id=NEW.line_id AND deleted=FALSE;
    IF line_customer IS NOT NULL AND line_customer<>NEW.customer_id THEN RAISE EXCEPTION 'long monitor line customer mismatch'; END IF;
  END IF;
  IF NEW.reverse_proxy_id IS NOT NULL THEN
    SELECT customer_id INTO proxy_customer FROM res_reverse_proxy WHERE id=NEW.reverse_proxy_id AND deleted=FALSE;
    IF proxy_customer IS NULL OR proxy_customer<>NEW.customer_id THEN RAISE EXCEPTION 'long monitor reverse proxy customer mismatch'; END IF;
  END IF;
  IF NEW.bot_id IS NOT NULL THEN
    SELECT customer_id INTO bot_customer FROM lm_bot WHERE id=NEW.bot_id AND deleted=FALSE;
    IF bot_customer IS NULL OR bot_customer<>NEW.customer_id THEN RAISE EXCEPTION 'long monitor bot customer mismatch'; END IF;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_validate_long_monitor ON lm_monitor;
CREATE TRIGGER trg_validate_long_monitor BEFORE INSERT OR UPDATE OF customer_id,service_id,line_id,reverse_proxy_id,bot_id ON lm_monitor
FOR EACH ROW EXECUTE FUNCTION fn_validate_long_monitor();

CREATE OR REPLACE FUNCTION fn_validate_service_external_delivery() RETURNS trigger AS $$
DECLARE proxy_customer BIGINT; proxy_purchase BIGINT; proxy_status VARCHAR(20); purchase_status VARCHAR(20);
BEGIN
  IF NEW.delivery_mode='SELF_OWNED' THEN
    IF NEW.supplier_purchase_id IS NOT NULL OR NEW.reverse_proxy_id IS NOT NULL THEN
      RAISE EXCEPTION 'self-owned service cannot bind supplier purchase or reverse proxy';
    END IF;
  ELSE
    IF NEW.reverse_proxy_id IS NULL OR NEW.supplier_purchase_id IS NULL THEN
      RAISE EXCEPTION 'external proxy service requires purchase and reverse proxy';
    END IF;
    SELECT customer_id,purchase_id,status INTO proxy_customer,proxy_purchase,proxy_status FROM res_reverse_proxy WHERE id=NEW.reverse_proxy_id AND deleted=FALSE;
    SELECT status INTO purchase_status FROM res_supplier_purchase WHERE id=NEW.supplier_purchase_id AND deleted=FALSE;
    IF proxy_customer IS NULL OR proxy_customer<>NEW.customer_id THEN RAISE EXCEPTION 'external proxy customer mismatch'; END IF;
    IF proxy_purchase<>NEW.supplier_purchase_id THEN RAISE EXCEPTION 'external proxy purchase mismatch'; END IF;
    IF proxy_status<>'ACTIVE' THEN RAISE EXCEPTION 'external reverse proxy must be ACTIVE before service provisioning'; END IF;
    IF purchase_status<>'ACTIVE' THEN RAISE EXCEPTION 'supplier purchase must be ACTIVE before service provisioning'; END IF;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_validate_service_external_delivery ON svc_instance;
CREATE TRIGGER trg_validate_service_external_delivery BEFORE INSERT OR UPDATE OF delivery_mode,supplier_purchase_id,reverse_proxy_id,customer_id ON svc_instance
FOR EACH ROW EXECUTE FUNCTION fn_validate_service_external_delivery();
