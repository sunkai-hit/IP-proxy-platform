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
import java.util.*;

/**
 * Commits ONLY a verified service-scoped expiry stop to the business ledger.
 * Never releases/recyles an IPv4, changes other shared customers' services,
 * or calls the product group. Requires task evidence from reconciliation.
 */
@Service
public class ProductExpiryStopCommitter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ProductExpiryStopCommitter(JdbcTemplate jdbc,ObjectMapper json){
        this.jdbc=jdbc;this.json=json;
    }
    public record CommitResult(String operationKey,Long serviceId,String serviceStatus,
          String expiryPhase,String taskStatus,int heldClaims,boolean egressReleased,
          boolean businessStateChanged,String note){}

    @Transactional
    public CommitResult commit(String key,Long actorUserId){
        if(actorUserId==null||actorUserId<=0)
            throw error("AUTH_FORBIDDEN","需要有效的业务平台操作员身份");
        if(key==null||key.isBlank()||key.length()>128)
            throw error("TASK_OPERATION_INVALID","操作编号不合法");
        // Lock the service and its originating task in one transaction. A
        // concurrent renewal/stop or second commit cannot both succeed.
        List<Map<String,Object>> rows=jdbc.queryForList("""
            SELECT t.id task_id,t.operation_key,t.action_type,t.task_status,t.stage,
                   t.remote_operation_id,t.result_summary::text task_result,
                   t.request_summary::text task_request,
                   s.id service_id,s.product_type,s.delivery_mode,s.status service_status,
                   s.expiry_phase,s.expire_at,s.grace_until_at,s.grace_days_snapshot
            FROM svc_action_task t JOIN svc_instance s ON s.id=t.service_id AND s.deleted=FALSE
            WHERE t.operation_key=? FOR UPDATE OF s,t
            """,key);
        if(rows.isEmpty())throw error("TASK_NOT_FOUND","操作任务或服务不存在");
        Map<String,Object> row=rows.getFirst();
        long serviceId=((Number)row.get("service_id")).longValue();
        long taskId=((Number)row.get("task_id")).longValue();
        if("SUCCEEDED".equals(str(row.get("task_status")))
                &&"BUSINESS_COMMITTED".equals(str(row.get("stage"))))
            return new CommitResult(key,serviceId,str(row.get("service_status")),str(row.get("expiry_phase")),"SUCCEEDED",
                0,false,false,"该操作已经提交；不会重放停服或影响当前服务的新生命周期");
        if(!"STOP_FOR_GRACE".equals(str(row.get("action_type")))
                ||!"VERIFYING".equals(str(row.get("task_status")))
                ||!"NEEDS_BUSINESS_COMMIT".equals(str(row.get("stage")))
                ||str(row.get("remote_operation_id")).isBlank())
            throw error("TASK_NOT_VERIFIED","任务尚未取得可提交的真实停服核验结果");
        if(!Set.of("LONG_IP","EXCLUSIVE_IP").contains(str(row.get("product_type")))
                ||!"SELF_OWNED".equals(str(row.get("delivery_mode")))
                ||!Set.of("ACTIVE","SUSPENDED").contains(str(row.get("service_status")))
                ||!"STOP_PENDING".equals(str(row.get("expiry_phase"))))
            throw error("SERVICE_STOP_STATE_CHANGED","服务类型或生命周期状态已改变，禁止套用旧任务回执");

        Integer days=num(row.get("grace_days_snapshot"));
        Instant expiry=time(row.get("expire_at"));
        Instant holdUntil=time(row.get("grace_until_at"));
        if(days==null||days<0||expiry==null||expiry.isAfter(Instant.now())
                ||holdUntil==null||!holdUntil.equals(expiry.plusSeconds(days*86400L)))
            throw error("GRACE_SNAPSHOT_INVALID","存量宽限权益不完整或服务已续期，请先核对权益");

        JsonNode request=parse(row.get("task_request"));
        JsonNode result=parse(row.get("task_result"));
        JsonNode proof=result.path("networkEvidence");
        if(!expiry.equals(timeText(request.path("effectiveExpireAt").asText("")))
                ||!Objects.equals(str(row.get("remote_operation_id")),result.path("operationId").asText())
                ||!"SUCCEEDED".equals(result.path("remoteStatus").asText())
                ||!result.path("networkVerified").asBoolean(false)
                ||!"PRODUCT_GROUP_HTTP".equals(proof.path("source").asText())
                ||!"STOPPED".equals(proof.path("actualStatus").asText())
                ||!"VERIFIED".equals(proof.path("verificationStatus").asText()))
            throw error("TASK_PROOF_INVALID","任务编号、原到期时间或产品组实际停服证据不匹配");
        Instant verifiedAt=timeText(proof.path("verifiedAt").asText(""));
        Instant sampledAt=timeText(proof.path("sampledAt").asText(""));
        // A stale verification predating expiry cannot prove this expiry stop.
        if(verifiedAt==null||sampledAt==null
                ||verifiedAt.isBefore(expiry)||sampledAt.isBefore(verifiedAt)
                ||verifiedAt.isAfter(Instant.now().plusSeconds(300))
                ||sampledAt.isAfter(Instant.now().plusSeconds(300)))
            throw error("TASK_PROOF_STALE","产品组停服验证时间不可信或早于本次到期时间");

        // A fixed IPv4 must remain reserved during grace. Require a complete
        // per-service claim; legacy records without a verified claim are blocked.
        List<Map<String,Object>> claims=jdbc.queryForList("""
            SELECT id,claim_mode,claim_status FROM svc_egress_claim
            WHERE service_id=? AND claim_status<>'RELEASED' ORDER BY id FOR UPDATE
            """,serviceId);
        if(claims.isEmpty())
            throw error("EGRESS_CLAIM_MISSING","固定IP服务缺少有效占用记录，不得标记到期停服完成");
        String requiredMode="EXCLUSIVE_IP".equals(str(row.get("product_type")))?"EXCLUSIVE":"SHARED";
        for(Map<String,Object> claim:claims){
            if(!requiredMode.equals(str(claim.get("claim_mode")))
                    ||!Set.of("RESERVED","ACTIVE").contains(str(claim.get("claim_status"))))
                throw error("EGRESS_CLAIM_INCONSISTENT","该服务固定IPv4占用状态异常，禁止自动处理");
        }

        int changed=jdbc.update("""
            UPDATE svc_instance
            SET status='EXPIRED',expiry_phase='GRACE_HELD',
                updated_by=?,updated_at=now(),version=version+1
            WHERE id=? AND deleted=FALSE AND status IN ('ACTIVE','SUSPENDED')
              AND expiry_phase='STOP_PENDING' AND expire_at=?
            """,actorUserId,serviceId,OffsetDateTime.ofInstant(expiry,java.time.ZoneOffset.UTC));
        if(changed!=1)throw error("SERVICE_STOP_CONCURRENT","服务已由其他操作改变，任务提交取消");
        int held=jdbc.update("""
            UPDATE svc_egress_claim
            SET claim_status='GRACE_HELD',hold_until_at=?,updated_at=now()
            WHERE service_id=? AND claim_status IN ('RESERVED','ACTIVE')
            """,OffsetDateTime.ofInstant(holdUntil,java.time.ZoneOffset.UTC),serviceId);
        if(held!=claims.size())
            throw error("EGRESS_CLAIM_CONCURRENT","出口占用在处理过程中已发生变化，回滚服务状态");
        // Disable this service's platform credentials, but keep line/exclusive
        // ownership (including old allocation tables) until real release.
        jdbc.update("""
            UPDATE svc_credential SET status='FROZEN',updated_at=now(),version=version+1
            WHERE service_id=? AND deleted=FALSE AND status='ACTIVE'
            """,serviceId);
        int finished=jdbc.update("""
            UPDATE svc_action_task
            SET task_status='SUCCEEDED',stage='BUSINESS_COMMITTED',
                actor_user_id=?,finished_at=now(),updated_at=now(),
                last_error_code=NULL,last_error_message=NULL
            WHERE id=? AND action_type='STOP_FOR_GRACE'
              AND task_status='VERIFYING' AND stage='NEEDS_BUSINESS_COMMIT'
            """,actorUserId,taskId);
        if(finished!=1)throw error("TASK_COMMIT_CONCURRENT","任务已变更，回滚业务状态");
        return new CommitResult(key,serviceId,"EXPIRED","GRACE_HELD","SUCCEEDED",
                held,false,true,"当前服务已确认停服并进入固定IPv4宽限保留；不释放出口、不影响其他共享客户");
    }

    private JsonNode parse(Object data){
        try{return json.readTree(str(data));}
        catch(Exception e){throw error("TASK_PROOF_INVALID","任务证据格式无法读取");}
    }
    private static Integer num(Object value){return value instanceof Number n?n.intValue():null;}
    private static String str(Object value){return value==null?"":value.toString();}
    private static Instant time(Object obj){
        if(obj instanceof OffsetDateTime t)return t.toInstant();
        if(obj instanceof Timestamp t)return t.toInstant();
        return null;
    }
    private static Instant timeText(String value){
        try{return OffsetDateTime.parse(value).toInstant();}
        catch(Exception e){return null;}
    }
    private static BusinessException error(String code,String msg){return new BusinessException(code,msg);}
}
