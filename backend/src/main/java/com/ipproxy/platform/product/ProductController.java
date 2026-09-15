package com.ipproxy.platform.product;

import com.ipproxy.platform.common.api.ApiResponse;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin/v1")
public class ProductController {
    private final ProductService service; private final V12ProductLifecycleService lifecycle;
    public ProductController(ProductService service,V12ProductLifecycleService lifecycle){this.service=service;this.lifecycle=lifecycle;}
    private void require(UserPrincipal p,String permission){if(p==null||(!p.roles().contains("SUPER_ADMIN")&&!p.permissions().contains(permission)))throw new BusinessException("AUTH_FORBIDDEN","当前账号无此操作权限");}
    private String ip(HttpServletRequest r){return r.getRemoteAddr()==null?"":r.getRemoteAddr();}

    @GetMapping("/products/overview") public ApiResponse<?> overview(@AuthenticationPrincipal UserPrincipal p){require(p,"product:read");return ApiResponse.success(service.overview());}
    @GetMapping("/products/options") public ApiResponse<?> options(@AuthenticationPrincipal UserPrincipal p){require(p,"product:read");return ApiResponse.success(service.options());}
    @GetMapping("/products") public ApiResponse<?> products(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String productType,@AuthenticationPrincipal UserPrincipal p){require(p,"product:read");return ApiResponse.success(service.products(page,size,keyword,status,productType));}
    @PostMapping("/products") public ApiResponse<?> create(@RequestBody ProductService.ProductInput b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"product:write");return ApiResponse.success(Map.of("id",service.createProduct(b,p,ip(r))));}
    @GetMapping("/products/{id}") public ApiResponse<?> product(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"product:read");Map<String,Object> out=new LinkedHashMap<>(service.product(id));out.put("builtinPolicy",lifecycle.policy(id));return ApiResponse.success(out);}
    @PutMapping("/products/{id}") public ApiResponse<?> update(@PathVariable Long id,@RequestBody ProductService.ProductInput b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"product:write");service.updateProduct(id,b,p,ip(r));return ApiResponse.success();}
    @GetMapping("/products/{id}/config") public ApiResponse<?> config(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"product:read");return ApiResponse.success(service.config(id));}
    @PutMapping("/products/{id}/config") public ApiResponse<?> updateConfig(@PathVariable Long id,@RequestBody Map<String,Object> b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"product:config");service.updateConfig(id,b,p,ip(r));return ApiResponse.success();}
    @GetMapping("/products/{id}/builtin-policy") public ApiResponse<?> builtinPolicy(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"product:read");return ApiResponse.success(lifecycle.policy(id));}
    @PostMapping("/products/{id}/enable") public ApiResponse<?> enable(@PathVariable Long id,@RequestBody ActionReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"product:status");lifecycle.enable(id,b.reason(),p,ip(r));return ApiResponse.success();}
    @PostMapping("/products/{id}/disable") public ApiResponse<?> disable(@PathVariable Long id,@RequestBody ActionReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"product:status");lifecycle.disable(id,b.reason(),p,ip(r));return ApiResponse.success();}
    @PostMapping("/products/{id}/copy") public ApiResponse<?> copy(@PathVariable Long id,@RequestBody ActionReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"product:write");return ApiResponse.success(Map.of("id",service.copyProduct(id,b.reason(),p,ip(r))));}

    @GetMapping("/packages") public ApiResponse<?> packages(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="") String status,@RequestParam(required=false) Long productId,@AuthenticationPrincipal UserPrincipal p){require(p,"product:package:read");return ApiResponse.success(service.packages(page,size,keyword,status,productId));}
    @PostMapping("/packages") public ApiResponse<?> createPackage(@RequestBody ProductService.PackageInput b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"product:package:write");return ApiResponse.success(Map.of("id",service.createPackage(b,p,ip(r))));}
    @GetMapping("/packages/{id}") public ApiResponse<?> pkg(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"product:package:read");return ApiResponse.success(service.pkg(id));}
    @PutMapping("/packages/{id}") public ApiResponse<?> updatePackage(@PathVariable Long id,@RequestBody ProductService.PackageInput b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"product:package:write");service.updatePackage(id,b,p,ip(r));return ApiResponse.success();}
    @PostMapping("/packages/{id}/copy") public ApiResponse<?> copyPackage(@PathVariable Long id,@RequestBody ActionReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"product:package:write");return ApiResponse.success(Map.of("id",service.copyPackage(id,b.reason(),p,ip(r))));}
    @PostMapping("/packages/{id}/enable") public ApiResponse<?> enablePackage(@PathVariable Long id,@RequestBody ActionReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"product:package:write");service.packageStatus(id,"ACTIVE",b.reason(),p,ip(r));return ApiResponse.success();}
    @PostMapping("/packages/{id}/disable") public ApiResponse<?> disablePackage(@PathVariable Long id,@RequestBody ActionReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"product:package:write");service.packageStatus(id,"DISABLED",b.reason(),p,ip(r));return ApiResponse.success();}

    /**
     * V1.2起资源策略不再是业务端配置项。以下接口仅保留历史兼容/数据回溯，不在正式菜单和开通过程中使用。
     */
    @Deprecated @GetMapping("/resource-strategies") public ApiResponse<?> strategies(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="") String status,@RequestParam(required=false) Long productId,@AuthenticationPrincipal UserPrincipal p){require(p,"product:strategy:read");return ApiResponse.success(service.strategies(page,size,keyword,status,productId));}
    @Deprecated @GetMapping("/resource-strategies/{id}") public ApiResponse<?> strategy(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"product:strategy:read");return ApiResponse.success(service.strategy(id));}

    public record ActionReasonRequest(String reason){}
}
