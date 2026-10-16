#!/usr/bin/env bash
# Synthetic data ONLY. Exercises real PostgreSQL transactions and controller
# against existing V22 schema, not actual product-group network delivery.
set -euo pipefail
PGID="${1:?postgres container required}"
ROOT_SERVICE="${2:?seed service id required}"
ADMIN_HEADER="${3:?admin header required}"
BASE="http://localhost:8080/api/admin/v1/v12/release-actions"
SQL() { docker exec "$PGID" psql -U ipproxy -d ip_proxy -At -v ON_ERROR_STOP=1 -c "$1"; }

docker exec -i "$PGID" psql -U ipproxy -d ip_proxy -v ON_ERROR_STOP=1 >/dev/null <<SQL
INSERT INTO svc_instance(
    service_no,customer_id,product_id,package_id,product_type,
    product_snapshot,package_snapshot,quota_snapshot,
    delivery_mode,delivery_stage,status,expiry_phase,effective_at,
    expire_at,grace_days_snapshot,grace_until_at
)
SELECT 'CI-DB-RELEASE-'||suffix,s.customer_id,s.product_id,s.package_id,
       'LONG_IP',s.product_snapshot,s.package_snapshot,s.quota_snapshot,
       'SELF_OWNED','MOCK_ONLY','EXPIRED','RELEASE_PENDING',
       '2019-12-01T00:00:00Z'::timestamptz,'2020-01-01T00:00:00Z'::timestamptz,
       0,'2020-01-01T00:00:00Z'::timestamptz
FROM svc_instance s CROSS JOIN (VALUES ('A'),('B')) v(suffix)
WHERE s.id=$ROOT_SERVICE;

INSERT INTO svc_egress_claim(service_id,customer_id,egress_ipv4,claim_mode,
                             claim_status,hold_until_at)
SELECT id,customer_id,'198.51.100.171'::inet,'SHARED','GRACE_HELD',
       '2020-01-01T00:00:00Z'::timestamptz
FROM svc_instance WHERE service_no IN ('CI-DB-RELEASE-A','CI-DB-RELEASE-B');

INSERT INTO svc_action_task(operation_key,service_id,action_type,task_status,
                            stage,remote_operation_id,request_summary,result_summary)
SELECT 'CI-DB-RELEASE-'||right(s.service_no,1),s.id,'RELEASE',
       'VERIFYING','NEEDS_BUSINESS_COMMIT','net-ci-'||right(s.service_no,1),
       jsonb_build_object(
          'effectiveExpireAt','2020-01-01T00:00:00Z',
          'egressIpv4','198.51.100.171',
          'releaseMode',CASE WHEN right(s.service_no,1)='A'
             THEN 'UNBIND_SERVICE_ONLY'
             ELSE 'RETURN_TO_SHORT_AFTER_VERIFICATION' END),
       jsonb_build_object(
          'operationId','net-ci-'||right(s.service_no,1),
          'remoteStatus','SUCCEEDED','networkVerified',true,
          'networkEvidence',jsonb_build_object(
             'source','PRODUCT_GROUP_HTTP','actualStatus','STOPPED',
             'verificationStatus','VERIFIED',
             'verifiedAt',to_char(now() AT TIME ZONE 'UTC' - interval '5 minutes',
                                  'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
             'releaseVerification',jsonb_build_object(
                 'releaseMode',CASE WHEN right(s.service_no,1)='A'
                    THEN 'UNBIND_SERVICE_ONLY'
                    ELSE 'RETURN_TO_SHORT_AFTER_VERIFICATION' END,
                 'egressIpv4','198.51.100.171',
                 'serviceAccessRevoked',true,'allOldAccessRevoked',true,
                 'switchedToShort',true,
                 'shortPoolHealthy',false,
                 'resourceId','ci-test-resource',
                 'verifiedAt',to_char(now() AT TIME ZONE 'UTC' - interval '2 minutes',
                                      'YYYY-MM-DD"T"HH24:MI:SS"Z"'))))
FROM svc_instance s WHERE s.service_no IN ('CI-DB-RELEASE-A','CI-DB-RELEASE-B');
SQL

# A can unbind without returning the egress to the short pool.
A=$(curl -fsS -X POST -H "$ADMIN_HEADER" "$BASE/CI-DB-RELEASE-A/commit")
python3 -c 'import sys,json; r=json.load(sys.stdin)["data"]; assert r["businessCommitted"] and not r["productPoolVerified"] and r["releaseMode"]=="UNBIND_SERVICE_ONLY"' <<< "$A"
test "$(SQL "SELECT count(*) FROM svc_egress_claim c JOIN svc_instance s ON s.id=c.service_id
  WHERE s.service_no='CI-DB-RELEASE-A' AND c.claim_status='RELEASED'")" = "1"
test "$(SQL "SELECT count(*) FROM svc_egress_claim c JOIN svc_instance s ON s.id=c.service_id
  WHERE s.service_no='CI-DB-RELEASE-B' AND c.claim_status='GRACE_HELD'")" = "1"

# B is now the last customer, but the product group did not attest a
# healthy short pool. Database must remain untouched after rejection.
BAD=$(curl -s -o /tmp/ci-db-release-denied.json -w '%{http_code}' -X POST -H "$ADMIN_HEADER" "$BASE/CI-DB-RELEASE-B/commit")
test "$BAD" = "400"
python3 -c 'import json; assert json.load(open("/tmp/ci-db-release-denied.json"))["code"]=="RELEASE_SHORT_POOL_NOT_VERIFIED"'
test "$(SQL "SELECT count(*) FROM svc_egress_claim c JOIN svc_instance s ON s.id=c.service_id
  WHERE s.service_no='CI-DB-RELEASE-B' AND c.claim_status='GRACE_HELD'")" = "1"

# Synthetic proof switch to exercise the already-confirmed final commit path.
# This is not a production network verification and never enables product writes.
SQL "UPDATE svc_action_task
     SET result_summary=jsonb_set(result_summary,
       '{networkEvidence,releaseVerification,shortPoolHealthy}','true'::jsonb)
     WHERE operation_key='CI-DB-RELEASE-B'" >/dev/null
B=$(curl -fsS -X POST -H "$ADMIN_HEADER" "$BASE/CI-DB-RELEASE-B/commit")
python3 -c 'import sys,json; r=json.load(sys.stdin)["data"]; assert r["businessCommitted"] and r["productPoolVerified"] and r["releaseMode"]=="RETURN_TO_SHORT_AFTER_VERIFICATION"' <<< "$B"
test "$(SQL "SELECT count(*) FROM svc_egress_claim
  WHERE egress_ipv4='198.51.100.171'::inet AND claim_status<>'RELEASED'")" = "0"
test "$(SQL "SELECT count(*) FROM svc_action_task
  WHERE operation_key IN ('CI-DB-RELEASE-A','CI-DB-RELEASE-B')
  AND task_status='SUCCEEDED' AND stage='BUSINESS_COMMITTED'")" = "2"
echo 'PostgreSQL release lifecycle smoke: shared peer protected, missing proof rejected, last exit committed (synthetic evidence only)'
