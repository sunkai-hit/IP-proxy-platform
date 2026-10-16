-- IP代理管理平台 V1.2-R3 / 存量独享出口与宽限迁移：只读预检
-- PostgreSQL 17; 依赖已安装的 V1–V23 结构。不包含 DDL / DML / 锁定资源。
-- 运行方式：只读事务；保存脱敏结果给业务平台，不要外发客户敏感数据。
-- 注意：current_public_ip / res_ip.ip_address 可以是动态IP，只有历史快照或产品组能力
-- 证明才能作为合同“固定出口”事实，不得用COALESCE的观测值直接回填。
BEGIN TRANSACTION READ ONLY;

-- A00 基础记录分布；PENDING_RECYCLE必须计为尚未证实释放。
SELECT 'A00_legacy_by_status' AS check_id,resource_type,status,
       count(*) AS records,count(DISTINCT customer_id) AS customers,
       count(DISTINCT service_id) AS services
FROM res_exclusive_allocation WHERE deleted=FALSE
GROUP BY resource_type,status ORDER BY resource_type,status;

-- A01 旧独享占用的实际核对视图：固定快照>旧IP对象>线路当前观测值。
-- 只有ip_source=FIXED_SNAPSHOT才能直接用作固定承诺证据。
CREATE TEMP VIEW r3_legacy_egress_audit AS
SELECT a.id allocation_id,a.allocation_no,a.service_id,a.customer_id,
       a.resource_type,a.status,a.line_id,a.ip_id,
       a.current_ip_snapshot AS fixed_snapshot,
       CASE
         WHEN a.current_ip_snapshot IS NOT NULL THEN a.current_ip_snapshot
         WHEN a.resource_type='IP' THEN ip.ip_address
         ELSE ln.current_public_ip
       END AS observed_ipv4,
       CASE WHEN a.current_ip_snapshot IS NOT NULL THEN 'FIXED_SNAPSHOT'
         WHEN a.resource_type='IP' AND ip.ip_address IS NOT NULL THEN 'LEGACY_IP_OBSERVED'
         WHEN a.resource_type='LINE' AND ln.current_public_ip IS NOT NULL THEN 'LINE_CURRENT_OBSERVED'
         ELSE 'MISSING' END AS ip_source,
       a.expire_at,a.released_at,
       s.status service_status,s.expire_at service_expire_at,
       ln.source_system,ln.source_id,ln.last_sync_at
FROM res_exclusive_allocation a
LEFT JOIN res_ip ip ON ip.id=a.ip_id
LEFT JOIN res_line ln ON ln.id=a.line_id
LEFT JOIN svc_instance s ON s.id=a.service_id
WHERE a.deleted=FALSE AND a.status IN ('LOCKED','ALLOCATED','PENDING_RECYCLE');
-- TEMP VIEW是在当前只读事务中创建的会话临时对象，不修改业务数据库的持久数据。

-- A02 不能安全直接回填：缺少固定快照 / 旧出口无来源 / 没有服务引用。
SELECT 'A02_manual_reconciliation_required' AS check_id,allocation_id,allocation_no,
       service_id,customer_id,resource_type,status,
       host(observed_ipv4) observed_ipv4,ip_source,
       (service_id IS NULL) missing_service_link
FROM r3_legacy_egress_audit
WHERE fixed_snapshot IS NULL OR observed_ipv4 IS NULL OR service_id IS NULL
ORDER BY customer_id,allocation_id;

-- A03 同一个被观测的公网IPv4在旧独享记录中出现多次。
-- 本查询只识别潜在冲突，不把观测IP误当固定IP证据。
SELECT 'A03_legacy_same_ipv4' AS check_id,host(observed_ipv4) observed_ipv4,
       count(*) record_count,count(DISTINCT customer_id) customers,
       array_agg(DISTINCT customer_id) customer_ids,
       array_agg(allocation_id ORDER BY allocation_id) allocation_ids
FROM r3_legacy_egress_audit WHERE observed_ipv4 IS NOT NULL
GROUP BY observed_ipv4 HAVING count(*)>1
ORDER BY customers DESC,record_count DESC;

-- A04 新旧占用表出现不同服务的IPv4重叠，即使旧记录是PENDING_RECYCLE也必须人工处理。
SELECT 'A04_new_legacy_overlap' AS check_id,host(c.egress_ipv4) egress_ipv4,
       c.id new_claim_id,c.service_id new_service,c.customer_id new_customer,
       c.claim_mode,c.claim_status,l.allocation_id legacy_allocation,
       l.service_id legacy_service,l.customer_id legacy_customer,l.status legacy_status,l.ip_source
FROM svc_egress_claim c
JOIN r3_legacy_egress_audit l ON l.observed_ipv4=c.egress_ipv4
WHERE c.claim_status IN ('RESERVED','ACTIVE','GRACE_HELD','RELEASE_PENDING')
  AND (l.service_id IS DISTINCT FROM c.service_id OR l.customer_id<>c.customer_id)
ORDER BY c.egress_ipv4,c.id;

