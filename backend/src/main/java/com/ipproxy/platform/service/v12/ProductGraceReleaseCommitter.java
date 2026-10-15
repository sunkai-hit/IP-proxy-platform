package com.ipproxy.platform.service.v12;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipproxy.platform.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Final business ledger commit for ONE already network-verified RELEASE task.
 * This never sends product commands. Rechecks the same global IPv4 exclusion
 * under lock before releasing this service's claim.
 */
@Service
public class ProductGraceReleaseCommitter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public ProductGraceReleaseCommitter(JdbcTemplate jdbc,ObjectMapper json) {
        this.jdbc=jdbc;this.json=json;
    }
    public record Result(String operationKey,Long serviceId,String phase,
                         String releaseMode,boolean businessCommitted,boolean productPoolVerified){}

    @Transactional
    public Result commit(String key,Long actorId) {
        if(actorId==null||actorId<=0)throw fail("AUTH_FORBIDDEN","无有效业务操作员");
        if(key==null||key.isBlank()||key.length()>128)throw fail("TASK_OPERATION_INVALID","释放任务编号不合法");
        List<Map<String,Object>> tasks=jdbc.queryForList("""
            SELECT t.id task_id,t.action_type,t.task_status,t.stage,t.remote_operation_id,
                   t.request_summary::text request_json,t.result_summary::text result_json,
                   s.id service_id,s.status service_status,s.product_type,s.delivery_mode,
                   s.expiry_phase,s.expire_at,s.grace_until_at,s.grace_days_snapshot
            FROM svc_action_task t
            JOIN svc_instance s ON s.id=t.service_id AND s.deleted=FALSE
            WHERE t.operation_key=? FOR UPDATE OF s,t
            """,key);
        if(tasks.isEmpty())throw fail("TASK_NOT_FOUND","任务不存在");
        Map<String,Object> task=tasks.getFirst();
        long serviceId=((Number)task.get("service_id")).longValue();
        if("SUCCEEDED".equals(v(task,"task_status"))&&"BUSINESS_COMMITTED".equals(v(task,"stage")))
            return new Result(key,serviceId,v(task,"expiry_phase"),"ALREADY_COMMITTED",false,false);
        if(!"RELEASE".equals(v(task,"action_type"))||!"VERIFYING".equals(v(task,"task_status"))
                ||!"NEEDS_BUSINESS_COMMIT".equals(v(task,"stage"))
                ||v(task,"remote_operation_id").isBlank()
                ||!"EXPIRED".equals(v(task,"service_status"))
                ||!"RELEASE_PENDING".equals(v(task,"expiry_phase"))
                ||!"SELF_OWNED".equals(v(task,"delivery_mode"))
                ||!Set.of("LONG_IP","EXCLUSIVE_IP").contains(v(task,"product_type")))
            throw fail("RELEASE_STATE_CHANGED","合同或释放任务状态不符合提交条件");
        Instant expiry=instant(task.get("expire_at")), hold=instant(task.get("grace_until_at"));
        Integer days=task.get("grace_days_snapshot") instanceof Number n?n.intValue():null;
        if(expiry==null||hold==null||days==null||days<0||!hold.equals(expiry.plusSeconds(days*86400L))
                ||Instant.now().isBefore(hold))
            throw fail("RELEASE_GRACE_NOT_ENDED","合同宽限期限发生变化或尚未结束");
        JsonNode request=read(task.get("request_json")), summary=read(task.get("result_json"));
        JsonNode evidence=summary.path("networkEvidence");
        JsonNode proof=evidence.path("releaseVerification");
        String mode=request.path("releaseMode").asText(), ip=request.path("egressIpv4").asText();
        if(!Set.of("UNBIND_SERVICE_ONLY","RETURN_TO_SHORT_AFTER_VERIFICATION").contains(mode)
                ||ip.isBlank()
                ||!expiry.equals(parseTime(request.path("effectiveExpireAt").asText()))
                ||!v(task,"remote_operation_id").equals(summary.path("operationId").asText())
                ||!"SUCCEEDED".equals(summary.path("remoteStatus").asText())
                ||!summary.path("networkVerified").asBoolean(false)
                ||!"PRODUCT_GROUP_HTTP".equals(evidence.path("source").asText())
                ||!"STOPPED".equals(evidence.path("actualStatus").asText())
                ||!"VERIFIED".equals(evidence.path("verificationStatus").asText())
                ||!proof.path("serviceAccessRevoked").asBoolean(false)
                ||!mode.equals(proof.path("releaseMode").asText())
                ||!ip.equals(proof.path("egressIpv4").asText()))
            throw fail("RELEASE_PROOF_INVALID","产品组的服务解绑、出口及任务回执不匹配");
        Instant networkAt=parseTime(evidence.path("verifiedAt").asText());
        Instant verifiedAt=parseTime(proof.path("verifiedAt").asText());
        if(networkAt==null||verifiedAt==null||verifiedAt.isBefore(hold)
                ||networkAt.isBefore(expiry)
                ||verifiedAt.isAfter(Instant.now().plusSeconds(300))
                ||networkAt.isAfter(Instant.now().plusSeconds(300)))
            throw fail("RELEASE_PROOF_STALE","释放核验时间无效");
        boolean last="RETURN_TO_SHORT_AFTER_VERIFICATION".equals(mode);
        if(last&&(!proof.path("allOldAccessRevoked").asBoolean(false)
                ||!proof.path("switchedToShort").asBoolean(false)
                ||!proof.path("shortPoolHealthy").asBoolean(false)
                ||proof.path("resourceId").asText().isBlank()))
            throw fail("RELEASE_SHORT_POOL_NOT_VERIFIED","最后一项出口占用未获得短效回池证明");
        List<Map<String,Object>> claims=jdbc.queryForList("""
            SELECT id,host(egress_ipv4) ipv4,claim_mode,claim_status,hold_until_at
            FROM svc_egress_claim WHERE service_id=? AND claim_status<>'RELEASED'
            ORDER BY id FOR UPDATE
            """,serviceId);
        if(claims.size()!=1||!ip.equals(v(claims.getFirst(),"ipv4"))
                ||!Set.of("GRACE_HELD","RELEASE_PENDING").contains(v(claims.getFirst(),"claim_status"))
                ||!hold.equals(instant(claims.getFirst().get("hold_until_at")))
                ||!("EXCLUSIVE_IP".equals(v(task,"product_type"))?"EXCLUSIVE":"SHARED")
                    .equals(v(claims.getFirst(),"claim_mode")))
            throw fail("RELEASE_CLAIM_CHANGED","当前服务的出口占用已变化，不能使用旧释放证明");
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?)::bigint)",Object.class,ip);
        Long peers=jdbc.queryForObject("""
            SELECT count(*) FROM svc_egress_claim
            WHERE egress_ipv4=CAST(? AS inet) AND service_id<>?
              AND claim_status IN ('RESERVED','ACTIVE','GRACE_HELD','RELEASE_PENDING')
            """,Long.class,ip,serviceId);
        if(peers==null||(last&&peers!=0)||(!last&&peers==0))
            throw fail("RELEASE_PEERS_CHANGED","共享客户数量已变化，请重新进行产品组实际释放及核验");
        if("EXCLUSIVE_IP".equals(v(task,"product_type"))&&peers>0)
            throw fail("RELEASE_EXCLUSIVE_CONFLICT","独享出口存在其他客户占用");
        Long legacy=jdbc.queryForObject("""
            SELECT count(*) FROM res_exclusive_allocation
            WHERE deleted=FALSE AND status IN ('LOCKED','ALLOCATED','PENDING_RECYCLE')
              AND service_id IS DISTINCT FROM ?
              AND (current_ip_snapshot=CAST(? AS inet) OR current_ip_snapshot IS NULL)
            """,Long.class,serviceId,ip);
        if(legacy==null||legacy>0)
            throw fail("RELEASE_LEGACY_UNRECONCILED","遗留独享出口仍有冲突或未验证的快照");

        int changed=jdbc.update("""
            UPDATE svc_egress_claim
            SET claim_status='RELEASED',released_at=now(),verified_at=?,operation_key=?,updated_at=now()
            WHERE id=? AND service_id=? AND claim_status IN ('GRACE_HELD','RELEASE_PENDING')
            """,OffsetDateTime.ofInstant(verifiedAt,ZoneOffset.UTC),key,claims.getFirst().get("id"),serviceId);
        if(changed!=1)throw fail("RELEASE_CLAIM_RACE","占用状态并发变化");
        jdbc.update("""
            UPDATE svc_credential SET status='REVOKED',updated_at=now(),version=version+1
            WHERE service_id=? AND deleted=FALSE AND status IN ('ACTIVE','FROZEN')
            """,serviceId);
        jdbc.update("""
            UPDATE svc_resource_binding SET status='RELEASED',released_at=now()
            WHERE service_id=? AND status='ACTIVE'
            """,serviceId);
        // Release this service's own historical exclusive claim ONLY after
        // proven network isolation; no other customer's legacy rows are touched.
        if("EXCLUSIVE_IP".equals(v(task,"product_type"))){
            jdbc.update("""
                UPDATE res_exclusive_allocation
                SET status='AVAILABLE',released_at=now(),release_reason='真实出口回池已验证',
                    updated_at=now(),version=version+1
                WHERE service_id=? AND deleted=FALSE AND current_ip_snapshot=CAST(? AS inet)
                  AND status IN ('LOCKED','ALLOCATED','PENDING_RECYCLE')
                """,serviceId,ip);
        }
        int serviceChanged=jdbc.update("""
            UPDATE svc_instance SET expiry_phase='RELEASED',updated_by=?,updated_at=now(),
                version=version+1
            WHERE id=? AND deleted=FALSE AND status='EXPIRED' AND expiry_phase='RELEASE_PENDING'
              AND expire_at=?
            """,actorId,serviceId,OffsetDateTime.ofInstant(expiry,ZoneOffset.UTC));
        if(serviceChanged!=1)throw fail("RELEASE_SERVICE_RACE","服务状态并发变化");
        int taskChanged=jdbc.update("""
            UPDATE svc_action_task SET task_status='SUCCEEDED',stage='BUSINESS_COMMITTED',
                result_summary=result_summary || jsonb_build_object(
                    'businessStateChanged',true,'businessCommittedBy',CAST(? AS bigint),
                    'businessCommittedAt',now(),'releaseMode',CAST(? AS text)),
                finished_at=now(),updated_at=now()
            WHERE id=? AND task_status='VERIFYING' AND stage='NEEDS_BUSINESS_COMMIT'
              AND action_type='RELEASE'
            """,actorId,mode,task.get("task_id"));
        if(taskChanged!=1)throw fail("RELEASE_TASK_RACE","任务状态并发变化");
        return new Result(key,serviceId,"RELEASED",mode,true,last);
    }
    private JsonNode read(Object raw){
        try{return json.readTree(v(raw));}
        catch(Exception e){throw fail("RELEASE_PROOF_INVALID","任务核验摘要不可读取");}
    }
    private static String v(Object x){return x==null?"":x.toString();}
    private static String v(Map<String,Object> m,String k){return v(m.get(k));}
    private static Instant instant(Object x){
        if(x instanceof OffsetDateTime o)return o.toInstant();
        if(x instanceof Timestamp t)return t.toInstant();
        return null;
    }
    private static Instant parseTime(String s){
        try{return OffsetDateTime.parse(s).toInstant();}catch(Exception e){return null;}
    }
    private static BusinessException fail(String code,String message){
        return new BusinessException(code,message);
    }
}
