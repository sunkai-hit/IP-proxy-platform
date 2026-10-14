package com.ipproxy.platform.service.v12;

import com.ipproxy.platform.common.api.ApiResponse;
import com.ipproxy.platform.common.audit.OperationAuditRepository;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * V1.2 R3 增量只读工作区与套餐级宽限。
 * 注意：人工切换尚无已签署真实适配器，提交入口必须阻断，不伪造SUCCESS。
 */
@RestController
@RequestMapping("/api/admin/v1/v12")
public class V12Round3Controller {
    private final JdbcTemplate jdbc;
    private final OperationAuditRepository audit;
    public V12Round3Controller(JdbcTemplate jdbc,OperationAuditRepository audit) {this.jdbc=jdbc;this.audit=audit;}

    private void require(UserPrincipal actor,String permission) {
        if(actor==null || (!actor.roles().contains("SUPER_ADMIN") && !actor.permissions().contains(permission)))
            throw new BusinessException("AUTH_FORBIDDEN","当前账号没有所需业务权限");
    }
    private BusinessException error(String code,String message) {return new BusinessException(code,message);}
    private Map<String,Object> first(String sql,Object... args) {
        List<Map<String,Object>> rows=jdbc.queryForList(sql,args);
        return rows.isEmpty()?null:rows.getFirst();
    }
    private Map<String,Object> service(Long id) {
        Map<String,Object> row=first("""
          SELECT s.id,s.service_no,s.customer_id,c.customer_name,s.product_type,s.status,
                 s.delivery_mode,s.expire_at,s.grace_days_snapshot,s.grace_until_at,s.expiry_phase,
                 s.delivery_stage,s.last_delivery_verified_at,s.reverse_proxy_id,
                 rp.platform_host,rp.platform_port
          FROM svc_instance s JOIN customer c ON c.id=s.customer_id
          LEFT JOIN res_reverse_proxy rp ON rp.id=s.reverse_proxy_id
          WHERE s.id=? AND s.deleted=FALSE
          """,id);
        if(row==null)throw error("SERVICE_NOT_FOUND","服务不存在");
        return row;
    }
    public record GraceInput(Integer graceDays,String reason){}
    public record CandidateInput(Long backupCandidateId){}
    public record FailoverInput(Long backupCandidateId,Integer expectedServiceVersion,String reason){}

    @GetMapping("/packages/{id}/grace")
    public ApiResponse<?> packageGrace(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal actor) {
        require(actor,"product:package:read");
        Map<String,Object> p=first("""
          SELECT k.id "packageId",p.product_type "productType",k.grace_days "graceDays",
                 (SELECT count(*) FROM svc_instance s WHERE s.package_id=k.id AND s.deleted=FALSE) "activeServiceCount"
          FROM prd_package k JOIN prd_product p ON p.id=k.product_id WHERE k.id=? AND k.deleted=FALSE
          """,id);
        if(p==null)throw error("PACKAGE_NOT_FOUND","套餐不存在");
        Map<String,Object> out=new LinkedHashMap<>(p);out.put("existingServicesUnaffected",true);
        return ApiResponse.success(out);
    }

    @PutMapping("/packages/{id}/grace")
    @Transactional
    public ApiResponse<?> updateGrace(@PathVariable Long id,@RequestBody GraceInput input,
                       @AuthenticationPrincipal UserPrincipal actor,HttpServletRequest request) {
        require(actor,"product:package:write");
        if(input==null||input.graceDays()==null||input.graceDays()<0)
            throw error("PACKAGE_GRACE_INVALID","必须明确提供非负的宽限天数；0天表示到期立即停服并进入受控回收");
        if(input.reason()==null||input.reason().isBlank())
            throw error("PACKAGE_REASON_REQUIRED","必须填写修改原因");
        Map<String,Object> p=first("""
          SELECT k.id,p.product_type FROM prd_package k JOIN prd_product p ON p.id=k.product_id
          WHERE k.id=? AND k.deleted=FALSE FOR UPDATE OF k
          """,id);
        if(p==null)throw error("PACKAGE_NOT_FOUND","套餐不存在");
        if(!Set.of("LONG_IP","EXCLUSIVE_IP").contains(String.valueOf(p.get("product_type"))))
            throw error("PACKAGE_GRACE_NOT_APPLICABLE","只有长效与独享套餐支持固定IP宽限");
        jdbc.update("UPDATE prd_package SET grace_days=?,updated_at=now(),updated_by=?,version=version+1 WHERE id=?",
                    input.graceDays(),actor.userId(),id);
        audit.success(actor,"PRODUCT","PACKAGE",id,"UPDATE_GRACE_R3",input.reason().trim(),request.getRemoteAddr());
        return ApiResponse.success(Map.of("packageId",id,"graceDays",input.graceDays(),"existingServicesUnaffected",true));
    }

