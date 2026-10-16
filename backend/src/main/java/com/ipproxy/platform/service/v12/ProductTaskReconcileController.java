package com.ipproxy.platform.service.v12;

import com.ipproxy.platform.common.api.ApiResponse;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Explicit operator reconciliation for persisted V22 tasks.
 * No remote write is exposed here; no task may be marked SUCCEEDED by request.
 */
@RestController
@RequestMapping("/api/admin/v1/v12/delivery-actions")
public class ProductTaskReconcileController {
    private final ProductTaskReconciler reconciler;
    private final ProductExpiryStopDispatcher dispatcher;
    private final ProductExpiryStopCommitter committer;
    public ProductTaskReconcileController(ProductTaskReconciler reconciler,
                                          ProductExpiryStopDispatcher dispatcher,
                                          ProductExpiryStopCommitter committer){
        this.reconciler=reconciler;this.dispatcher=dispatcher;this.committer=committer;
    }
    /**
     * Default-off privileged operation. Sends only a scoped PAUSE for an already
     * due service and keeps its business status and fixed IPv4 claims untouched.
     */
    @PostMapping("/{operationKey}/dispatch-expiry-stop")
    public ApiResponse<ProductExpiryStopDispatcher.DispatchResult> dispatchExpiryStop(
            @PathVariable String operationKey,@AuthenticationPrincipal UserPrincipal actor){
        requireRelease(actor);
        return ApiResponse.success(dispatcher.dispatch(operationKey,actor.userId()));
    }

    /**
     * The only allowed business commit for a verified expiry STOP_FOR_GRACE
     * task. It freezes this service's authorization and holds its own IPv4
     * claims in grace; shared peers and exclusive legacy allocations stay put.
     */
    @PostMapping("/{operationKey}/commit-expiry-stop")
    public ApiResponse<ProductExpiryStopCommitter.CommitResult> commitExpiryStop(
            @PathVariable String operationKey,@AuthenticationPrincipal UserPrincipal actor){
        requireRelease(actor);
        return ApiResponse.success(committer.commit(operationKey,actor.userId()));
    }

    private void requireRelease(UserPrincipal actor){
        if(actor==null||(!actor.roles().contains("SUPER_ADMIN")
                &&!actor.permissions().contains("service:release")))
            throw new BusinessException("AUTH_FORBIDDEN","当前账号无任务核对与真实停服权限");
    }
    @PostMapping("/{operationKey}/reconcile")
    public ApiResponse<ProductTaskReconciler.ReconcileResult> reconcile(
            @PathVariable String operationKey,@AuthenticationPrincipal UserPrincipal actor){
        requireRelease(actor);
        return ApiResponse.success(reconciler.reconcile(operationKey));
    }
}
