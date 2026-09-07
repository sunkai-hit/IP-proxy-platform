package com.ipproxy.platform.product;

import com.ipproxy.platform.common.audit.OperationAuditRepository;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** V1.2 产品启用规则：只要求产品专项配置完整；资源调度由代码内置策略承担。 */
@Service
public class V12ProductLifecycleService {
    private final JdbcTemplate jdbc; private final OperationAuditRepository audit;
    public V12ProductLifecycleService(JdbcTemplate jdbc,OperationAuditRepository audit){this.jdbc=jdbc;this.audit=audit;}

    @Transactional public void enable(Long id,String reason,UserPrincipal actor,String ip){Map<String,Object> p=product(id);String type=String.valueOf(p.get("product_type"));if(!configExists(id,type))throw new BusinessException("PRODUCT_CONFIG_REQUIRED","产品专项配置未完成");int n=jdbc.update("UPDATE prd_product SET status='ACTIVE',builtin_policy_code=COALESCE(builtin_policy_code,?),updated_at=now(),updated_by=?,version=version+1 WHERE id=? AND deleted=FALSE",policyCode(type),actor.userId(),id);if(n==0)throw new BusinessException("PRODUCT_NOT_FOUND","产品不存在");audit.success(actor,"PRODUCT","PRODUCT",id,"ENABLE_V12",required(reason),ip);}

    @Transactional public void disable(Long id,String reason,UserPrincipal actor,String ip){product(id);jdbc.update("UPDATE prd_product SET status='DISABLED',updated_at=now(),updated_by=?,version=version+1 WHERE id=? AND deleted=FALSE",actor.userId(),id);audit.success(actor,"PRODUCT","PRODUCT",id,"DISABLE_V12",required(reason),ip);}

    public Map<String,Object> policy(Long id){Map<String,Object> p=product(id);String type=String.valueOf(p.get("product_type"));return Map.of("productId",id,"productType",type,"policyCode",String.valueOf(p.get("builtin_policy_code")),"businessConfigurable",false,"description",description(type));}

    private Map<String,Object> product(Long id){List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,product_code,product_name,product_type,status,builtin_policy_code FROM prd_product WHERE id=? AND deleted=FALSE",id);if(rows.isEmpty())throw new BusinessException("PRODUCT_NOT_FOUND","产品不存在");return rows.getFirst();}
    private boolean configExists(Long id,String type){String table=switch(type){case "SHORT_IP"->"prd_short_ip_config";case "LONG_IP"->"prd_long_ip_config";case "EXCLUSIVE_IP"->"prd_exclusive_ip_config";case "VPN"->"prd_vpn_config";case "TUNNEL"->"prd_tunnel_config";default->null;};if(table==null)return false;Integer x=jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE product_id=?",Integer.class,id);return x!=null&&x>0;}
    private String policyCode(String type){return "BUILTIN_"+type;}
    private String description(String type){return switch(type){case "SHORT_IP"->"按短效IP内置规则从共享资源池提取可用线路当前IP";case "LONG_IP"->"按长效内置规则选择稳定家宽/线路并保持服务绑定";case "EXCLUSIVE_IP"->"按独享内置规则锁定一条可用家宽/线路";case "VPN"->"选择支持VPN的可用家宽/线路";case "TUNNEL"->"按隧道入口及后端调度代码执行";default->"系统内置资源策略";};}
    private String required(String x){if(x==null||x.isBlank())throw new BusinessException("PRODUCT_REASON_REQUIRED","操作原因不能为空");return x.trim();}
}
