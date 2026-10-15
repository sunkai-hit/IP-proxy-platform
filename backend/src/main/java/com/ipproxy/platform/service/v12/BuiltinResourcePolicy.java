package com.ipproxy.platform.service.v12;

import com.ipproxy.platform.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * V1.2 产品资源策略由代码内置规则实现。
 * 当前仅用于兼容 V1.2 管理面候选资源选择；不代表产品组已完成真实代理转换。
 * SHORT_IP 使用旧共享线路池；LONG_IP 优先复用已有长效共享出口，
 * 如无可复用长效出口，真正的短效预占/迁移/转换必须等待产品组原子接口签收，不能本地假转换。
 * EXCLUSIVE_IP / VPN / TUNNEL 不以资源池类型承载。
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
          -- 先考虑已有被长效服务占用的池，减少不必要的新出口开通。
          ORDER BY CASE WHEN EXISTS (
            SELECT 1 FROM res_pool_line candidate_members
            JOIN res_line candidate_line ON candidate_line.id=candidate_members.line_id
            JOIN svc_egress_claim shared_claim ON shared_claim.source_line_id=candidate_line.id
            WHERE candidate_members.pool_id=p.id AND candidate_members.enabled=TRUE
              AND candidate_line.deleted=FALSE AND candidate_line.online_status='ONLINE'
              AND candidate_line.line_type='LONG'
              AND shared_claim.egress_ipv4=candidate_line.current_public_ip
              AND shared_claim.claim_mode='SHARED'
              AND shared_claim.claim_status IN ('RESERVED','ACTIVE','GRACE_HELD')
          ) THEN 0 ELSE 1 END, online_matching_lines DESC,p.priority,p.id LIMIT 1
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
            -- 产品线不可再提供已有独享固定出口；历史独享未完成核对时同样阻断。
            AND NOT EXISTS (
              SELECT 1 FROM svc_egress_claim ex
              WHERE ex.egress_ipv4=l.current_public_ip AND ex.claim_mode='EXCLUSIVE'
                AND ex.claim_status IN ('RESERVED','ACTIVE','GRACE_HELD','RELEASE_PENDING')
            )
            AND NOT EXISTS (
              SELECT 1 FROM res_exclusive_allocation legacy
              WHERE legacy.deleted=FALSE
                AND legacy.status IN ('LOCKED','ALLOCATED','PENDING_RECYCLE')
                AND (legacy.current_ip_snapshot=l.current_public_ip
                  OR (legacy.current_ip_snapshot IS NULL AND legacy.line_id=l.id))
            )
          ORDER BY CASE WHEN EXISTS (
            SELECT 1 FROM svc_egress_claim shared
            WHERE shared.source_line_id=l.id AND shared.egress_ipv4=l.current_public_ip
              AND shared.claim_mode='SHARED'
              AND shared.claim_status IN ('RESERVED','ACTIVE','GRACE_HELD')
          ) THEN 0 ELSE 1 END,
          l.latency_ms NULLS LAST,l.active_users ASC,l.last_sync_at DESC,l.id LIMIT 1
          """,poolId,lineType);
        return rows.isEmpty()?null:rows.getFirst();
    }

    public record Decision(String policyCode,Long poolId,String poolCode,Long lineId,String lineCode,String productType){}
}
