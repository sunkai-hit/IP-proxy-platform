package com.ipproxy.platform.service.v12;

import com.ipproxy.platform.common.api.ApiResponse;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** A deliberately narrow manual route for release dispatch and verified
 * business commit. Product writes stay disabled by default.
 */
@RestController
@RequestMapping("/api/admin/v1/v12/release-actions")
public class ProductGraceReleaseExecutionController {
    private final ProductGraceReleaseDispatcher dispatcher;
    private final ProductGraceReleaseCommitter committer;
    public ProductGraceReleaseExecutionController(ProductGraceReleaseDispatcher dispatcher,
                                                  ProductGraceReleaseCommitter committer){
        this.dispatcher=dispatcher;this.committer=committer;
    }
    @PostMapping("/{operationKey}/dispatch")
    public ApiResponse<ProductGraceReleaseDispatcher.DispatchResult> dispatch(
            @PathVariable String operationKey,@AuthenticationPrincipal UserPrincipal actor){
        requireRelease(actor);
        return ApiResponse.success(dispatcher.dispatch(operationKey,actor.userId()));
    }
    @PostMapping("/{operationKey}/commit")
    public ApiResponse<ProductGraceReleaseCommitter.Result> commit(
            @PathVariable String operationKey,@AuthenticationPrincipal UserPrincipal actor){
        requireRelease(actor);
        return ApiResponse.success(committer.commit(operationKey,actor.userId()));
    }
    private void requireRelease(UserPrincipal actor) {
        if(actor==null||(!actor.roles().contains("SUPER_ADMIN")
                &&!actor.permissions().contains("service:release")))
            throw new BusinessException("AUTH_FORBIDDEN","无资源释放操作权限");
    }
}