    @GetMapping("/services/{id}/delivery-summary")
    public ApiResponse<?> delivery(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal actor) {
        require(actor,"service:read");Map<String,Object> s=service(id);
        Map<String,Object> e=first("""
          SELECT host(e.egress_ipv4) "contractedEgressIpv4",host(l.current_public_ip) "currentReportedIpv4"
          FROM svc_egress_claim e LEFT JOIN res_line l ON l.id=e.source_line_id
          WHERE e.service_id=? AND e.claim_status<>'RELEASED' ORDER BY e.id LIMIT 1
          """,id);
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("serviceId",s.get("id"));out.put("serviceNo",s.get("service_no"));
        out.put("customerName",s.get("customer_name"));out.put("productType",s.get("product_type"));
        out.put("businessStatus",s.get("status"));out.put("deliveryStage",s.get("delivery_stage"));
        out.put("lastDeliveryVerifiedAt",s.get("last_delivery_verified_at"));
        out.put("expireAt",s.get("expire_at"));out.put("graceDaysSnapshot",s.get("grace_days_snapshot"));
        out.put("graceUntilAt",s.get("grace_until_at"));out.put("expiryPhase",s.get("expiry_phase"));
        out.put("contractedEgressIpv4",e==null?null:e.get("contractedEgressIpv4"));
        out.put("currentReportedIpv4",e==null?null:e.get("currentReportedIpv4"));
        Object host=s.get("platform_host"),port=s.get("platform_port");
        out.put("platformEntry",host==null?null:host+ (port==null?"":":"+port));
        out.put("dataSource","MANAGEMENT_DB_UNVERIFIED");
        out.put("sampledAt",null);
        return ApiResponse.success(out);
    }

    @GetMapping("/services/{id}/egress-claims")
    public ApiResponse<?> egressClaims(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal actor) {
        require(actor,"service:read");service(id);
        return ApiResponse.success(jdbc.queryForList("""
            SELECT id,host(egress_ipv4) "egressIpv4",claim_mode "mode",claim_status "claimStatus",
                   customer_id "customerId",source_line_id "sourceLineId",
                   hold_until_at "holdUntilAt",verified_at "verifiedAt"
            FROM svc_egress_claim WHERE service_id=? ORDER BY id
            """,id));
    }

    @GetMapping("/services/{id}/backup-candidates")
    public ApiResponse<?> backups(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal actor) {
        require(actor,"service:read");service(id);
        List<Map<String,Object>> rows=jdbc.queryForList("""
          SELECT b.id,b.line_id "lineId",host(b.expected_ipv4) "expectedIpv4",
                 b.health_status "health",b.last_checked_at "checkedAt",b.candidate_status
          FROM svc_backup_candidate b WHERE b.service_id=? ORDER BY b.id
          """,id);
        for(Map<String,Object> b:rows){b.put("eligible",false);b.put("reason","真实切换能力尚待资源API和产品组确认");}
        return ApiResponse.success(rows);
    }

    @PostMapping("/services/{id}/failover-preview")
    public ApiResponse<?> preview(@PathVariable Long id,@RequestBody CandidateInput in,
                                  @AuthenticationPrincipal UserPrincipal actor) {
        require(actor,"service:read");service(id);
        if(in==null||in.backupCandidateId()==null)throw error("BACKUP_REQUIRED","请选择该服务的备用候选");
        Map<String,Object> b=first("""
          SELECT id,host(expected_ipv4) expected_ip FROM svc_backup_candidate
          WHERE id=? AND service_id=? AND candidate_status='CANDIDATE'
          """,in.backupCandidateId(),id);
        if(b==null)throw error("BACKUP_NOT_OWNED","备选不存在或不属于当前服务");
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("allowed",false);out.put("blockers",List.of("真实资源API/产品数据面切换接口尚未完成能力签署"));
        out.put("toIpv4",b.get("expected_ip"));out.put("adapterReady",false);
        out.put("changesFixedIp",true);out.put("checkedAt",OffsetDateTime.now());
        return ApiResponse.success(out);
    }

    @PostMapping("/services/{id}/failover-tasks")
    public ApiResponse<?> submitFailover(@PathVariable Long id,@RequestBody FailoverInput in,
                 @RequestHeader("Idempotency-Key") String key,@AuthenticationPrincipal UserPrincipal actor) {
        require(actor,"service:change");service(id);
        throw error("FAILOVER_ADAPTER_UNAVAILABLE","人工主备切换真实适配器尚未签署，禁止提交或伪造成功结果");
    }

    @GetMapping("/service-expiry/queue")
    public ApiResponse<?> expiry(@RequestParam(defaultValue="")String phase,@AuthenticationPrincipal UserPrincipal actor) {
        require(actor,"service:release");
        return ApiResponse.success(jdbc.queryForList("""
          SELECT id "serviceId",service_no "serviceNo",product_type "productType",status "businessStatus",
                 delivery_stage "deliveryStage",expire_at "expireAt",grace_days_snapshot "graceDaysSnapshot",
                 grace_until_at "graceUntilAt",expiry_phase "expiryPhase"
          FROM svc_instance WHERE deleted=FALSE AND product_type IN ('LONG_IP','EXCLUSIVE_IP')
            AND (?='' OR expiry_phase=?) ORDER BY expire_at NULLS LAST LIMIT 200
          """,phase,phase));
    }

    @GetMapping("/delivery-actions/{operationKey}")
    public ApiResponse<?> action(@PathVariable String operationKey,@AuthenticationPrincipal UserPrincipal actor) {
        require(actor,"service:read");
        Map<String,Object> a=first("""
          SELECT operation_key "operationKey",service_id "serviceId",action_type "action",
                 task_status "taskStatus",stage,attempt_count "attemptCount",
                 remote_operation_id "remoteOperationId",last_error_code "lastErrorCode",
                 last_error_message "lastErrorMessage",created_at "createdAt",finished_at "finishedAt"
          FROM svc_action_task WHERE operation_key=?
          """,operationKey);
        if(a==null)throw error("TASK_NOT_FOUND","操作记录不存在");
        return ApiResponse.success(a);
    }
}
