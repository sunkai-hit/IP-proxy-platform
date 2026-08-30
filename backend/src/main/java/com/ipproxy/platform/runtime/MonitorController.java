package com.ipproxy.platform.runtime;

import com.ipproxy.platform.common.api.ApiResponse;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/monitor")
public class MonitorController {
    private final MonitorService service;
    public MonitorController(MonitorService service){this.service=service;}
    @GetMapping("/overview") public ApiResponse<?> overview(@AuthenticationPrincipal UserPrincipal p){require(p,"monitor:read");return ApiResponse.success(service.overview());}
    @GetMapping("/objects") public ApiResponse<?> objects(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String objectType,@RequestParam(defaultValue="") String keyword,@AuthenticationPrincipal UserPrincipal p){require(p,"monitor:read");return ApiResponse.success(service.objects(page,size,objectType,keyword));}
    @GetMapping("/objects/{objectType}/{objectId}/metrics") public ApiResponse<?> metrics(@PathVariable String objectType,@PathVariable Long objectId,@RequestParam(defaultValue="") String metricCode,@RequestParam(defaultValue="24") int hours,@AuthenticationPrincipal UserPrincipal p){require(p,"monitor:read");return ApiResponse.success(service.metrics(objectType,objectId,metricCode,hours));}
    @PostMapping("/collect") public ApiResponse<?> collect(@RequestParam(defaultValue="MANUAL") String triggerType,@AuthenticationPrincipal UserPrincipal p){require(p,"monitor:collect");return ApiResponse.success(service.collect(triggerType.toUpperCase(),p.userId()));}
    private void require(UserPrincipal p,String permission){if(p==null||(!p.roles().contains("SUPER_ADMIN")&&!p.permissions().contains(permission)))throw new BusinessException("AUTH_FORBIDDEN","当前账号无此操作权限");}
}
