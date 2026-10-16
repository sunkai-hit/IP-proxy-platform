package com.ipproxy.platform.service.v12;

import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.resource.v12.ProductGroupReadClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.Duration;
import java.util.*;

/**
 * Durable one-way reconciliation of an ALREADY SUBMITTED product operation.
 *
 * The V22 svc_action_task table is the local ledger. This component never
 * issues product writes, resends ambiguous operations or marks business services
 * ACTIVE/EXPIRED/RELEASED. A remote 'SUCCEEDED' is only a network verification
 * candidate, not a business-side commit or claim release.
 */
@Service
public class ProductTaskReconciler {
    private final JdbcTemplate jdbc;
    private final ProductGroupReadClient product;
    private final ObjectMapper json;
    private static final Set<String> TERMINAL=Set.of("SUCCEEDED","FAILED");
    private static final Set<String> POLLABLE=Set.of(
            "PENDING","RUNNING","VERIFYING","COMPENSATION_REQUIRED","PRECHECK_BLOCKED");

    public ProductTaskReconciler(JdbcTemplate jdbc,ProductGroupReadClient product,ObjectMapper json){
        this.jdbc=jdbc;this.product=product;this.json=json;
    }

    public record ReconcileResult(String operationKey,Long serviceId,String taskStatus,
          String stage,String remoteStatus,boolean networkVerified,
          boolean businessStateChanged,String note){}

    public ReconcileResult reconcile(String operationKey) {
        if(operationKey==null||operationKey.isBlank()||operationKey.length()>128)
            throw new BusinessException("TASK_OPERATION_INVALID","任务标识不能为空且不得超过128字符");
        List<Map<String,Object>> found=jdbc.queryForList("""
            SELECT id,operation_key,service_id,action_type,task_status,stage,
                   remote_operation_id,attempt_count,request_summary::text request_json
            FROM svc_action_task WHERE operation_key=?
            """,operationKey);
        if(found.isEmpty())throw new BusinessException("TASK_NOT_FOUND","任务不存在");
        Map<String,Object> task=found.getFirst();
        Long serviceId=((Number)task.get("service_id")).longValue();
        String local=str(task.get("task_status"));
        String remoteId=str(task.get("remote_operation_id"));
        if(TERMINAL.contains(local))
            return result(operationKey,serviceId,local,str(task.get("stage")),"NOT_POLLED",false,
                    "任务已终结；不会重新执行或覆写既有结果");
        if(!POLLABLE.contains(local))
            throw new BusinessException("TASK_STATUS_INVALID","操作任务状态不允许对账");
        if(remoteId.isBlank())
            return result(operationKey,serviceId,local,str(task.get("stage")),"NOT_SENT",false,
                    "未登记远端操作编号；保持本地任务，不能推定产品组已执行");
        Map<String,Object> remote;
        try{remote=product.taskStatus(remoteId);}
        catch(BusinessException e){
            // Transport/read errors are explicitly non-final. In particular,
            // UNKNOWN and HTTP timeouts may mean the remote action has executed.
            return result(operationKey,serviceId,local,str(task.get("stage")),"UNAVAILABLE",false,
                    "产品组查单失败，原任务及资源占用保持不变；不得重发操作");
        }
        // A task receipt that explicitly refuses or omits acceptance is never
        // trustworthy as successful execution, even if its status says SUCCEEDED.
        String remoteStatus=Boolean.TRUE.equals(remote.get("accepted"))
                ?str(remote.get("status")):"UNKNOWN";
        boolean verified=false;
        Map<String,Object> networkEvidence=new LinkedHashMap<>();
        String stage;
        String next;
        switch(remoteStatus){
            case "ACCEPTED","PENDING" -> {next="PENDING";stage="REMOTE_ACCEPTED";}
            case "RUNNING" -> {next="RUNNING";stage="REMOTE_RUNNING";}
            case "VERIFYING" -> {next="VERIFYING";stage="REMOTE_VERIFYING";}
            case "SUCCEEDED" -> {
                next="VERIFYING";stage="WAITING_NETWORK_VERIFICATION";
                try {
                    Map<String,Object> actual=product.actualService(String.valueOf(serviceId));
                    String actualStatus=str(actual.get("actualStatus"));
                    boolean rightState=switch(str(task.get("action_type"))){
                        case "STOP_FOR_GRACE","TERMINATE" -> "STOPPED".equals(actualStatus);
                        case "RELEASE" -> "STOPPED".equals(actualStatus) && releaseVerified(remote);
                        case "PROVISION","RENEW_RESTORE" -> "RUNNING".equals(actualStatus);
                        default -> false;
                    };
                    verified=rightState
                          &&"VERIFIED".equals(str(actual.get("verificationStatus")))
                          &&validActualProof(actual)
                          &&(!"RELEASE".equals(str(task.get("action_type")))
                              ||releaseVerified(remote,str(task.get("request_json"))));
                    if(verified){
                        stage="NEEDS_BUSINESS_COMMIT";
                        networkEvidence.put("actualStatus",actualStatus);
                        networkEvidence.put("verificationStatus","VERIFIED");
                        networkEvidence.put("verifiedAt",actual.get("verifiedAt"));
                        networkEvidence.put("sampledAt",actual.get("sampledAt"));
                        networkEvidence.put("source","PRODUCT_GROUP_HTTP");
                        if("RELEASE".equals(str(task.get("action_type"))))
                            networkEvidence.put("releaseVerification",remote.get("releaseVerification"));
                    }
                }catch(BusinessException ignored){
                    // Remote SUCCEEDED without a matching real data-plane proof
                    // MUST stay VERIFYING.
                }
            }
            case "FAILED","COMPENSATION_REQUIRED","UNKNOWN" -> {
                next="COMPENSATION_REQUIRED";stage="REMOTE_NEEDS_MANUAL_REVIEW";
            }
            default -> throw new BusinessException("TASK_REMOTE_STATUS_INVALID","远端任务状态无效");
        }
        Map<String,Object> summary=new LinkedHashMap<>();
        summary.put("operationId",remoteId);
        summary.put("remoteStatus",remoteStatus);
        summary.put("networkVerified",verified);
        if(verified)summary.put("networkEvidence",networkEvidence);
        summary.put("businessStateChanged",false);
        summary.put("receivedAt",OffsetDateTime.now().toString());
        String serialized;
        try{serialized=json.writeValueAsString(summary);}
        catch(Exception e){throw new BusinessException("TASK_JSON_ERROR","远端任务摘要无法序列化");}
        // Optimistic compare-and-swap: another operator or worker may have
        // already updated the task. Never roll a terminal state backwards.
        int updated=jdbc.update("""
            UPDATE svc_action_task
            SET task_status=?,stage=?,result_summary=CAST(? AS jsonb),
                last_error_code=?,last_error_message=?,updated_at=now()
            WHERE id=? AND task_status=? AND remote_operation_id=?
            """,next,stage,serialized,
            "COMPENSATION_REQUIRED".equals(next)?"REMOTE_STATUS_UNCERTAIN":null,
            "COMPENSATION_REQUIRED".equals(next)?"停止盲目重试；需检查产品组实际代理状态":null,
            task.get("id"),local,remoteId);
        if(updated!=1)
            throw new BusinessException("TASK_CONCURRENT_MODIFICATION","任务已被其他操作更新，请重新查询");
        return result(operationKey,serviceId,next,stage,remoteStatus,verified,
                verified?"仅验证产品组的执行声明和运行状态；待业务审核提交，服务状态与IPv4占用未更改":
                "任务进展已记录，尚未满足真实交付和业务状态提交条件");
    }

