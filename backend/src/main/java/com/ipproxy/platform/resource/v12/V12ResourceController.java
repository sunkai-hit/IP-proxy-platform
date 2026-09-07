package com.ipproxy.platform.resource.v12;

import com.ipproxy.platform.common.api.ApiResponse;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin/v1/v12")
public class V12ResourceController {
    private final V12ResourceService service;
    public V12ResourceController(V12ResourceService service){this.service=service;}
    private void require(UserPrincipal p,String permission){if(p==null||(!p.roles().contains("SUPER_ADMIN")&&!p.permissions().contains(permission)))throw new BusinessException("AUTH_FORBIDDEN","当前账号无此操作权限");}
    private String ip(HttpServletRequest r){return r.getRemoteAddr()==null?"":r.getRemoteAddr();}

    @GetMapping("/resources/overview") public ApiResponse<?> overview(@AuthenticationPrincipal UserPrincipal p){require(p,"resource:read");return ApiResponse.success(service.overview());}
    @GetMapping("/resources/options") public ApiResponse<?> options(@AuthenticationPrincipal UserPrincipal p){require(p,"resource:read");return ApiResponse.success(service.options());}

    @GetMapping("/resources/centos") public ApiResponse<?> centos(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="")String keyword,@RequestParam(defaultValue="")String status,@AuthenticationPrincipal UserPrincipal p){require(p,"resource:read");return ApiResponse.success(service.centos(page,size,keyword,status));}
    @GetMapping("/resources/centos/{id}") public ApiResponse<?> centos(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"resource:read");return ApiResponse.success(service.centos(id));}

    @GetMapping("/resources/ros") public ApiResponse<?> ros(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="")String keyword,@RequestParam(defaultValue="")String status,@RequestParam(defaultValue="")String autoSwitch,@AuthenticationPrincipal UserPrincipal p){require(p,"resource:read");return ApiResponse.success(service.ros(page,size,keyword,status,autoSwitch));}
    @GetMapping("/resources/ros/{id}") public ApiResponse<?> ros(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"resource:read");return ApiResponse.success(service.ros(id));}
    @PostMapping("/resources/ros/{id}/auto-switch") public ApiResponse<?> autoSwitch(@PathVariable Long id,@RequestBody AutoSwitchInput b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"resource:ros:operate");return ApiResponse.success(service.setRosAutoSwitch(id,b.enabled(),b.reason(),p,ip(r)));}
    @PostMapping("/resources/ros/{id}/ha") public ApiResponse<?> ha(@PathVariable Long id,@RequestBody HaInput b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"resource:ros:operate");return ApiResponse.success(service.setRosHa(id,b.role(),b.replacementRosId(),b.switchStatus(),b.reason(),p,ip(r)));}

    @GetMapping("/resources/lines") public ApiResponse<?> lines(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="")String keyword,@RequestParam(defaultValue="")String status,@RequestParam(required=false)Long rosId,@RequestParam(defaultValue="")String type,@RequestParam(defaultValue="")String province,@RequestParam(defaultValue="")String city,@RequestParam(defaultValue="")String carrier,@RequestParam(required=false)Long customerId,@AuthenticationPrincipal UserPrincipal p){require(p,"resource:read");return ApiResponse.success(service.lines(page,size,keyword,status,rosId,type,province,city,carrier,customerId));}
    @GetMapping("/resources/lines/{id}") public ApiResponse<?> line(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"resource:read");return ApiResponse.success(service.line(id));}
    @GetMapping("/resources/lines/{id}/ip-history") public ApiResponse<?> ipHistory(@PathVariable Long id,@RequestParam(defaultValue="200")int limit,@AuthenticationPrincipal UserPrincipal p){require(p,"resource:read");return ApiResponse.success(service.lineIpHistory(id,limit));}
    @GetMapping("/resources/lines/{id}/operations") public ApiResponse<?> operations(@PathVariable Long id,@RequestParam(defaultValue="200")int limit,@AuthenticationPrincipal UserPrincipal p){require(p,"resource:read");return ApiResponse.success(service.lineOperations(id,limit));}
    @PostMapping("/resources/lines/{id}/type") public ApiResponse<?> lineType(@PathVariable Long id,@RequestBody LineTypeInput b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"resource:line:operate");return ApiResponse.success(service.setLineType(id,b.type(),b.reason(),p,ip(r)));}

    @GetMapping("/resource-pools") public ApiResponse<?> pools(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="")String keyword,@RequestParam(defaultValue="")String status,@RequestParam(defaultValue="")String province,@RequestParam(defaultValue="")String city,@RequestParam(defaultValue="")String carrier,@AuthenticationPrincipal UserPrincipal p){require(p,"resource:pool:read");return ApiResponse.success(service.pools(page,size,keyword,status,province,city,carrier));}
    @PostMapping("/resource-pools") public ApiResponse<?> createPool(@RequestBody V12ResourceService.PoolInput b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"resource:pool:write");return ApiResponse.success(Map.of("id",service.createPool(b,p,ip(r))));}
    @GetMapping("/resource-pools/{id}") public ApiResponse<?> pool(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"resource:pool:read");return ApiResponse.success(service.pool(id));}
    @PutMapping("/resource-pools/{id}") public ApiResponse<?> updatePool(@PathVariable Long id,@RequestBody V12ResourceService.PoolInput b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"resource:pool:write");service.updatePool(id,b,p,ip(r));return ApiResponse.success();}
    @GetMapping("/resource-pools/{id}/lines") public ApiResponse<?> poolLines(@PathVariable Long id,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="50")int size,@RequestParam(defaultValue="")String keyword,@RequestParam(defaultValue="")String status,@RequestParam(defaultValue="")String type,@AuthenticationPrincipal UserPrincipal p){require(p,"resource:pool:read");return ApiResponse.success(service.poolLines(id,page,size,keyword,status,type));}
    @GetMapping("/resource-pools/{id}/candidate-lines") public ApiResponse<?> candidateLines(@PathVariable Long id,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="")String keyword,@RequestParam(defaultValue="")String type,@AuthenticationPrincipal UserPrincipal p){require(p,"resource:pool:read");return ApiResponse.success(service.candidatePoolLines(id,page,size,keyword,type));}
    @PostMapping("/resource-pools/{id}/lines") public ApiResponse<?> addPoolLines(@PathVariable Long id,@RequestBody PoolLinesInput b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"resource:pool:write");return ApiResponse.success(Map.of("affected",service.addPoolLines(id,b.lineIds(),b.reason(),p,ip(r))));}
    @PostMapping("/resource-pools/{id}/lines/remove") public ApiResponse<?> removePoolLines(@PathVariable Long id,@RequestBody PoolLinesInput b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"resource:pool:write");return ApiResponse.success(Map.of("affected",service.removePoolLines(id,b.lineIds(),b.reason(),p,ip(r))));}
    @PutMapping("/resource-pools/{id}/lines") public ApiResponse<?> replacePoolLines(@PathVariable Long id,@RequestBody PoolLinesInput b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"resource:pool:write");return ApiResponse.success(Map.of("memberCount",service.replacePoolLines(id,b.lineIds(),b.reason(),p,ip(r))));}
    @GetMapping("/resource-pools/{id}/operations") public ApiResponse<?> poolOperations(@PathVariable Long id,@RequestParam(defaultValue="200")int limit,@AuthenticationPrincipal UserPrincipal p){require(p,"resource:pool:read");return ApiResponse.success(service.poolOperations(id,limit));}

    public record AutoSwitchInput(boolean enabled,String reason){}
    public record HaInput(String role,Long replacementRosId,String switchStatus,String reason){}
    public record LineTypeInput(String type,String reason){}
    public record PoolLinesInput(List<Long> lineIds,String reason){}
}
