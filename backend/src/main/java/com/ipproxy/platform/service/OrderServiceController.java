package com.ipproxy.platform.service;

import com.ipproxy.platform.common.api.ApiResponse;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import com.ipproxy.platform.service.v12.V12ServiceLifecycleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1")
public class OrderServiceController {
    private final OrderServiceService service; private final V12ServiceLifecycleService lifecycle;
    public OrderServiceController(OrderServiceService service,V12ServiceLifecycleService lifecycle){this.service=service;this.lifecycle=lifecycle;}
    private void require(UserPrincipal p,String permission){if(p==null||(!p.roles().contains("SUPER_ADMIN")&&!p.permissions().contains(permission)))throw new BusinessException("AUTH_FORBIDDEN","当前账号无此操作权限");}
    private String ip(HttpServletRequest r){return r.getRemoteAddr()==null?"":r.getRemoteAddr();}
    public record ReasonRequest(String reason){} public record RenewRequest(Integer days,String reason){} public record WhitelistRequest(String ipAddress,String remark){}

    @GetMapping("/orders") public ApiResponse<?> orders(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="")String keyword,@RequestParam(defaultValue="")String status,@RequestParam(required=false)Long customerId,@AuthenticationPrincipal UserPrincipal p){require(p,"order:read");return ApiResponse.success(service.orders(page,size,keyword,status,customerId));}
    @PostMapping("/orders") public ApiResponse<?> createOrder(@RequestBody OrderServiceService.OrderInput b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"order:write");return ApiResponse.success(Map.of("id",service.createOrder(b,p,ip(r))));}
    @GetMapping("/orders/{id}") public ApiResponse<?> order(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"order:read");return ApiResponse.success(service.order(id));}
    @PostMapping("/orders/{id}/confirm") public ApiResponse<?> confirm(@PathVariable Long id,@RequestBody ReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"order:status");service.confirmOrder(id,b.reason(),p,ip(r));return ApiResponse.success();}
    @PostMapping("/orders/{id}/cancel") public ApiResponse<?> cancel(@PathVariable Long id,@RequestBody ReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"order:status");service.cancelOrder(id,b.reason(),p,ip(r));return ApiResponse.success();}
    @PostMapping("/orders/{id}/provision") public ApiResponse<?> provision(@PathVariable Long id,@RequestBody ReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"order:provision");return ApiResponse.success(lifecycle.provisionOrder(id,b.reason(),p,ip(r)));}

    @GetMapping("/services") public ApiResponse<?> services(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="")String keyword,@RequestParam(defaultValue="")String status,@RequestParam(required=false)Long customerId,@RequestParam(defaultValue="")String productType,@RequestParam(defaultValue="")String deliveryMode,@AuthenticationPrincipal UserPrincipal p){require(p,"service:read");return ApiResponse.success(lifecycle.services(page,size,keyword,status,customerId,productType,deliveryMode));}
    @PostMapping("/services/provision") public ApiResponse<?> manual(@RequestBody V12ServiceLifecycleService.ProvisionInput b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"service:provision");if("EXTERNAL_PROXY".equalsIgnoreCase(b.deliveryMode()))require(p,"service:provision:external");return ApiResponse.success(lifecycle.manualProvision(b,p,ip(r)));}
    @GetMapping("/services/{id}") public ApiResponse<?> detail(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"service:read");return ApiResponse.success(lifecycle.service(id));}
    @GetMapping("/services/{id}/credentials") public ApiResponse<?> credentials(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"service:credential:read");return ApiResponse.success(lifecycle.service(id).get("credentials"));}
    @PostMapping("/services/{id}/credentials/reset") public ApiResponse<?> reset(@PathVariable Long id,@RequestBody ReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"service:credential:reset");return ApiResponse.success(service.resetCredential(id,b.reason(),p,ip(r)));}
    @PostMapping("/services/{id}/suspend") public ApiResponse<?> suspend(@PathVariable Long id,@RequestBody ReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"service:status");service.suspend(id,b.reason(),p,ip(r));return ApiResponse.success();}
    @PostMapping("/services/{id}/resume") public ApiResponse<?> resume(@PathVariable Long id,@RequestBody ReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"service:status");service.resume(id,b.reason(),p,ip(r));return ApiResponse.success();}
    @PostMapping("/services/{id}/renew") public ApiResponse<?> renew(@PathVariable Long id,@RequestBody RenewRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"service:renew");return ApiResponse.success(Map.of("expireAt",service.renew(id,b.days()==null?0:b.days(),b.reason(),p,ip(r))));}
    @PostMapping("/services/{id}/terminate") public ApiResponse<?> terminate(@PathVariable Long id,@RequestBody ReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"service:status");return ApiResponse.success(service.terminate(id,b.reason(),p,ip(r)));}
    @GetMapping("/services/{id}/changes") public ApiResponse<?> changes(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"service:change:read");return ApiResponse.success(lifecycle.service(id).get("changes"));}
    @GetMapping("/services/{id}/resource-bindings") public ApiResponse<?> bindings(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"service:read");return ApiResponse.success(lifecycle.service(id).get("resourceBindings"));}
    @GetMapping("/services/{id}/whitelist") public ApiResponse<?> whitelist(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"service:read");return ApiResponse.success(lifecycle.service(id).get("whitelist"));}
    @PostMapping("/services/{id}/whitelist") public ApiResponse<?> addWhitelist(@PathVariable Long id,@RequestBody WhitelistRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"service:whitelist");return ApiResponse.success(Map.of("id",service.addWhitelist(id,b.ipAddress(),b.remark(),p,ip(r))));}
    @DeleteMapping("/services/{id}/whitelist/{wid}") public ApiResponse<?> disableWhitelist(@PathVariable Long id,@PathVariable Long wid,@RequestParam(defaultValue="手工移除白名单")String reason,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"service:whitelist");service.disableWhitelist(id,wid,reason,p,ip(r));return ApiResponse.success();}
    @GetMapping("/service-releases") public ApiResponse<?> releases(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="")String status,@AuthenticationPrincipal UserPrincipal p){require(p,"service:release");return ApiResponse.success(service.releases(page,size,status));}
    @GetMapping("/order-service/options") public ApiResponse<?> options(@AuthenticationPrincipal UserPrincipal p){require(p,"order-service:access");return ApiResponse.success(service.options());}
}