    /** A remote task SUCCESS alone never certifies an IPv4 has been unbound or
     * restored to the short pool. Proof is checked again at business commit.
     */
    private boolean validActualProof(Map<String,Object> actual){
        Instant verifiedAt=parseTime(actual.get("verifiedAt"));
        Instant sampledAt=parseTime(actual.get("sampledAt"));
        Instant now=Instant.now();
        // A verified state without temporally coherent source evidence must
        // NEVER unlock business-commit review.
        return verifiedAt!=null&&sampledAt!=null
                &&!verifiedAt.isAfter(sampledAt)
                &&!sampledAt.isBefore(now.minus(Duration.ofMinutes(15)))
                &&!sampledAt.isAfter(now.plus(Duration.ofMinutes(2)));
    }

    private static Instant parseTime(Object raw){
        try{return OffsetDateTime.parse(String.valueOf(raw)).toInstant();}
        catch(Exception ignored){return null;}
    }

    private JsonNode parseRequest(String data){
        try{return json.readTree(data);}
        catch(Exception ignored){return null;}
    }

    private boolean releaseVerified(Map<String,Object> remote,String requestJson){
        Object raw=remote.get("releaseVerification");
        if(!(raw instanceof Map<?,?> proof))return false;
        JsonNode request=parseRequest(requestJson);
        if(request==null||!request.isObject())return false;
        String expectedMode=request.path("releaseMode").asText("");
        String expectedIpv4=request.path("egressIpv4").asText("");
        if(!Set.of("UNBIND_SERVICE_ONLY","RETURN_TO_SHORT_AFTER_VERIFICATION").contains(expectedMode)
                ||expectedIpv4.isBlank()
                ||!expectedMode.equals(str(proof.get("releaseMode")))
                ||!expectedIpv4.equals(str(proof.get("egressIpv4")))
                ||!Boolean.TRUE.equals(proof.get("serviceAccessRevoked")))
            return false;
        Instant proofAt=parseTime(proof.get("verifiedAt"));
        Instant now=Instant.now();
        if(proofAt==null||proofAt.isAfter(now.plus(Duration.ofMinutes(2))))
            return false;
        if("UNBIND_SERVICE_ONLY".equals(expectedMode))return true;
        return Boolean.TRUE.equals(proof.get("allOldAccessRevoked"))
                &&Boolean.TRUE.equals(proof.get("switchedToShort"))
                &&Boolean.TRUE.equals(proof.get("shortPoolHealthy"))
                &&proof.get("resourceId") instanceof String id &&!id.isBlank();
    }

    private ReconcileResult result(String key,Long serviceId,String status,String stage,
                                   String remote,boolean verified,String note){
        return new ReconcileResult(key,serviceId,status,stage,remote,verified,false,note);
    }
    private String str(Object value){return value==null?"":String.valueOf(value);}
}
