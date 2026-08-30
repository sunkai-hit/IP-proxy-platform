package com.ipproxy.platform.openapi;

import com.ipproxy.platform.common.api.ApiResponse;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/runtime-logs")
public class RuntimeLogController {
    private final RuntimeLogService service;
    public RuntimeLogController(RuntimeLogService service){this.service=service;}

    @GetMapping("/ip-extract")
    public ApiResponse<?> extracts(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) Long serviceId,@AuthenticationPrincipal UserPrincipal p){require(p,"runtime-log:extract:read");return ApiResponse.success(service.extracts(page,size,keyword,serviceId));}

    @GetMapping("/api")
    public ApiResponse<?> apis(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) Long serviceId,@AuthenticationPrincipal UserPrincipal p){require(p,"runtime-log:api:read");return ApiResponse.success(service.apis(page,size,keyword,serviceId));}

    @GetMapping("/usage")
    public ApiResponse<?> usage(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String keyword,@RequestParam(required=false) Long serviceId,@AuthenticationPrincipal UserPrincipal p){require(p,"runtime-log:usage:read");return ApiResponse.success(service.usage(page,size,keyword,serviceId));}

    private void require(UserPrincipal p,String permission){if(p==null||(!p.roles().contains("SUPER_ADMIN")&&!p.permissions().contains(permission)))throw new BusinessException("AUTH_FORBIDDEN","当前账号无此操作权限");}
}