-- A05 新表同IPv4存在至少一项EXCLUSIVE、并发多条占用记录（应为0）。
SELECT 'A05_new_egress_conflicts' AS check_id,host(egress_ipv4) egress_ipv4,
       count(*) claims,count(DISTINCT customer_id) customers,
       array_agg(DISTINCT claim_mode) modes,
       array_agg(id ORDER BY id) claim_ids
FROM svc_egress_claim
WHERE claim_status IN ('RESERVED','ACTIVE','GRACE_HELD','RELEASE_PENDING')
GROUP BY egress_ipv4
HAVING count(*)>1 AND bool_or(claim_mode='EXCLUSIVE');

-- A06 已创建的独享出口不能脱离独享服务或跨客户绑定。
SELECT 'A06_claim_service_mismatch' AS check_id,c.id claim_id,c.service_id,
       c.customer_id claim_customer,s.customer_id service_customer,
       c.claim_mode,s.product_type,c.claim_status
FROM svc_egress_claim c
LEFT JOIN svc_instance s ON s.id=c.service_id
WHERE s.id IS NULL OR s.deleted=TRUE OR s.customer_id<>c.customer_id
   OR (c.claim_mode='EXCLUSIVE' AND s.product_type<>'EXCLUSIVE_IP');

-- A07 活跃/暂停/到期但没有明确宽限快照的固定IP服务，必须逐条决定迁移规则。
SELECT 'A07_missing_grace_snapshot' AS check_id,s.id service_id,s.service_no,
       s.customer_id,s.product_type,s.status,s.expire_at,s.package_id,
       s.grace_days_snapshot,p.grace_days package_current_grace,
       s.package_snapshot
FROM svc_instance s LEFT JOIN prd_package p ON p.id=s.package_id
WHERE s.deleted=FALSE AND s.product_type IN ('LONG_IP','EXCLUSIVE_IP')
  AND s.status IN ('PENDING','ACTIVE','SUSPENDED','EXPIRED')
  AND s.grace_days_snapshot IS NULL
ORDER BY s.expire_at NULLS LAST,s.id;

-- A08 到期但未获真实停服验证、或者占用已经过截止却仍未真实释放。
SELECT 'A08_due_or_stale_claim' AS check_id,s.id service_id,s.service_no,
       s.status,s.expiry_phase,s.delivery_stage,s.expire_at,s.grace_until_at,
       c.id claim_id,host(c.egress_ipv4) egress_ipv4,c.claim_status,c.hold_until_at
FROM svc_instance s
LEFT JOIN svc_egress_claim c ON c.service_id=s.id AND c.claim_status<>'RELEASED'
WHERE s.deleted=FALSE AND s.product_type IN ('LONG_IP','EXCLUSIVE_IP')
  AND (s.expire_at<=now() OR (c.hold_until_at IS NOT NULL AND c.hold_until_at<=now()))
ORDER BY s.expire_at NULLS LAST,s.id;

-- A09 排查旧记录虽已标PENDING_RECYCLE但仍可能需要阻断新售卖。
SELECT 'A09_pending_recycle_legacy' AS check_id,allocation_id,allocation_no,
       customer_id,service_id,resource_type,status,host(observed_ipv4) observed_ipv4,
       ip_source,released_at,service_status
FROM r3_legacy_egress_audit WHERE status='PENDING_RECYCLE'
ORDER BY allocation_id;

-- A10 当前线路动态IP重复不必然违规，但其承诺/计数口径需与独享实际出口分离。
SELECT 'A10_duplicate_line_current_ipv4' AS check_id,
       host(current_public_ip) current_ipv4,count(*) line_count,
       array_agg(resource_code ORDER BY resource_code) lines
FROM res_line WHERE deleted=FALSE AND current_public_ip IS NOT NULL
GROUP BY current_public_ip HAVING count(*)>1;

-- A11 当前资源主档中有非空旧customer_id字段；新业务必须从绑定服务派生。
SELECT 'A11_line_customer_legacy' AS check_id, count(*) populated_line_customer,
       count(DISTINCT customer_id) distinct_customers
FROM res_line WHERE deleted=FALSE AND customer_id IS NOT NULL;

-- A12 汇总发现项，供上报；不要仅用这张汇总替代逐条核对和实际接口证明。
SELECT 'A12_review_counts' AS check_id,
  (SELECT count(*) FROM r3_legacy_egress_audit) legacy_unreleased,
  (SELECT count(*) FROM r3_legacy_egress_audit WHERE fixed_snapshot IS NULL) no_fixed_snapshot,
  (SELECT count(*) FROM r3_legacy_egress_audit WHERE observed_ipv4 IS NULL) no_observed_ipv4,
  (SELECT count(*) FROM r3_legacy_egress_audit WHERE service_id IS NULL) no_service_reference,
  (SELECT count(*) FROM r3_legacy_egress_audit WHERE status='PENDING_RECYCLE') pending_recycle,
  (SELECT count(*) FROM svc_instance WHERE deleted=FALSE AND product_type IN ('LONG_IP','EXCLUSIVE_IP')
     AND grace_days_snapshot IS NULL) fixed_services_without_grace_snapshot;
ROLLBACK;
