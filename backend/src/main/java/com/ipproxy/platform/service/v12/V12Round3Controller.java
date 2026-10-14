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
    private final V12ExpiryTaskPlanner expiryPlanner;
    public V12Round3Controller(JdbcTemplate jdbc,OperationAuditRepository audit,V12ExpiryTaskPlanner expiryPlanner) {
        this.jdbc=jdbc;this.audit=audit;this.expiryPlanner=expiryPlanner;
    }

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
    public record ExpiryReconcileInput(Integer limit,String reason){}
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
        require(actor,"service:read");
        Map<String,Object> s=service(id);
        if(in==null||in.backupCandidateId()==null)
            throw error("BACKUP_REQUIRED","请选择该服务的备用候选");
        Map<String,Object> b=first("""
          SELECT b.id,b.line_id,b.candidate_status,b.health_status,b.last_checked_at,
                 host(b.expected_ipv4) expected_ipv4,
                 l.resource_code line_code,host(l.current_public_ip) current_public_ipv4,
                 l.online_status,l.last_sync_at,l.province_code,l.city_code,l.carrier_code
          FROM svc_backup_candidate b JOIN res_line l ON l.id=b.line_id AND l.deleted=FALSE
          WHERE b.id=? AND b.service_id=?
          """,in.backupCandidateId(),id);
        if(b==null)throw error("BACKUP_NOT_OWNED","备选不存在或不属于当前服务");
        Map<String,Object> current=first("""
          SELECT host(e.egress_ipv4) active_ipv4,e.claim_mode,e.claim_status,
                 e.source_line_id,l.resource_code source_line_code,
                 l.province_code,l.city_code,l.carrier_code
          FROM svc_egress_claim e LEFT JOIN res_line l ON l.id=e.source_line_id
          WHERE e.service_id=? AND e.claim_status<>'RELEASED'
          ORDER BY e.id DESC LIMIT 1
          """,id);
        String from=current==null?null:(String)current.get("active_ipv4");
        String expected=(String)b.get("expected_ipv4");
        String reported=(String)b.get("current_public_ipv4");
        boolean unknown=from==null||expected==null;
        boolean changed=unknown||!Objects.equals(from,expected);
        Boolean sameRegion=current==null?null:Objects.equals(current.get("province_code"),b.get("province_code"))
           &&Objects.equals(current.get("city_code"),b.get("city_code"));
        Boolean sameCarrier=current==null?null:Objects.equals(current.get("carrier_code"),b.get("carrier_code"));
        boolean conflicted=false;
        if(expected!=null){
            conflicted=Boolean.TRUE.equals(jdbc.queryForObject("""
              SELECT EXISTS(SELECT 1 FROM svc_egress_claim e
                WHERE e.egress_ipv4=CAST(? AS inet) AND e.service_id<>?
                AND e.claim_status IN ('RESERVED','ACTIVE','GRACE_HELD','RELEASE_PENDING')
                AND (?='EXCLUSIVE_IP' OR e.claim_mode='EXCLUSIVE'))
              """,Boolean.class,expected,id,String.valueOf(s.get("product_type"))));
        }
        List<String> blockers=new ArrayList<>();
        if(!Set.of("LONG_IP","EXCLUSIVE_IP").contains(String.valueOf(s.get("product_type"))))
            blockers.add("只有长效IP和独享IP服务可以参与此固定出口人工切换");
        if(!"CANDIDATE".equals(String.valueOf(b.get("candidate_status"))))
            blockers.add("该候选并非有效备用候选");
        if(!"ONLINE".equals(String.valueOf(b.get("online_status"))))
            blockers.add("备用线路当前未上报在线");
        if(expected==null)blockers.add("候选缺少已核对的预期公网IPv4");
        if(expected!=null&&!Objects.equals(expected,reported))
            blockers.add("候选预期IPv4和线路当前上报IPv4不一致，禁止凭旧快照切换");
        if(from==null)blockers.add("旧固定出口缺少有效占用快照，不得猜测或释放");
        if(conflicted)blockers.add("候选出口与其他服务未释放占用冲突");
        if(sameRegion==null||sameCarrier==null)
            blockers.add("旧出口地区/运营商没有完整证据，须核查合同承诺");
        if(Boolean.FALSE.equals(sameRegion)||Boolean.FALSE.equals(sameCarrier))
            blockers.add("跨地区或运营商变化需事先客户同意且产品组确认实际支持");
        if(changed)blockers.add("变更承诺固定IPv4需客户明确同意或进入故障补偿流程");
        blockers.add("资源API和五类产品的数据面切换/回退接口尚未签收，真实执行不可用");
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("allowed",false);
        out.put("adapterReady",false);
        out.put("blockers",blockers);
        out.put("serviceId",id);out.put("candidateId",b.get("id"));
        out.put("fromIpv4",from);out.put("toIpv4",expected);
        out.put("candidateReportedIpv4",reported);
        out.put("sourceLineCode",current==null?null:current.get("source_line_code"));
        out.put("candidateLineCode",b.get("line_code"));
        out.put("candidateHealth",b.get("health_status"));
        out.put("candidateLastCheckedAt",b.get("last_checked_at"));
        out.put("candidateLastSyncAt",b.get("last_sync_at"));
        out.put("sameRegion",sameRegion);out.put("sameCarrier",sameCarrier);
        out.put("changesFixedIp",changed);out.put("ipv4Conflict",conflicted);
        out.put("customerConsentRequired",changed||Boolean.FALSE.equals(sameRegion)||Boolean.FALSE.equals(sameCarrier));
        out.put("customerConsentVerified",false);
        out.put("currentPlatformEntry",s.get("platform_host")==null?null:
            s.get("platform_host")+":"+s.get("platform_port"));
        out.put("monitoringImpact","需重新确认当前服务的监控目标与通知机器人");
        out.put("checkedAt",OffsetDateTime.now());
        out.put("dataSource","MANAGEMENT_SNAPSHOT_NOT_REAL_FAILOVER_PROOF");
        return ApiResponse.success(out);
    }

    @PostMapping("/services/{id}/failover-tasks")
    public ApiResponse<?> submitFailover(@PathVariable Long id,@RequestBody FailoverInput in,
                 @RequestHeader("Idempotency-Key") String key,@AuthenticationPrincipal UserPrincipal actor) {
        require(actor,"service:change");service(id);
        throw error("FAILOVER_ADAPTER_UNAVAILABLE","人工主备切换真实适配器尚未签署，禁止提交或伪造成功结果");
    }

    @GetMapping("/service-expiry/queue")
    public ApiResponse<?> expiry(@RequestParam(defaultValue="") String phase,
                                 @AuthenticationPrincipal UserPrincipal actor) {
        require(actor,"service:release");
        return ApiResponse.success(expiryPlanner.queue(200,phase));
    }

    @PostMapping("/service-expiry/reconcile")
    public ApiResponse<?> reconcileExpiry(@RequestBody ExpiryReconcileInput input,
            @AuthenticationPrincipal UserPrincipal actor,HttpServletRequest request) {
        require(actor,"service:release");
        if(input==null||input.reason()==null||input.reason().isBlank())
            throw error("RECONCILE_REASON_REQUIRED","扫描到期任务需填写审计原因");
        Map<String,Object> result=expiryPlanner.planDue(input.limit()==null?200:input.limit());
        audit.success(actor,"SERVICE","EXPIRY",null,"PLAN_EXPIRY_TASKS",
                input.reason().trim()+"；本次仅登记未验证任务，不执行远端停服或释放",request.getRemoteAddr());
        return ApiResponse.success(result);
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
