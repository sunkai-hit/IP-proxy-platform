package com.ipproxy.platform.service.v12;

import com.ipproxy.platform.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Side-effect-free (on business entities) authoritative release preflight.
 *
 * A service lease can end without freeing its IPv4: for LONG shared exits only
 * the last long claim may trigger a short-pool mode conversion. Neither case
 * assumes that a SQL state change equals verified network release.
 */
@Service
public class ProductGraceReleaseInspector {
    private final JdbcTemplate jdbc;
    public ProductGraceReleaseInspector(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public record ClaimPlan(Long claimId,String ipv4,String mode,String releasePlan,
                            int otherProtectedClaims,boolean requiresModeConversion){}
    public record Inspection(String operationKey,Long serviceId,String status,
                             boolean ready,boolean dataPlaneVerified,
                             List<ClaimPlan> claims,String note){}

    @Transactional
    public Inspection inspect(String operationKey) {
        if(operationKey==null||operationKey.isBlank()||operationKey.length()>128)
            throw fail("TASK_OPERATION_INVALID","释放任务编号不合法");
        List<Map<String,Object>> rows=jdbc.queryForList("""
            SELECT t.id task_id,t.operation_key,t.service_id,t.action_type,t.task_status,
                   t.stage,t.remote_operation_id,t.request_summary::text task_request,
                   s.status service_status,s.product_type,s.delivery_mode,s.expiry_phase,
                   s.expire_at,s.grace_until_at,s.grace_days_snapshot
            FROM svc_action_task t
            JOIN svc_instance s ON s.id=t.service_id AND s.deleted=FALSE
            WHERE t.operation_key=? FOR UPDATE OF s,t
            """,operationKey);
        if(rows.isEmpty())throw fail("TASK_NOT_FOUND","释放任务不存在");
        Map<String,Object> t=rows.getFirst();
        long serviceId=((Number)t.get("service_id")).longValue();
        if(!"RELEASE".equals(val(t,"action_type"))
                ||!"PRECHECK_BLOCKED".equals(val(t,"task_status"))
                ||t.get("remote_operation_id")!=null
                ||!"EXPIRED".equals(val(t,"service_status"))
                ||!"RELEASE_PENDING".equals(val(t,"expiry_phase"))
                ||!"SELF_OWNED".equals(val(t,"delivery_mode"))
                ||!Set.of("LONG_IP","EXCLUSIVE_IP").contains(val(t,"product_type")))
            throw fail("RELEASE_PRECHECK_BLOCKED","当前服务/任务不允许进入回收预检");

        Instant expiry=asTime(t.get("expire_at"));
        Instant hold=asTime(t.get("grace_until_at"));
        Integer days=t.get("grace_days_snapshot") instanceof Number n?n.intValue():null;
        if(expiry==null||hold==null||days==null||days<0
                ||!hold.equals(expiry.plusSeconds(days*86400L))
                ||Instant.now().isBefore(hold))
            throw fail("RELEASE_GRACE_NOT_ENDED","尚未到宽限截止，或合同权益快照不完整");
        // Lock all of THIS service's unreleased claims, including those that a
        // background planner has already marked RELEASE_PENDING.
        List<Map<String,Object>> claims=jdbc.queryForList("""
            SELECT id,host(egress_ipv4) ipv4,claim_mode,claim_status,hold_until_at
            FROM svc_egress_claim
            WHERE service_id=? AND claim_status<>'RELEASED'
            ORDER BY id FOR UPDATE
            """,serviceId);
        if(claims.isEmpty())throw fail("RELEASE_CLAIM_MISSING","缺少可验证的原有固定IPv4占用");
        String expectedMode="EXCLUSIVE_IP".equals(val(t,"product_type"))?"EXCLUSIVE":"SHARED";
        List<ClaimPlan> plans=new ArrayList<>();
        for(Map<String,Object> claim:claims){
            String ip=val(claim,"ipv4");
            if(ip.isBlank()||!expectedMode.equals(val(claim,"claim_mode"))
                    ||!Set.of("GRACE_HELD","RELEASE_PENDING").contains(val(claim,"claim_status")))
                throw fail("RELEASE_CLAIM_INCONSISTENT","固定IP类型或宽限保留状态异常");
            Instant claimHold=asTime(claim.get("hold_until_at"));
            if(claimHold==null||!claimHold.equals(hold))
                throw fail("RELEASE_CLAIM_HOLD_MISMATCH","固定IP持有截止与原合同不匹配");
            // The same advisory namespace as the global IPv4 claim trigger.
            // New claims are barred from racing between peer counting and plan.
            jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?)::bigint)",
                    Object.class,ip);
            List<Map<String,Object>> others=jdbc.queryForList("""
                SELECT c.service_id,c.claim_status,c.claim_mode
                FROM svc_egress_claim c
                WHERE c.egress_ipv4=CAST(? AS inet) AND c.service_id<>?
                  AND c.claim_status IN ('RESERVED','ACTIVE','GRACE_HELD','RELEASE_PENDING')
                FOR UPDATE
                """,ip,serviceId);
            if("EXCLUSIVE".equals(expectedMode)&&!others.isEmpty())
                throw fail("EXCLUSIVE_EGRESS_CONFLICT","独享公网IPv4仍存在其他服务占用，不得回池");
            for(Map<String,Object> other:others)
                if(!"SHARED".equals(val(other,"claim_mode")))
                    throw fail("EGRESS_MODE_CONFLICT","发现独享与共享占用重叠，必须人工核对");
            // Legacy records do not always have an immutable original IPv4.
            // Unknown historical exits must be resolved before any RETURN.
            Long legacy=jdbc.queryForObject("""
                SELECT count(*) FROM res_exclusive_allocation a
                WHERE a.deleted=FALSE AND a.status IN ('LOCKED','ALLOCATED','PENDING_RECYCLE')
                  AND a.service_id IS DISTINCT FROM ?
                  AND (a.current_ip_snapshot=CAST(? AS inet)
                       OR a.current_ip_snapshot IS NULL)
                """,Long.class,serviceId,ip);
            if(legacy==null||legacy>0)
                throw fail("RELEASE_LEGACY_RECONCILIATION_REQUIRED",
                    "仍有未核实或冲突的历史独享IP占用，不能回收公网IPv4");
            boolean returnToShort=others.isEmpty();
            plans.add(new ClaimPlan(((Number)claim.get("id")).longValue(),ip,expectedMode,
                    returnToShort?"RETURN_TO_SHORT_AFTER_VERIFICATION":"UNBIND_SERVICE_ONLY",
                    others.size(),returnToShort));
        }
        // This inspection is advisory. If a peer appears/renews afterwards,
        // the real dispatch and final commit MUST recalculate under the lock.
        return new Inspection(operationKey,serviceId,"READY_FOR_PRODUCT_REVIEW",true,false,
                List.copyOf(plans),
                "仅完成原合同宽限、其他客户占用及历史数据预检；尚未执行服务解绑定、代理转换或回池");
    }

    private static String val(Map<String,Object> m,String k){
        Object v=m.get(k);return v==null?"":v.toString();
    }
    private static Instant asTime(Object v){
        if(v instanceof OffsetDateTime t)return t.toInstant();
        if(v instanceof Timestamp t)return t.toInstant();
        return null;
    }
    private static BusinessException fail(String code,String msg){return new BusinessException(code,msg);}
}
