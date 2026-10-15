package com.ipproxy.platform.service.v12;

import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.resource.v12.ProductGroupStopClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * At-most-one automatic submission attempt for a verified due STOP_FOR_GRACE
 * task. The operation ID and dispatch intent are committed before HTTP.
 * If the process dies or the reply is ambiguous, reconcile by operation ID;
 * never blindly issue a second POST.
 */
@Service
public class ProductExpiryStopDispatcher {
    private final JdbcTemplate jdbc;
    private final ProductGroupStopClient remote;
    public ProductExpiryStopDispatcher(JdbcTemplate jdbc,ProductGroupStopClient remote){
        this.jdbc=jdbc;this.remote=remote;
    }
    public record DispatchResult(String operationKey,String remoteOperationId,
             String taskStatus,String stage,boolean businessStateChanged,String note){}

    public DispatchResult dispatch(String key,Long actorUserId){
        if(key==null||key.isBlank()||key.length()>128)
            throw new BusinessException("TASK_OPERATION_INVALID","无效操作编号");
        if(!remote.ready())
            throw new BusinessException("PRODUCT_WRITE_DISABLED",
                    "尚未签收产品组实际停服写接口；禁止发送远程命令");
        List<Map<String,Object>> rows=jdbc.queryForList("""
            SELECT t.id,t.operation_key,t.service_id,t.action_type,t.task_status,
                   t.remote_operation_id,
                   s.product_type,s.delivery_mode,s.status service_status,
                   s.expire_at,s.grace_days_snapshot,s.expiry_phase
            FROM svc_action_task t JOIN svc_instance s ON s.id=t.service_id AND s.deleted=FALSE
            WHERE t.operation_key=?
            """,key);
        if(rows.isEmpty())throw new BusinessException("TASK_NOT_FOUND","操作任务不存在");
        Map<String,Object> t=rows.getFirst();
        if(!"STOP_FOR_GRACE".equals(text(t,"action_type"))
                ||!"PRECHECK_BLOCKED".equals(text(t,"task_status"))
                ||t.get("remote_operation_id")!=null
                ||!Set.of("LONG_IP","EXCLUSIVE_IP").contains(text(t,"product_type"))
                ||!"SELF_OWNED".equals(text(t,"delivery_mode"))
                ||!Set.of("ACTIVE","SUSPENDED").contains(text(t,"service_status"))
                ||!"STOP_PENDING".equals(text(t,"expiry_phase"))
                ||t.get("grace_days_snapshot")==null)
            throw new BusinessException("TASK_PRECHECK_BLOCKED",
                    "服务/宽限/交付模式或任务状态未满足真实到期停服前置条件");
        Instant expire;
        Object expiry=t.get("expire_at");
        if(expiry instanceof OffsetDateTime time)expire=time.toInstant();
        else if(expiry instanceof Timestamp sql)expire=sql.toInstant();
        else throw new BusinessException("TASK_EXPIRY_INVALID","服务到期时间不存在");
        if(expire.isAfter(Instant.now()))
            throw new BusinessException("TASK_NOT_DUE","服务尚未到期，不能提前停服");
        Long serviceId=((Number)t.get("service_id")).longValue();
        // Stable idempotency ID that product v1 accepts in the URL and body.
        // It can be reconstructed from local task key even after a crash.
        String remoteId=UUID.nameUUIDFromBytes(("ip-v12-expiry:"+key).getBytes(StandardCharsets.UTF_8))
                .toString().replace("-","");
        // No @Transactional: this CAS write commits before the network call.
        int claimed=jdbc.update("""
            UPDATE svc_action_task
            SET task_status='RUNNING',stage='DISPATCH_IN_FLIGHT',remote_operation_id=?,
                attempt_count=attempt_count+1,updated_at=now()
            WHERE id=? AND action_type='STOP_FOR_GRACE'
              AND task_status='PRECHECK_BLOCKED' AND remote_operation_id IS NULL
            """,remoteId,t.get("id"));
        if(claimed!=1)
            throw new BusinessException("TASK_ALREADY_DISPATCHED",
                    "任务已被其他调用方占用，禁止重复发送远程停服");
        try{
            ProductGroupStopClient.Acceptance receipt=
                    remote.pauseForExpiry(serviceId,remoteId,expire.toString());
            jdbc.update("""
                UPDATE svc_action_task
                SET task_status='RUNNING',stage='REMOTE_ACCEPTED',
                    last_error_code=NULL,last_error_message=NULL,updated_at=now()
                WHERE id=? AND remote_operation_id=? AND stage='DISPATCH_IN_FLIGHT'
                """,t.get("id"),remoteId);
            return new DispatchResult(key,remoteId,"RUNNING","REMOTE_ACCEPTED",false,
                    "仅收到产品组202异步受理；服务仍须查单和真实停服验证，不得释放固定IPv4");
        }catch(BusinessException ignored){
            // Even HTTP 4xx/5xx or timeout can be ambiguous if the remote
            // already started work. Preserve the ID and DO NOT auto retry.
            jdbc.update("""
                UPDATE svc_action_task SET task_status='COMPENSATION_REQUIRED',
                    stage='DISPATCH_RESULT_UNCERTAIN',
                    last_error_code='REMOTE_DISPATCH_UNCERTAIN',
                    last_error_message='请按remote_operation_id查询产品组真实状态，不得自动重发',
                    updated_at=now()
                WHERE id=? AND remote_operation_id=? AND stage='DISPATCH_IN_FLIGHT'
                """,t.get("id"),remoteId);
            return new DispatchResult(key,remoteId,"COMPENSATION_REQUIRED",
                    "DISPATCH_RESULT_UNCERTAIN",false,
                    "产品组执行结果未知，保持服务与出口占用，需按原操作编号查单");
        }
    }
    private static String text(Map<String,Object> row,String field){
        Object value=row.get(field);return value==null?"":value.toString();
    }
}
