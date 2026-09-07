package com.ipproxy.platform.service.v12;

import com.ipproxy.platform.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * V1.2 产品资源策略由代码内置规则实现。
 * 资源池仅组织共享/长效家宽线路：SHORT_IP 使用共享线路池，LONG_IP 使用长效线路池。
 * EXCLUSIVE_IP / VPN / TUNNEL 由各自产品与业务模块维护，不以“资源池类型”承载。
 */
@Component
public class BuiltinResourcePolicy {
    private final JdbcTemplate jdbc;
    public BuiltinResourcePolicy(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public Decision decide(String productType,Long productId,Long packageId,Long customerId){
        String type=productType==null?"":productType;
        if(!Set.of("SHORT_IP","LONG_IP").contains(type)){
            return new Decision("BUILTIN_"+type+"_PRODUCT_RESOURCE",null,null,null,null,type);
        }
        String lineType="SHORT_IP".equals(type)?"SHARED":"LONG";
        Map<String,Object> pool=findPool(lineType);
        if(pool==null)throw new BusinessException("SERVICE_RESOURCE_UNAVAILABLE","当前没有包含可用"+("SHARED".equals(lineType)?"共享":"长效")+"线路的资源池");
        Long poolId=((Number)pool.get("id")).longValue();
        Map<String,Object> line="LONG_IP".equals(type)?findLine(poolId,"LONG"):null;
        if("LONG_IP".equals(type)&&line==null)throw new BusinessException("SERVICE_LINE_UNAVAILABLE","当前资源池没有可用长效家宽线路");
        return new Decision("BUILTIN_"+type,poolId,String.valueOf(pool.get("pool_code")),line==null?null:((Number)line.get("id")).longValue(),line==null?null:String.valueOf(line.get("resource_code")),type);
    }

    private Map<String,Object> findPool(String lineType){
        List<Map<String,Object>> rows=jdbc.queryForList("""
          SELECT p.id,p.pool_code,p.pool_name,
            count(pl.line_id) FILTER(WHERE pl.enabled=TRUE AND l.online_status='ONLINE' AND l.line_type=?) online_matching_lines
          FROM res_pool p LEFT JOIN res_pool_line pl ON pl.pool_id=p.id LEFT JOIN res_line l ON l.id=pl.line_id AND l.deleted=FALSE
          WHERE p.deleted=FALSE AND p.status='ACTIVE'
          GROUP BY p.id
          HAVING count(pl.line_id) FILTER(WHERE pl.enabled=TRUE AND l.online_status='ONLINE' AND l.line_type=?)>0
          ORDER BY online_matching_lines DESC,p.priority,p.id LIMIT 1
          """,lineType,lineType);
        return rows.isEmpty()?null:rows.getFirst();
    }

    private Map<String,Object> findLine(Long poolId,String lineType){
        List<Map<String,Object>> rows=jdbc.queryForList("""
          SELECT l.id,l.resource_code,host(l.current_public_ip) current_public_ip,l.http_port,l.socks5_port,l.domain_name,l.user_limit,l.active_users
          FROM res_pool_line pl JOIN res_line l ON l.id=pl.line_id
          WHERE pl.pool_id=? AND pl.enabled=TRUE AND l.deleted=FALSE AND l.online_status='ONLINE'
            AND l.line_type=? AND l.current_public_ip IS NOT NULL
            AND (l.user_limit IS NULL OR l.active_users<l.user_limit)
          ORDER BY l.latency_ms NULLS LAST,l.active_users ASC,l.last_sync_at DESC,l.id LIMIT 1
          """,poolId,lineType);
        return rows.isEmpty()?null:rows.getFirst();
    }

    public record Decision(String policyCode,Long poolId,String poolCode,Long lineId,String lineCode,String productType){}
}
