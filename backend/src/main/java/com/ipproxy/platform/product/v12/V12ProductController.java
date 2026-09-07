package com.ipproxy.platform.product.v12;

import com.ipproxy.platform.common.api.ApiResponse;
import com.ipproxy.platform.common.audit.OperationAuditRepository;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** V1.2 产品启停入口：资源调度规则由 BuiltinResourcePolicy 在代码层实现，不再依赖运营配置资源策略。 */
@RestController
@RequestMapping("/api/admin/v1/v12/products")
public class V12ProductController {
    private final JdbcTemplate jdbc; private final OperationAuditRepository audit;
    public V12ProductController(JdbcTemplate jdbc,OperationAuditRepository audit){this.jdbc=jdbc;this.audit=audit;}
    public record ReasonRequest(String reason){}
    private void require(UserPrincipal p,String permission){if(p==null||(!p.roles().contains("SUPER_ADMIN")&&!p.permissions().contains(permission)))throw new BusinessException("AUTH_FORBIDDEN","当前账号无此操作权限");}
    private String ip(HttpServletRequest r){return r.getRemoteAddr()==null?"":r.getRemoteAddr();}
    private String reason(String v){if(v==null||v.isBlank())throw new BusinessException("PRODUCT_REASON_REQUIRED","操作原因不能为空");return v.trim();}

    @PostMapping("/{id}/enable")
    @Transactional
    public ApiResponse<?> enable(@PathVariable Long id,@RequestBody ReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){
        require(p,"product:status");Map<String,Object> product=product(id);String type=String.valueOf(product.get("product_type"));
        if(!configExists(id,type))throw new BusinessException("PRODUCT_CONFIG_REQUIRED","产品专项配置未完成");
        String policy="BUILTIN_"+type;
        jdbc.update("UPDATE prd_product SET status='ACTIVE',builtin_policy_code=?,updated_at=now(),updated_by=?,version=version+1 WHERE id=? AND deleted=FALSE",policy,p.userId(),id);
        audit.success(p,"PRODUCT","PRODUCT",id,"ENABLE_V12",reason(b.reason()),ip(r));
        return ApiResponse.success(Map.of("id",id,"status","ACTIVE","builtinPolicyCode",policy));
    }

    @PostMapping("/{id}/disable")
    @Transactional
    public ApiResponse<?> disable(@PathVariable Long id,@RequestBody ReasonRequest b,@AuthenticationPrincipal UserPrincipal p,HttpServletRequest r){
        require(p,"product:status");product(id);
        jdbc.update("UPDATE prd_product SET status='DISABLED',updated_at=now(),updated_by=?,version=version+1 WHERE id=? AND deleted=FALSE",p.userId(),id);
        audit.success(p,"PRODUCT","PRODUCT",id,"DISABLE_V12",reason(b.reason()),ip(r));
        return ApiResponse.success(Map.of("id",id,"status","DISABLED"));
    }

    private Map<String,Object> product(Long id){List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,product_type,status FROM prd_product WHERE id=? AND deleted=FALSE",id);if(rows.isEmpty())throw new BusinessException("PRODUCT_NOT_FOUND","产品不存在");return rows.getFirst();}
    private boolean configExists(Long id,String type){String table=switch(type){case "SHORT_IP"->"prd_short_ip_config";case "LONG_IP"->"prd_long_ip_config";case "EXCLUSIVE_IP"->"prd_exclusive_ip_config";case "VPN"->"prd_vpn_config";case "TUNNEL"->"prd_tunnel_config";default->throw new BusinessException("PRODUCT_TYPE_INVALID","未知产品类型");};Integer n=jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE product_id=?",Integer.class,id);return n!=null&&n>0;}
}
