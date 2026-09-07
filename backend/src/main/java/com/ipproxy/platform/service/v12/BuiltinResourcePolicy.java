package com.ipproxy.platform.service.v12;

import com.ipproxy.platform.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * V1.2 产品资源策略不再由运营端配置，而由代码内置规则实现。
 * 该类是唯一的业务资源决策入口，后续开发可在这里按产品类型扩展规则。
 */
@Component
public class BuiltinResourcePolicy {
    private final JdbcTemplate jdbc;
    public BuiltinResourcePolicy(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public Decision decide(String productType,Long productId,Long packageId,Long customerId){
        String type=productType==null?"":productType;
        String policy="BUILTIN_"+type;
        Map<String,Object> pool=findPool(type);
        if(pool==null)throw new BusinessException("SERVICE_RESOURCE_UNAVAILABLE","当前没有符合内置规则的可用资源池");
        Long poolId=((Number)pool.get("id")).longValue();
        Map<String,Object> line=switch(type){
            case "LONG_IP" -> findLine(poolId,false,false);
            case "EXCLUSIVE_IP" -> findLine(poolId,true,false);
            case "VPN" -> findLine(poolId,false,true);
            default -> null;
        };
        if(Set.of("LONG_IP","EXCLUSIVE_IP","VPN").contains(type)&&line==null)throw new BusinessException("SERVICE_LINE_UNAVAILABLE","当前资源池没有符合产品内置规则的可用家宽/线路");
        return new Decision(policy,poolId,String.valueOf(pool.get("pool_code")),line==null?null:((Number)line.get("id")).longValue(),line==null?null:String.valueOf(line.get("resource_code")),type);
    }

    private Map<String,Object> findPool(String type){
        String desired=switch(type){case "SHORT_IP"->"SHARED";case "LONG_IP"->"LONG";case "EXCLUSIVE_IP"->"EXCLUSIVE";case "VPN"->"VPN";case "TUNNEL"->"TUNNEL";default->"MIXED";};
        List<Map<String,Object>> rows=jdbc.queryForList("""
          SELECT p.id,p.pool_code,p.pool_name,p.pool_type,
            count(pl.line_id) FILTER(WHERE pl.enabled=TRUE AND l.online_status='ONLINE') online_lines
          FROM res_pool p LEFT JOIN res_pool_line pl ON pl.pool_id=p.id LEFT JOIN res_line l ON l.id=pl.line_id
          WHERE p.deleted=FALSE AND p.status='ACTIVE' AND (p.pool_type=? OR p.pool_type='MIXED')
          GROUP BY p.id HAVING count(pl.line_id) FILTER(WHERE pl.enabled=TRUE AND l.online_status='ONLINE')>0
          ORDER BY CASE WHEN p.pool_type=? THEN 0 ELSE 1 END,online_lines DESC,p.priority,p.id LIMIT 1
          """,desired,desired);
        return rows.isEmpty()?null:rows.getFirst();
    }

    private Map<String,Object> findLine(Long poolId,boolean exclusive,boolean requireVpn){
        String sql="""
          SELECT l.id,l.resource_code,host(l.current_public_ip) current_public_ip,l.http_port,l.socks5_port,l.domain_name,l.vpn_supported,l.user_limit,l.active_users
          FROM res_pool_line pl JOIN res_line l ON l.id=pl.line_id
          WHERE pl.pool_id=? AND pl.enabled=TRUE AND l.deleted=FALSE AND l.online_status='ONLINE'
            AND l.current_public_ip IS NOT NULL
            AND (?=FALSE OR l.vpn_supported=TRUE)
            AND (l.user_limit IS NULL OR l.active_users<l.user_limit)
            AND (?=FALSE OR NOT EXISTS(SELECT 1 FROM res_exclusive_allocation a WHERE a.line_id=l.id AND a.deleted=FALSE AND a.status IN ('LOCKED','ALLOCATED')))
          ORDER BY l.latency_ms NULLS LAST,l.active_users ASC,l.last_sync_at DESC,l.id LIMIT 1
          """;
        List<Map<String,Object>> rows=jdbc.queryForList(sql,poolId,requireVpn,exclusive);
        return rows.isEmpty()?null:rows.getFirst();
    }

    public record Decision(String policyCode,Long poolId,String poolCode,Long lineId,String lineCode,String productType){}
}
