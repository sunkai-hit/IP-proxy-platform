package com.ipproxy.platform.runtime;

import com.ipproxy.platform.common.api.ApiResponse;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1/alarms")
public class AlarmController {
    private final AlarmService service;
    public AlarmController(AlarmService service){this.service=service;}
    @GetMapping public ApiResponse<?> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String severity,@RequestParam(defaultValue="") String keyword,@AuthenticationPrincipal UserPrincipal p){require(p,"alarm:read");return ApiResponse.success(service.alarms(page,size,status.toUpperCase(),severity.toUpperCase(),keyword));}
    @GetMapping("/{id}") public ApiResponse<?> detail(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"alarm:read");return ApiResponse.success(service.alarm(id));}
    @PostMapping("/{id}/acknowledge") public ApiResponse<?> acknowledge(@PathVariable Long id,@RequestBody(required=false) Map<String,Object> b,@AuthenticationPrincipal UserPrincipal p){require(p,"alarm:handle");return ApiResponse.success(service.acknowledge(id,p.userId(),remark(b)));}
    @PostMapping("/{id}/process") public ApiResponse<?> process(@PathVariable Long id,@RequestBody(required=false) Map<String,Object> b,@AuthenticationPrincipal UserPrincipal p){require(p,"alarm:handle");return ApiResponse.success(service.startProcessing(id,p.userId(),remark(b)));}
    @PostMapping("/{id}/close") public ApiResponse<?> close(@PathVariable Long id,@RequestBody(required=false) Map<String,Object> b,@AuthenticationPrincipal UserPrincipal p){require(p,"alarm:handle");return ApiResponse.success(service.close(id,p.userId(),remark(b)));}
    @PostMapping("/{id}/notes") public ApiResponse<?> note(@PathVariable Long id,@RequestBody Map<String,Object> b,@AuthenticationPrincipal UserPrincipal p){require(p,"alarm:handle");return ApiResponse.success(service.addNote(id,p.userId(),remark(b)));}

    @GetMapping("/rules") public ApiResponse<?> rules(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String keyword,@AuthenticationPrincipal UserPrincipal p){require(p,"alarm:rule:read");return ApiResponse.success(service.rules(page,size,keyword));}
    @PostMapping("/rules") public ApiResponse<?> createRule(@RequestBody Map<String,Object> b,@AuthenticationPrincipal UserPrincipal p){require(p,"alarm:rule:write");return ApiResponse.success(service.createRule(b,p.userId()));}
    @PutMapping("/rules/{id}") public ApiResponse<?> updateRule(@PathVariable Long id,@RequestBody Map<String,Object> b,@AuthenticationPrincipal UserPrincipal p){require(p,"alarm:rule:write");return ApiResponse.success(service.updateRule(id,b,p.userId()));}
    @PostMapping("/rules/{id}/status") public ApiResponse<?> status(@PathVariable Long id,@RequestBody Map<String,Object> b,@AuthenticationPrincipal UserPrincipal p){require(p,"alarm:rule:write");return ApiResponse.success(service.setRuleStatus(id,String.valueOf(b.getOrDefault("status","")),p.userId()));}
    @DeleteMapping("/rules/{id}") public ApiResponse<?> deleteRule(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"alarm:rule:write");service.deleteRule(id,p.userId());return ApiResponse.success(Map.of("deleted",true));}

    @GetMapping("/notifications") public ApiResponse<?> notifications(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String channel,@RequestParam(defaultValue="") String result,@AuthenticationPrincipal UserPrincipal p){require(p,"alarm:notification:read");return ApiResponse.success(service.notifications(page,size,channel.toUpperCase(),result.toUpperCase()));}
    private String remark(Map<String,Object> b){return b==null?null:String.valueOf(b.getOrDefault("remark","")).trim();}
    private void require(UserPrincipal p,String permission){if(p==null||(!p.roles().contains("SUPER_ADMIN")&&!p.permissions().contains(permission)))throw new BusinessException("AUTH_FORBIDDEN","当前账号无此操作权限");}
}
