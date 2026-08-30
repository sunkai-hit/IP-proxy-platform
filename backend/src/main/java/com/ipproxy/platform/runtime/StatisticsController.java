package com.ipproxy.platform.runtime;

import com.ipproxy.platform.common.api.ApiResponse;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/admin/v1/statistics")
public class StatisticsController {
    private final StatisticsService service;
    public StatisticsController(StatisticsService service){this.service=service;}
    @GetMapping("/overview") public ApiResponse<?> overview(@AuthenticationPrincipal UserPrincipal p){require(p,"statistics:read");return ApiResponse.success(service.overview());}
    @GetMapping("/resources") public ApiResponse<?> resources(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,@AuthenticationPrincipal UserPrincipal p){require(p,"statistics:read");return ApiResponse.success(service.resource(from,to));}
    @GetMapping("/ips") public ApiResponse<?> ips(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,@AuthenticationPrincipal UserPrincipal p){require(p,"statistics:read");return ApiResponse.success(service.ip(from,to));}
    @GetMapping("/customers") public ApiResponse<?> customers(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,@AuthenticationPrincipal UserPrincipal p){require(p,"statistics:read");return ApiResponse.success(service.customers(from,to));}
    @GetMapping("/products") public ApiResponse<?> products(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,@AuthenticationPrincipal UserPrincipal p){require(p,"statistics:read");return ApiResponse.success(service.products(from,to));}
    @GetMapping("/suppliers") public ApiResponse<?> suppliers(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,@AuthenticationPrincipal UserPrincipal p){require(p,"statistics:read");return ApiResponse.success(service.suppliers(from,to));}
    @PostMapping("/recalculate") public ApiResponse<?> recalculate(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hour,@RequestParam(defaultValue="MANUAL") String triggerType,@AuthenticationPrincipal UserPrincipal p){require(p,"statistics:recalculate");return ApiResponse.success(service.recalculate(hour,triggerType.toUpperCase(),p.userId()));}
    private void require(UserPrincipal p,String permission){if(p==null||(!p.roles().contains("SUPER_ADMIN")&&!p.permissions().contains(permission)))throw new BusinessException("AUTH_FORBIDDEN","当前账号无此操作权限");}
}
