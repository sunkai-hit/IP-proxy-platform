package com.ipproxy.platform.resource.external;

import com.ipproxy.platform.common.api.ApiResponse;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1/v12/external-delivery")
public class ExternalDeliveryController {
    private final ExternalDeliveryService service;
    public ExternalDeliveryController(ExternalDeliveryService service){this.service=service;}
    private void require(UserPrincipal p,String permission){if(p==null||(!p.roles().contains("SUPER_ADMIN")&&!p.permissions().contains(permission)))throw new BusinessException("AUTH_FORBIDDEN","当前账号无此操作权限");}
    private String ip(HttpServletRequest r){return r.getRemoteAddr()==null?"":r.getRemoteAddr();}
    public record ReasonRequest(String reason){} public record StatusRequest(String status,String reason){}

    @GetMapping("/options") public ApiResponse<?> options(@RequestParam(required=false)Long customerId,@AuthenticationPrincipal UserPrincipal p){require(p,"resource:purchase:read");return ApiResponse.success(service.options(customerId));}

    @GetMapping("/purchases") public ApiResponse<?> purchases(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="")String keyword,@RequestParam(defaultValue="")String status,@RequestParam(required=false)Long supplierId,@RequestParam(required=false)Long customerId,@AuthenticationPrincipal UserPrincipal p){require(p,"resource:purchase:read");return ApiResponse.success(service.purchases(page,size,keyword,status,supplierId,customerId));}
    @PostMapping("/purchases") public ApiResponse<?> createPurchase(@RequestBody ExternalDeliveryService.PurchaseInput b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"resource:purchase:write");return ApiResponse.success(Map.of("id",service.createPurchase(b,p,ip(r))));}
    @GetMapping("/purchases/{id}") public ApiResponse<?> purchase(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"resource:purchase:read");return ApiResponse.success(service.purchase(id));}
    @PutMapping("/purchases/{id}") public ApiResponse<?> updatePurchase(@PathVariable Long id,@RequestBody ExternalDeliveryService.PurchaseInput b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"resource:purchase:write");service.updatePurchase(id,b,p,ip(r));return ApiResponse.success();}
    @PostMapping("/purchases/{id}/status") public ApiResponse<?> purchaseStatus(@PathVariable Long id,@RequestBody StatusRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"resource:purchase:status");service.purchaseStatus(id,b.status(),b.reason(),p,ip(r));return ApiResponse.success();}

    @GetMapping("/reverse-proxies") public ApiResponse<?> proxies(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="")String keyword,@RequestParam(defaultValue="")String status,@RequestParam(required=false)Long customerId,@AuthenticationPrincipal UserPrincipal p){require(p,"resource:reverse-proxy:read");return ApiResponse.success(service.proxies(page,size,keyword,status,customerId));}
    @PostMapping("/reverse-proxies") public ApiResponse<?> createProxy(@RequestBody ExternalDeliveryService.ProxyInput b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"resource:reverse-proxy:write");return ApiResponse.success(Map.of("id",service.createProxy(b,p,ip(r))));}
    @GetMapping("/reverse-proxies/{id}") public ApiResponse<?> proxy(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"resource:reverse-proxy:read");return ApiResponse.success(service.proxy(id));}
    @PutMapping("/reverse-proxies/{id}") public ApiResponse<?> updateProxy(@PathVariable Long id,@RequestBody ExternalDeliveryService.ProxyInput b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"resource:reverse-proxy:write");service.updateProxy(id,b,p,ip(r));return ApiResponse.success();}
    @PostMapping("/reverse-proxies/{id}/apply") public ApiResponse<?> apply(@PathVariable Long id,@RequestBody ReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"resource:reverse-proxy:status");return ApiResponse.success(service.applyProxy(id,b.reason(),p,ip(r)));}
    @PostMapping("/reverse-proxies/{id}/test") public ApiResponse<?> test(@PathVariable Long id,@RequestBody ReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"resource:reverse-proxy:test");return ApiResponse.success(service.testProxy(id,b.reason(),p,ip(r)));}
    @PostMapping("/reverse-proxies/{id}/disable") public ApiResponse<?> disable(@PathVariable Long id,@RequestBody ReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"resource:reverse-proxy:status");return ApiResponse.success(service.disableProxy(id,b.reason(),p,ip(r)));}
}
