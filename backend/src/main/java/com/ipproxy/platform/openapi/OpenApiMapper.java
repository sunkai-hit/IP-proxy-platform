package com.ipproxy.platform.openapi;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class OpenApiMapper {
    private final JdbcTemplate jdbc;
    public OpenApiMapper(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private Map<String,Object> one(String sql,Object... args){List<Map<String,Object>> r=jdbc.queryForList(sql,args);return r.isEmpty()?null:r.getFirst();}

    public Map<String,Object> account(String username){
        return one("SELECT a.id,a.customer_id,a.username,a.password_hash,a.status account_status,c.customer_code,c.customer_name,c.status customer_status FROM customer_account a JOIN customer c ON c.id=a.customer_id WHERE a.username=? AND a.deleted=FALSE AND c.deleted=FALSE",username);
    }

    public List<Map<String,Object>> eligibleServices(Long customerId){
        return jdbc.queryForList("SELECT s.id service_id,s.service_no,s.customer_id,s.product_id,s.package_id,s.product_type,s.status service_status,s.effective_at,s.expire_at,s.package_snapshot::text package_snapshot_json,p.product_code,p.product_name,p.access_mode_code,sc.single_extract_limit,sc.extract_rate_limit,sc.extract_rate_window_seconds,sc.dedup_period_seconds,sc.ip_valid_seconds,sc.whitelist_required FROM svc_instance s JOIN prd_product p ON p.id=s.product_id LEFT JOIN prd_short_ip_config sc ON sc.product_id=s.product_id WHERE s.customer_id=? AND s.deleted=FALSE AND s.status='ACTIVE' AND (s.effective_at IS NULL OR s.effective_at<=now()) AND (s.expire_at IS NULL OR s.expire_at>now()) AND p.deleted=FALSE AND p.access_mode_code='API_EXTRACT' ORDER BY s.created_at,s.id",customerId);
    }

    public Map<String,Object> grant(Long accountId,Long serviceId){return one("SELECT id,account_id,customer_id,service_id,token_version,status,policy_snapshot::text policy_snapshot_json,issued_at,rotated_at,last_used_at FROM open_access_grant WHERE account_id=? AND service_id=?",accountId,serviceId);}
    public Map<String,Object> grant(Long id){return one("SELECT id,account_id,customer_id,service_id,token_version,status,policy_snapshot::text policy_snapshot_json,issued_at,rotated_at,last_used_at FROM open_access_grant WHERE id=?",id);}

    public Long insertGrant(Long accountId,Long customerId,Long serviceId,String policyJson){
        return jdbc.queryForObject("INSERT INTO open_access_grant(account_id,customer_id,service_id,token_version,status,policy_snapshot) VALUES(?,?,?,1,'ACTIVE',CAST(? AS jsonb)) RETURNING id",Long.class,accountId,customerId,serviceId,policyJson);
    }
    public long rotateGrant(Long id){return jdbc.queryForObject("UPDATE open_access_grant SET token_version=token_version+1,status='ACTIVE',rotated_at=now(),updated_at=now(),version=version+1 WHERE id=? RETURNING token_version",Long.class,id);}
    public void touchGrant(Long id){jdbc.update("UPDATE open_access_grant SET last_used_at=now(),updated_at=now() WHERE id=?",id);}

    public Map<String,Object> accessContext(Long grantId){
        return one("SELECT g.id grant_id,g.account_id,g.customer_id,g.service_id,g.token_version,g.status grant_status,g.policy_snapshot::text policy_snapshot_json,a.status account_status,c.status customer_status,s.service_no,s.status service_status,s.effective_at,s.expire_at,s.product_id,s.package_id,s.product_type,p.product_code,p.product_name,p.access_mode_code FROM open_access_grant g JOIN customer_account a ON a.id=g.account_id AND a.deleted=FALSE JOIN customer c ON c.id=g.customer_id AND c.deleted=FALSE JOIN svc_instance s ON s.id=g.service_id AND s.deleted=FALSE JOIN prd_product p ON p.id=s.product_id AND p.deleted=FALSE WHERE g.id=?",grantId);
    }

    public Map<String,Object> primaryPool(Long serviceId){
        return one("SELECT b.resource_id pool_id,p.pool_code,p.pool_name FROM svc_resource_binding b JOIN res_pool p ON p.id=b.resource_id WHERE b.service_id=? AND b.resource_type='POOL' AND b.status='ACTIVE' AND p.deleted=FALSE AND p.status='ACTIVE' ORDER BY CASE WHEN b.binding_role='PRIMARY' THEN 0 ELSE 1 END,b.id LIMIT 1",serviceId);
    }

    public List<Map<String,Object>> selectIps(Long poolId,Long serviceId,int amount,String region,String carrier,int dedupSeconds){
        return jdbc.queryForList("SELECT i.id,host(i.ip_address) ip,i.region_code,i.carrier_code,i.latency_ms,i.quality_score,i.source_type FROM res_pool_ip pi JOIN res_ip i ON i.id=pi.ip_id WHERE pi.pool_id=? AND pi.enabled=TRUE AND i.deleted=FALSE AND i.available_status='AVAILABLE' AND i.duplicate_flag=FALSE AND (?='' OR i.region_code=?) AND (?='' OR i.carrier_code=?) AND (?<=0 OR NOT EXISTS (SELECT 1 FROM log_ip_extract l CROSS JOIN LATERAL jsonb_array_elements(l.returned_ips) e WHERE l.service_id=? AND l.occurred_at>=now()-make_interval(secs=>?) AND e->>'ip'=host(i.ip_address))) ORDER BY i.quality_score DESC NULLS LAST,i.latency_ms ASC NULLS LAST,i.id LIMIT ?",poolId,region,region,carrier,carrier,dedupSeconds,serviceId,dedupSeconds,amount);
    }

    public long extractedCount(Long serviceId){return jdbc.queryForObject("SELECT COALESCE(sum(returned_count),0) FROM log_ip_extract WHERE service_id=? AND result IN ('SUCCESS','PARTIAL')",Long.class,serviceId);}
    public long recentExtractRequests(Long serviceId,int windowSeconds){return jdbc.queryForObject("SELECT count(*) FROM log_ip_extract WHERE service_id=? AND occurred_at>=now()-make_interval(secs=>?)",Long.class,serviceId,windowSeconds);}

    public void insertExtractLog(String requestId,Long customerId,Long accountId,Long serviceId,Long productId,String clientIp,String protocol,String region,String carrier,int requested,int returned,String returnedJson,String resourceJson,String result,String errorCode,String errorMessage,long elapsedMs){
        jdbc.update("INSERT INTO log_ip_extract(request_id,customer_id,account_id,service_id,product_id,client_ip,requested_protocol_code,region_code,carrier_code,requested_count,returned_count,returned_ips,resource_summary,result,error_code,error_message,elapsed_ms) VALUES(?,?,?,?,?,NULLIF(?,'')::inet,?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,?,?,?)",requestId,customerId,accountId,serviceId,productId,clientIp,protocol,region,carrier,requested,returned,returnedJson,resourceJson,result,errorCode,errorMessage,(int)Math.min(Integer.MAX_VALUE,elapsedMs));
    }

    public void insertApiLog(String requestId,Long customerId,Long accountId,Long serviceId,String method,String path,String queryJson,String clientIp,int status,String result,String errorCode,String errorMessage,long elapsedMs){
        jdbc.update("INSERT INTO log_api(request_id,api_scope,customer_id,account_id,service_id,method,path,query_summary,client_ip,response_status,result,error_code,error_message,elapsed_ms) VALUES(?,'OPEN',?,?,?,?,?,CAST(NULLIF(?,'') AS jsonb),NULLIF(?,'')::inet,?,?,?,?,?)",requestId,customerId,accountId,serviceId,method,path,queryJson,clientIp,status,result,errorCode,errorMessage,(int)Math.min(Integer.MAX_VALUE,elapsedMs));
    }

    public List<Map<String,Object>> whitelist(Long serviceId){return jdbc.queryForList("SELECT id,host(ip_address) ip,created_at,remark FROM svc_whitelist WHERE service_id=? AND deleted=FALSE AND status='ACTIVE' ORDER BY created_at,id",serviceId);}
    public long whitelistCount(Long serviceId){return jdbc.queryForObject("SELECT count(*) FROM svc_whitelist WHERE service_id=? AND deleted=FALSE AND status='ACTIVE'",Long.class,serviceId);}
    public Long addWhitelist(Long serviceId,String ip){return jdbc.queryForObject("INSERT INTO svc_whitelist(service_id,ip_address,status,remark) VALUES(?,CAST(? AS inet),'ACTIVE','Open API') ON CONFLICT(service_id,ip_address) WHERE deleted=FALSE DO UPDATE SET status='ACTIVE',updated_at=now() RETURNING id",Long.class,serviceId,ip);}
    public int deleteWhitelist(Long serviceId,String ip){return jdbc.update("UPDATE svc_whitelist SET status='DISABLED',updated_at=now(),version=version+1 WHERE service_id=? AND ip_address=CAST(? AS inet) AND deleted=FALSE AND status='ACTIVE'",serviceId,ip);}

    public long countExtractLogs(String keyword,Long serviceId){return jdbc.queryForObject("SELECT count(*) FROM log_ip_extract l LEFT JOIN customer c ON c.id=l.customer_id LEFT JOIN svc_instance s ON s.id=l.service_id WHERE (?='' OR l.request_id ILIKE '%'||?||'%' OR c.customer_name ILIKE '%'||?||'%' OR s.service_no ILIKE '%'||?||'%') AND (?=0 OR l.service_id=?)",Long.class,keyword,keyword,keyword,keyword,serviceId==null?0:serviceId,serviceId==null?0:serviceId);}
    public List<Map<String,Object>> extractLogs(String keyword,Long serviceId,int size,int offset){return jdbc.queryForList("SELECT l.id,l.request_id,l.customer_id,c.customer_name,l.account_id,l.service_id,s.service_no,l.product_id,p.product_name,host(l.client_ip) client_ip,l.requested_protocol_code,l.region_code,l.carrier_code,l.requested_count,l.returned_count,l.returned_ips::text returned_ips_json,l.resource_summary::text resource_summary_json,l.result,l.error_code,l.error_message,l.elapsed_ms,l.occurred_at FROM log_ip_extract l LEFT JOIN customer c ON c.id=l.customer_id LEFT JOIN svc_instance s ON s.id=l.service_id LEFT JOIN prd_product p ON p.id=l.product_id WHERE (?='' OR l.request_id ILIKE '%'||?||'%' OR c.customer_name ILIKE '%'||?||'%' OR s.service_no ILIKE '%'||?||'%') AND (?=0 OR l.service_id=?) ORDER BY l.occurred_at DESC,l.id DESC LIMIT ? OFFSET ?",keyword,keyword,keyword,keyword,serviceId==null?0:serviceId,serviceId==null?0:serviceId,size,offset);}

    public long countApiLogs(String keyword,Long serviceId){return jdbc.queryForObject("SELECT count(*) FROM log_api l LEFT JOIN customer c ON c.id=l.customer_id LEFT JOIN svc_instance s ON s.id=l.service_id WHERE l.api_scope='OPEN' AND (?='' OR l.request_id ILIKE '%'||?||'%' OR l.path ILIKE '%'||?||'%' OR c.customer_name ILIKE '%'||?||'%' OR s.service_no ILIKE '%'||?||'%') AND (?=0 OR l.service_id=?)",Long.class,keyword,keyword,keyword,keyword,keyword,serviceId==null?0:serviceId,serviceId==null?0:serviceId);}
    public List<Map<String,Object>> apiLogs(String keyword,Long serviceId,int size,int offset){return jdbc.queryForList("SELECT l.id,l.request_id,l.customer_id,c.customer_name,l.account_id,l.service_id,s.service_no,l.method,l.path,l.query_summary::text query_summary_json,host(l.client_ip) client_ip,l.response_status,l.result,l.error_code,l.error_message,l.elapsed_ms,l.occurred_at FROM log_api l LEFT JOIN customer c ON c.id=l.customer_id LEFT JOIN svc_instance s ON s.id=l.service_id WHERE l.api_scope='OPEN' AND (?='' OR l.request_id ILIKE '%'||?||'%' OR l.path ILIKE '%'||?||'%' OR c.customer_name ILIKE '%'||?||'%' OR s.service_no ILIKE '%'||?||'%') AND (?=0 OR l.service_id=?) ORDER BY l.occurred_at DESC,l.id DESC LIMIT ? OFFSET ?",keyword,keyword,keyword,keyword,keyword,serviceId==null?0:serviceId,serviceId==null?0:serviceId,size,offset);}

    public long countUsageLogs(String keyword,Long serviceId){return jdbc.queryForObject("SELECT count(*) FROM log_usage l LEFT JOIN customer c ON c.id=l.customer_id LEFT JOIN svc_instance s ON s.id=l.service_id WHERE (?='' OR l.request_id ILIKE '%'||?||'%' OR c.customer_name ILIKE '%'||?||'%' OR s.service_no ILIKE '%'||?||'%' OR host(l.outbound_ip) ILIKE '%'||?||'%') AND (?=0 OR l.service_id=?)",Long.class,keyword,keyword,keyword,keyword,keyword,serviceId==null?0:serviceId,serviceId==null?0:serviceId);}
    public List<Map<String,Object>> usageLogs(String keyword,Long serviceId,int size,int offset){return jdbc.queryForList("SELECT l.id,l.request_id,l.customer_id,c.customer_name,l.service_id,s.service_no,l.product_id,p.product_name,host(l.client_ip) client_ip,host(l.outbound_ip) outbound_ip,l.protocol_code,l.target_host,l.result,l.status_code,l.upload_bytes,l.download_bytes,l.elapsed_ms,l.error_message,l.occurred_at FROM log_usage l LEFT JOIN customer c ON c.id=l.customer_id LEFT JOIN svc_instance s ON s.id=l.service_id LEFT JOIN prd_product p ON p.id=l.product_id WHERE (?='' OR l.request_id ILIKE '%'||?||'%' OR c.customer_name ILIKE '%'||?||'%' OR s.service_no ILIKE '%'||?||'%' OR host(l.outbound_ip) ILIKE '%'||?||'%') AND (?=0 OR l.service_id=?) ORDER BY l.occurred_at DESC,l.id DESC LIMIT ? OFFSET ?",keyword,keyword,keyword,keyword,keyword,serviceId==null?0:serviceId,serviceId==null?0:serviceId,size,offset);}
}
