package com.ipproxy.platform.system.admin;

import com.ipproxy.platform.common.api.ApiResponse;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/admin/v1/system")
public class SystemAdminController {
    private final SystemAdminService service; public SystemAdminController(SystemAdminService service){this.service=service;}
    private void require(UserPrincipal p,String permission){if(p==null||(!p.roles().contains("SUPER_ADMIN")&&!p.permissions().contains(permission)))throw new BusinessException("AUTH_FORBIDDEN","当前账号无此操作权限");}
    private String ip(HttpServletRequest r){return r.getRemoteAddr()==null?"":r.getRemoteAddr();}

    @GetMapping("/users") public ApiResponse<?> users(@AuthenticationPrincipal UserPrincipal p){require(p,"system:user:read");return ApiResponse.success(service.users());}
    @PostMapping("/users") public ApiResponse<?> createUser(@RequestBody UserRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"system:user:write");return ApiResponse.success(Map.of("id",service.createUser(b.username,b.password,b.displayName,b.department,b.mobile,b.email,b.status,b.roleIds,p,ip(r))));}
    @PutMapping("/users/{id}") public ApiResponse<?> updateUser(@PathVariable Long id,@RequestBody UserRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"system:user:write");service.updateUser(id,b.displayName,b.department,b.mobile,b.email,b.status,b.roleIds,p,ip(r));return ApiResponse.success();}
    @PostMapping("/users/{id}/reset-password") public ApiResponse<?> resetPassword(@PathVariable Long id,@RequestBody PasswordRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"system:user:write");service.resetPassword(id,b.password,p,ip(r));return ApiResponse.success();}

    @GetMapping("/roles") public ApiResponse<?> roles(@AuthenticationPrincipal UserPrincipal p){require(p,"system:role:read");return ApiResponse.success(service.roles());}
    @PostMapping("/roles") public ApiResponse<?> createRole(@RequestBody RoleRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"system:role:write");return ApiResponse.success(Map.of("id",service.createRole(b.roleCode,b.roleName,b.dataScope,b.description,b.status,p,ip(r))));}
    @PutMapping("/roles/{id}") public ApiResponse<?> updateRole(@PathVariable Long id,@RequestBody RoleRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"system:role:write");service.updateRole(id,b.roleName,b.dataScope,b.description,b.status,p,ip(r));return ApiResponse.success();}
    @GetMapping("/roles/{id}/permissions") public ApiResponse<?> rolePermissions(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"system:role:read");return ApiResponse.success(service.rolePermissionIds(id));}
    @PutMapping("/roles/{id}/permissions") public ApiResponse<?> authorize(@PathVariable Long id,@RequestBody IdsRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"system:role:write");service.replaceRolePermissions(id,b.ids,p,ip(r));return ApiResponse.success();}
    @GetMapping("/permissions") public ApiResponse<?> permissions(@AuthenticationPrincipal UserPrincipal p){require(p,"system:permission:read");return ApiResponse.success(service.permissions());}

    @GetMapping("/dict-types") public ApiResponse<?> dictTypes(@AuthenticationPrincipal UserPrincipal p){require(p,"system:dict:read");return ApiResponse.success(service.dictTypes());}
    @PostMapping("/dict-types") public ApiResponse<?> createDictType(@RequestBody DictTypeRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"system:dict:write");return ApiResponse.success(Map.of("id",service.createDictType(b.dictCode,b.dictName,b.description,b.editable,b.status,p,ip(r))));}
    @PutMapping("/dict-types/{id}") public ApiResponse<?> updateDictType(@PathVariable Long id,@RequestBody DictTypeRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"system:dict:write");service.updateDictType(id,b.dictName,b.description,b.editable,b.status,p,ip(r));return ApiResponse.success();}
    @GetMapping("/dict-types/{id}/items") public ApiResponse<?> dictItems(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p){require(p,"system:dict:read");return ApiResponse.success(service.dictItems(id));}
    @PostMapping("/dict-types/{id}/items") public ApiResponse<?> createDictItem(@PathVariable Long id,@RequestBody DictItemRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"system:dict:write");return ApiResponse.success(Map.of("id",service.createDictItem(id,b.itemCode,b.itemName,b.itemValue,b.sortOrder,b.colorTag,b.isDefault,b.status,b.remark,p,ip(r))));}
    @PutMapping("/dict-items/{id}") public ApiResponse<?> updateDictItem(@PathVariable Long id,@RequestBody DictItemRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"system:dict:write");service.updateDictItem(id,b.itemName,b.itemValue,b.sortOrder,b.colorTag,b.isDefault,b.status,b.remark,p,ip(r));return ApiResponse.success();}

    @GetMapping("/parameters") public ApiResponse<?> parameters(@AuthenticationPrincipal UserPrincipal p){require(p,"system:param:read");return ApiResponse.success(service.parameters());}
    @PostMapping("/parameters") public ApiResponse<?> createParameter(@RequestBody ParameterRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"system:param:write");return ApiResponse.success(Map.of("id",service.createParameter(b.paramGroup,b.paramCode,b.paramName,b.paramValue,b.valueType,b.sensitive,b.description,b.status,p,ip(r))));}
    @PutMapping("/parameters/{id}") public ApiResponse<?> updateParameter(@PathVariable Long id,@RequestBody ParameterRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){require(p,"system:param:write");service.updateParameter(id,b.paramGroup,b.paramName,b.paramValue,b.valueType,b.sensitive,b.description,b.status,p,ip(r));return ApiResponse.success();}

    @GetMapping("/login-logs") public ApiResponse<?> loginLogs(@AuthenticationPrincipal UserPrincipal p){require(p,"system:login-log:read");return ApiResponse.success(service.loginLogs());}
    @GetMapping("/operation-logs") public ApiResponse<?> operationLogs(@AuthenticationPrincipal UserPrincipal p){require(p,"system:operation-log:read");return ApiResponse.success(service.operationLogs());}

    public record UserRequest(String username,String password,String displayName,String department,String mobile,String email,String status,List<Long> roleIds){}
    public record PasswordRequest(String password){}
    public record RoleRequest(String roleCode,String roleName,String dataScope,String description,String status){}
    public record IdsRequest(List<Long> ids){}
    public record DictTypeRequest(String dictCode,String dictName,String description,Boolean editable,String status){}
    public record DictItemRequest(String itemCode,String itemName,String itemValue,Integer sortOrder,String colorTag,Boolean isDefault,String status,String remark){}
    public record ParameterRequest(String paramGroup,String paramCode,String paramName,String paramValue,String valueType,Boolean sensitive,String description,String status){}
}
