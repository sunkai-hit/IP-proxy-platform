package com.ipproxy.platform.resource.v12;

import com.ipproxy.platform.common.api.ApiResponse;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authenticated operator read-only lens into the product group's actual
 * resource service. These snapshots are not allocations or delivery receipts.
 */
@RestController
@RequestMapping("/api/admin/v1/v12/product-integration")
public class ProductGroupReadController {
    private final ProductGroupReadClient gateway;
    public ProductGroupReadController(ProductGroupReadClient gateway){this.gateway=gateway;}

    private void require(UserPrincipal actor,String permission){
        if(actor==null||(!actor.roles().contains("SUPER_ADMIN")
                &&!actor.permissions().contains(permission)))
            throw new BusinessException("AUTH_FORBIDDEN","当前账号无此读取权限");
    }

    @GetMapping("/status")
    public ApiResponse<?> status(@AuthenticationPrincipal UserPrincipal actor){
        require(actor,"resource:read");
        return ApiResponse.success(gateway.integrationStatus());
    }
    @GetMapping("/capabilities")
    public ApiResponse<?> capabilities(@AuthenticationPrincipal UserPrincipal actor){
        require(actor,"resource:read");
        return ApiResponse.success(gateway.capabilities());
    }
    @GetMapping("/short-pool")
    public ApiResponse<?> shortPool(@AuthenticationPrincipal UserPrincipal actor){
        require(actor,"resource:read");
        return ApiResponse.success(gateway.shortPool());
    }
    @GetMapping("/long-pool")
    public ApiResponse<?> longPool(@AuthenticationPrincipal UserPrincipal actor){
        require(actor,"resource:read");
        return ApiResponse.success(gateway.longPool());
    }
    @GetMapping("/services/{serviceId}/actual-status")
    public ApiResponse<?> actualService(@PathVariable String serviceId,
            @AuthenticationPrincipal UserPrincipal actor){
        require(actor,"service:read");
        return ApiResponse.success(gateway.actualService(serviceId));
    }
    @GetMapping("/tasks/{operationId}")
    public ApiResponse<?> task(@PathVariable String operationId,
            @AuthenticationPrincipal UserPrincipal actor){
        require(actor,"service:read");
        return ApiResponse.success(gateway.taskStatus(operationId));
    }
}
