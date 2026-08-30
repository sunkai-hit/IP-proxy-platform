package com.ipproxy.platform.customer;

import com.ipproxy.platform.common.api.ApiResponse;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin/v1")
public class CustomerController {
    private final CustomerService service; public CustomerController(CustomerService service){this.service=service;}
    private void require(UserPrincipal p,String permission){if(p==null||(!p.roles().contains("SUPER_ADMIN")&&!p.permissions().contains(permission)))throw new BusinessException("AUTH_FORBIDDEN","当前账号无此操作权限");}
    private String ip(HttpServletRequest r){return r.getRemoteAddr()==null?"":r.getRemoteAddr();}

    @GetMapping("/customers") public ApiResponse<?> customers(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String authStatus,@RequestParam(defaultValue="") String customerTypeCode,@AuthenticationPrincipal UserPrincipal p){require(p,"customer:read");return ApiResponse.success(service.customers(page,size,keyword,status,authStatus,customerTypeCode));}
    @GetMapping("/customers/options") public ApiResponse<?> options(@AuthenticationPrincipal UserPrincipal p){require(p,"customer:read");return ApiResponse.success(service.options());}
    @PostMapping("/customers") public ApiResponse<?> createCustomer(@RequestBody CustomerRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"customer:write");return ApiResponse.success(Map.of("id",service.createCustomer(b.customerName,b.customerTypeCode,b.contactName,b.contactPhone,b.contactEmail,b.ownerUserId,b.remark,p,ip(r))));}
    @GetMapping("/customers/{id}") public ApiResponse<?> customer(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"customer:read");return ApiResponse.success(service.detail(id));}
    @PutMapping("/customers/{id}") public ApiResponse<?> updateCustomer(@PathVariable Long id,@RequestBody CustomerRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"customer:write");service.updateCustomer(id,b.customerName,b.customerTypeCode,b.contactName,b.contactPhone,b.contactEmail,b.ownerUserId,b.remark,p,ip(r));return ApiResponse.success();}
    @PostMapping("/customers/{id}/freeze") public ApiResponse<?> freezeCustomer(@PathVariable Long id,@RequestBody ActionReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"customer:status");service.changeCustomerStatus(id,"FROZEN",b.reason,p,ip(r));return ApiResponse.success();}
    @PostMapping("/customers/{id}/resume") public ApiResponse<?> resumeCustomer(@PathVariable Long id,@RequestBody ActionReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"customer:status");service.changeCustomerStatus(id,"ACTIVE",b.reason,p,ip(r));return ApiResponse.success();}
    @PostMapping("/customers/{id}/disable") public ApiResponse<?> disableCustomer(@PathVariable Long id,@RequestBody ActionReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"customer:status");service.changeCustomerStatus(id,"DISABLED",b.reason,p,ip(r));return ApiResponse.success();}
    @GetMapping("/customers/{id}/services") public ApiResponse<?> services(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"customer:read");return ApiResponse.success(service.services(id));}
    @GetMapping("/customers/{id}/credentials") public ApiResponse<?> credentials(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"customer:credential:read");return ApiResponse.success(service.credentials(id));}
    @GetMapping("/customers/{id}/usage") public ApiResponse<?> usage(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"customer:read");return ApiResponse.success(service.usage(id));}
    @PostMapping("/customers/{id}/auth") public ApiResponse<?> createAuth(@PathVariable Long id,@RequestBody AuthCreateRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"customer:write");return ApiResponse.success(Map.of("id",service.createAuth(id,b.authTypeCode,b.submittedData,b.attachmentRefs,p,ip(r))));}

    @GetMapping("/customer-auth") public ApiResponse<?> auths(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) Long customerId,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="") String status,@AuthenticationPrincipal UserPrincipal p){require(p,"customer:auth:read");return ApiResponse.success(service.auths(page,size,customerId,keyword,status));}
    @GetMapping("/customer-auth/{id}") public ApiResponse<?> auth(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"customer:auth:read");return ApiResponse.success(service.auth(id));}
    @PostMapping("/customer-auth/{id}/approve") public ApiResponse<?> approve(@PathVariable Long id,@RequestBody AuthReviewRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"customer:auth:review");service.reviewAuth(id,true,b.opinion,p,ip(r));return ApiResponse.success();}
    @PostMapping("/customer-auth/{id}/reject") public ApiResponse<?> reject(@PathVariable Long id,@RequestBody AuthReviewRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"customer:auth:review");service.reviewAuth(id,false,b.opinion,p,ip(r));return ApiResponse.success();}

    @GetMapping("/customer-accounts") public ApiResponse<?> accounts(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) Long customerId,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="") String status,@AuthenticationPrincipal UserPrincipal p){require(p,"customer:account:read");return ApiResponse.success(service.accounts(page,size,customerId,keyword,status));}
    @PostMapping("/customer-accounts") public ApiResponse<?> createAccount(@RequestBody AccountCreateRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"customer:account:write");return ApiResponse.success(Map.of("id",service.createAccount(b.customerId,b.username,b.password,b.accountTypeCode,p,ip(r))));}
    @GetMapping("/customer-accounts/{id}") public ApiResponse<?> account(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"customer:account:read");return ApiResponse.success(service.account(id));}
    @PostMapping("/customer-accounts/{id}/reset-password") public ApiResponse<?> resetPassword(@PathVariable Long id,@RequestBody PasswordResetRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"customer:account:write");service.resetAccountPassword(id,b.password,b.reason,p,ip(r));return ApiResponse.success();}
    @PostMapping("/customer-accounts/{id}/freeze") public ApiResponse<?> freezeAccount(@PathVariable Long id,@RequestBody ActionReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"customer:account:write");service.changeAccountStatus(id,"FROZEN",b.reason,p,ip(r));return ApiResponse.success();}
    @PostMapping("/customer-accounts/{id}/resume") public ApiResponse<?> resumeAccount(@PathVariable Long id,@RequestBody ActionReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"customer:account:write");service.changeAccountStatus(id,"ACTIVE",b.reason,p,ip(r));return ApiResponse.success();}
    @PostMapping("/customer-accounts/{id}/disable") public ApiResponse<?> disableAccount(@PathVariable Long id,@RequestBody ActionReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"customer:account:write");service.changeAccountStatus(id,"DISABLED",b.reason,p,ip(r));return ApiResponse.success();}

    public record CustomerRequest(String customerName,String customerTypeCode,String contactName,String contactPhone,String contactEmail,Long ownerUserId,String remark){}
    public record ActionReasonRequest(String reason){}
    public record AuthCreateRequest(String authTypeCode,Map<String,Object> submittedData,List<String> attachmentRefs){}
    public record AuthReviewRequest(String opinion){}
    public record AccountCreateRequest(Long customerId,String username,String password,String accountTypeCode){}
    public record PasswordResetRequest(String password,String reason){}
}
