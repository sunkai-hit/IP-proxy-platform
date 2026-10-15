package com.ipproxy.platform.service.v12;

import com.ipproxy.platform.common.api.ApiResponse;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Shows the per-IP decision before any real release/short conversion.
 * No management ledger or product resource is released from this endpoint.
 */
@RestController
@RequestMapping("/api/admin/v1/v12/release-inspections")
public class ProductGraceReleaseController {
    private final ProductGraceReleaseInspector inspector;
    public ProductGraceReleaseController(ProductGraceReleaseInspector inspector){
        this.inspector=inspector;
    }
    @GetMapping("/{operationKey}")
    public ApiResponse<ProductGraceReleaseInspector.Inspection> inspect(
            @PathVariable String operationKey,@AuthenticationPrincipal UserPrincipal actor){
        if(actor==null||(!actor.roles().contains("SUPER_ADMIN")
                &&!actor.permissions().contains("service:release")))
            throw new BusinessException("AUTH_FORBIDDEN","当前账号无固定IPv4释放预检权限");
        return ApiResponse.success(inspector.inspect(operationKey));
    }
}
