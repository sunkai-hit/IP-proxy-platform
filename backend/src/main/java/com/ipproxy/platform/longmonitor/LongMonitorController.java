package com.ipproxy.platform.longmonitor;

import com.ipproxy.platform.common.api.ApiResponse;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1/long-monitor")
public class LongMonitorController {
    private final LongMonitorService service;
    public LongMonitorController(LongMonitorService service){this.service=service;}
    private void require(UserPrincipal p,String permission){if(p==null||(!p.roles().contains("SUPER_ADMIN")&&!p.permissions().contains(permission)))throw new BusinessException("AUTH_FORBIDDEN","当前账号无此操作权限");}
    private String ip(HttpServletRequest r){return r.getRemoteAddr()==null?"":r.getRemoteAddr();}
    public record ReasonRequest(String reason){} public record EnabledRequest(boolean enabled,String reason){}

    @GetMapping("/overview") public ApiResponse<?> overview(@AuthenticationPrincipal UserPrincipal p){require(p,"long-monitor:read");return ApiResponse.success(service.overview());}
    @GetMapping("/options") public ApiResponse<?> options(@RequestParam(required=false)Long customerId,@AuthenticationPrincipal UserPrincipal p){require(p,"long-monitor:read");return ApiResponse.success(service.options(customerId));}

    @GetMapping("/bots") public ApiResponse<?> bots(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="")String keyword,@RequestParam(required=false)Long customerId,@RequestParam(required=false)Boolean enabled,@AuthenticationPrincipal UserPrincipal p){require(p,"long-monitor:bot:read");return ApiResponse.success(service.bots(page,size,keyword,customerId,enabled));}
    @PostMapping("/bots") public ApiResponse<?> createBot(@RequestBody LongMonitorService.BotInput b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"long-monitor:bot:write");return ApiResponse.success(Map.of("id",service.createBot(b,p,ip(r))));}
    @GetMapping("/bots/{id}") public ApiResponse<?> bot(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"long-monitor:bot:read");return ApiResponse.success(service.bot(id));}
    @PutMapping("/bots/{id}") public ApiResponse<?> updateBot(@PathVariable Long id,@RequestBody LongMonitorService.BotInput b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"long-monitor:bot:write");service.updateBot(id,b,p,ip(r));return ApiResponse.success();}
    @PostMapping("/bots/{id}/enabled") public ApiResponse<?> botEnabled(@PathVariable Long id,@RequestBody EnabledRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"long-monitor:bot:write");service.botEnabled(id,b.enabled(),b.reason(),p,ip(r));return ApiResponse.success();}
    @PostMapping("/bots/{id}/test") public ApiResponse<?> testBot(@PathVariable Long id,@RequestBody ReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"long-monitor:bot:test");return ApiResponse.success(service.testBot(id,b.reason(),p,ip(r)));}

    @GetMapping("/monitors") public ApiResponse<?> monitors(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="")String keyword,@RequestParam(defaultValue="")String status,@RequestParam(required=false)Long customerId,@RequestParam(required=false)Boolean enabled,@AuthenticationPrincipal UserPrincipal p){require(p,"long-monitor:read");return ApiResponse.success(service.monitors(page,size,keyword,status,customerId,enabled));}
    @PostMapping("/monitors") public ApiResponse<?> createMonitor(@RequestBody LongMonitorService.MonitorInput b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"long-monitor:write");return ApiResponse.success(Map.of("id",service.createMonitor(b,p,ip(r))));}
    @GetMapping("/monitors/{id}") public ApiResponse<?> monitor(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"long-monitor:read");return ApiResponse.success(service.monitor(id));}
    @PutMapping("/monitors/{id}") public ApiResponse<?> updateMonitor(@PathVariable Long id,@RequestBody LongMonitorService.MonitorInput b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"long-monitor:write");service.updateMonitor(id,b,p,ip(r));return ApiResponse.success();}
    @PostMapping("/monitors/{id}/enabled") public ApiResponse<?> monitorEnabled(@PathVariable Long id,@RequestBody EnabledRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"long-monitor:status");service.monitorEnabled(id,b.enabled(),b.reason(),p,ip(r));return ApiResponse.success();}
    @PostMapping("/monitors/{id}/check") public ApiResponse<?> check(@PathVariable Long id,@RequestBody ReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"long-monitor:check");return ApiResponse.success(service.check(id,b.reason(),p,ip(r)));}

    @GetMapping("/alarms") public ApiResponse<?> alarms(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="")String status,@RequestParam(required=false)Long customerId,@AuthenticationPrincipal UserPrincipal p){require(p,"long-monitor:alarm:read");return ApiResponse.success(service.alarms(page,size,status,customerId));}
    @GetMapping("/alarms/{id}") public ApiResponse<?> alarm(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"long-monitor:alarm:read");return ApiResponse.success(service.alarm(id));}
    @PostMapping("/alarms/{id}/close") public ApiResponse<?> close(@PathVariable Long id,@RequestBody ReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"long-monitor:alarm:close");service.closeAlarm(id,b.reason(),p,ip(r));return ApiResponse.success();}

    @GetMapping("/notifications") public ApiResponse<?> notifications(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="")String status,@RequestParam(required=false)Long customerId,@AuthenticationPrincipal UserPrincipal p){require(p,"long-monitor:notification:read");return ApiResponse.success(service.notifications(page,size,status,customerId));}
}
