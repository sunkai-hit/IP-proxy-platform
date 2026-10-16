package com.ipproxy.platform.service.v12;

import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.resource.v12.ProductGroupStopClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/** Reuses the V22 task ledger. A release is dispatched once, only after
 * grace and global IPv4 preflight. Unknown POST results are reconciled by ID.
 * Remote 202 never modifies service or egress claims.
 */
@Service
public class ProductGraceReleaseDispatcher {
    private final JdbcTemplate jdbc;
    private final ProductGraceReleaseInspector inspector;
    private final ProductGroupStopClient product;

    public ProductGraceReleaseDispatcher(JdbcTemplate jdbc,
            ProductGraceReleaseInspector inspector,ProductGroupStopClient product) {
        this.jdbc=jdbc;this.inspector=inspector;this.product=product;
    }
    public record DispatchResult(String operationKey,String remoteOperationId,
           String taskStatus,String stage,boolean egressReleased,String note){}

    public DispatchResult dispatch(String key,Long actorId) {
        if(actorId==null||actorId<=0)throw new BusinessException("AUTH_FORBIDDEN","无操作员身份");
        if(!product.ready())throw new BusinessException("PRODUCT_WRITE_DISABLED","产品组释放写接口未签收或启用");
        var preflight=inspector.inspect(key);
        // One V22 RELEASE task is service-scoped. Multi-egress services remain
        // blocked until the product team supplies an atomic multi-egress contract.
        if(preflight.claims().size()!=1)
            throw new BusinessException("RELEASE_MULTIPLE_EGRESS_UNSUPPORTED","当前操作涉及多个公网出口，不允许部分释放");
        var plan=preflight.claims().getFirst();
        String remoteId=UUID.nameUUIDFromBytes(
                ("ip-v12-release:"+key).getBytes(StandardCharsets.UTF_8)).toString().replace("-","");
        int locked=jdbc.update("""
            UPDATE svc_action_task
            SET task_status='RUNNING',stage='DISPATCH_IN_FLIGHT',
                remote_operation_id=?,actor_user_id=?,attempt_count=attempt_count+1,
                request_summary=request_summary || jsonb_build_object(
                    'releaseMode',CAST(? AS text),'egressIpv4',CAST(? AS text)),
                updated_at=now()
            WHERE operation_key=? AND service_id=? AND action_type='RELEASE'
              AND task_status='PRECHECK_BLOCKED' AND remote_operation_id IS NULL
            """,remoteId,actorId,plan.releasePlan(),plan.ipv4(),key,preflight.serviceId());
        if(locked!=1)throw new BusinessException("TASK_ALREADY_DISPATCHED","操作已下发或被并发处理，禁止重新发送");
        try {
            product.releaseAfterGrace(preflight.serviceId(),remoteId,plan.releasePlan(),plan.ipv4());
            jdbc.update("""
                UPDATE svc_action_task SET task_status='RUNNING',stage='REMOTE_ACCEPTED',
                    last_error_code=NULL,last_error_message=NULL,updated_at=now()
                WHERE operation_key=? AND remote_operation_id=?
                  AND stage='DISPATCH_IN_FLIGHT'
                """,key,remoteId);
            return new DispatchResult(key,remoteId,"RUNNING","REMOTE_ACCEPTED",false,
                "仅记录产品组异步受理；待验证实际解绑或回短效后才允许业务落账");
        }catch(BusinessException e){
            jdbc.update("""
                UPDATE svc_action_task
                SET task_status='COMPENSATION_REQUIRED',stage='DISPATCH_RESULT_UNCERTAIN',
                    last_error_code='REMOTE_DISPATCH_UNCERTAIN',
                    last_error_message='按原远端操作编号查单，禁止自动重发',
                    updated_at=now()
                WHERE operation_key=? AND remote_operation_id=?
                  AND stage='DISPATCH_IN_FLIGHT'
                """,key,remoteId);
            return new DispatchResult(key,remoteId,"COMPENSATION_REQUIRED",
                "DISPATCH_RESULT_UNCERTAIN",false,"远端结果未知；占用与服务均未释放，必须原编号查单");
        }
    }
}
