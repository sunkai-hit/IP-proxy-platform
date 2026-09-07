package com.ipproxy.platform.resource.v12;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class V12ResourceMapper {
    private final JdbcTemplate jdbc;
    public V12ResourceMapper(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private Map<String,Object> one(String sql,Object...args){List<Map<String,Object>> rows=jdbc.queryForList(sql,args);return rows.isEmpty()?null:rows.getFirst();}

    public Map<String,Object> overview(){return one("""
        SELECT
          (SELECT count(*) FROM res_centos WHERE deleted=FALSE) centos_total,
          (SELECT count(*) FROM res_centos WHERE deleted=FALSE AND online_status='ONLINE') centos_online,
          (SELECT count(*) FROM res_ros WHERE deleted=FALSE) ros_total,
          (SELECT count(*) FROM res_ros WHERE deleted=FALSE AND online_status='ONLINE') ros_online,
          (SELECT count(*) FROM res_line WHERE deleted=FALSE) line_total,
          (SELECT count(*) FROM res_line WHERE deleted=FALSE AND online_status='ONLINE') line_online,
          (SELECT count(DISTINCT current_public_ip) FROM res_line WHERE deleted=FALSE AND current_public_ip IS NOT NULL AND online_status='ONLINE') current_unique_ip,
          (SELECT count(*) FROM res_pool WHERE deleted=FALSE AND status='ACTIVE') active_pool_total,
          (SELECT count(*) FROM res_exclusive_allocation WHERE deleted=FALSE AND status IN ('LOCKED','ALLOCATED')) exclusive_active_total,
          (SELECT count(*) FROM res_supplier WHERE deleted=FALSE AND status='ACTIVE') supplier_total,
          (SELECT count(*) FROM res_supplier_purchase WHERE deleted=FALSE AND status='ACTIVE') purchase_total,
          (SELECT count(*) FROM res_reverse_proxy WHERE deleted=FALSE AND status='ACTIVE') reverse_proxy_total
        """);}

    public long countCentos(String k,String status){return jdbc.queryForObject("""
        SELECT count(*) FROM res_centos c WHERE c.deleted=FALSE
        AND (?='' OR c.resource_code ILIKE '%'||?||'%' OR COALESCE(c.resource_name,'') ILIKE '%'||?||'%' OR COALESCE(c.host_account,'') ILIKE '%'||?||'%' OR host(c.management_ip) ILIKE '%'||?||'%')
        AND (?='' OR c.online_status=?)
        """,Long.class,k,k,k,k,k,status,status);}
    public List<Map<String,Object>> listCentos(String k,String status,int size,int offset){return jdbc.queryForList("""
        SELECT c.id,c.resource_code,c.resource_name,c.host_type,c.host_account,host(c.management_ip) management_ip,
          c.location_name,c.region_code,c.carrier_code,c.online_status,c.cpu_usage,c.memory_usage,c.system_load,c.disk_usage,
          c.vpn_supported,c.pppoe_server_name,c.remark,c.last_communicated_at,c.last_sync_at,
          (SELECT count(*) FROM res_ros r WHERE r.centos_id=c.id AND r.deleted=FALSE) ros_count,
          (SELECT count(*) FROM res_line l JOIN res_ros r ON r.id=l.ros_id WHERE r.centos_id=c.id AND l.deleted=FALSE) line_count,
          (SELECT count(*) FROM res_pool_line pl JOIN res_line l ON l.id=pl.line_id JOIN res_ros r ON r.id=l.ros_id WHERE r.centos_id=c.id AND pl.enabled=TRUE) pool_member_count
        FROM res_centos c WHERE c.deleted=FALSE
        AND (?='' OR c.resource_code ILIKE '%'||?||'%' OR COALESCE(c.resource_name,'') ILIKE '%'||?||'%' OR COALESCE(c.host_account,'') ILIKE '%'||?||'%' OR host(c.management_ip) ILIKE '%'||?||'%')
        AND (?='' OR c.online_status=?) ORDER BY c.updated_at DESC,c.id DESC LIMIT ? OFFSET ?
        """,k,k,k,k,k,status,status,size,offset);}
    public Map<String,Object> centos(Long id){return one("""
        SELECT c.*,host(c.management_ip) management_ip_text,
          (SELECT count(*) FROM res_ros r WHERE r.centos_id=c.id AND r.deleted=FALSE) ros_count,
          (SELECT count(*) FROM res_line l JOIN res_ros r ON r.id=l.ros_id WHERE r.centos_id=c.id AND l.deleted=FALSE) line_count
        FROM res_centos c WHERE c.id=? AND c.deleted=FALSE
        """,id);}
    public List<Map<String,Object>> centosRos(Long id){return jdbc.queryForList("""
        SELECT r.id,r.resource_code,r.resource_name,r.online_status,r.line_total,r.line_online,r.current_ip_count,r.b_prefix_count,r.c_prefix_count,r.bas_count,r.system_load,r.last_report_at
        FROM res_ros r WHERE r.centos_id=? AND r.deleted=FALSE ORDER BY r.resource_code
        """,id);}

    public long countRos(String k,String status,String autoSwitch){return jdbc.queryForObject("""
        SELECT count(*) FROM res_ros r LEFT JOIN res_centos c ON c.id=r.centos_id WHERE r.deleted=FALSE
        AND (?='' OR r.resource_code ILIKE '%'||?||'%' OR COALESCE(r.resource_name,'') ILIKE '%'||?||'%' OR COALESCE(r.record_name,'') ILIKE '%'||?||'%')
        AND (?='' OR r.online_status=?) AND (?='' OR r.auto_switch=CAST(? AS boolean))
        """,Long.class,k,k,k,k,status,status,autoSwitch,autoSwitch);}
    public List<Map<String,Object>> listRos(String k,String status,String autoSwitch,int size,int offset){return jdbc.queryForList("""
        SELECT r.id,r.resource_code,r.resource_name,r.centos_id,c.resource_code centos_code,c.resource_name centos_name,r.host_type,
          host(r.management_ip) management_ip,r.region_code,r.carrier_code,r.online_status,r.cpu_usage,r.memory_usage,r.system_load,r.disk_usage,
          r.line_total,r.line_online,r.line_abnormal,r.current_ip_count,r.b_prefix_count,r.c_prefix_count,r.bas_count,r.uptime_seconds,r.record_name,
          r.ha_role,r.replacement_ros_id,rr.resource_code replacement_ros_code,r.switch_status,r.auto_switch,r.last_report_at,r.last_sync_at
        FROM res_ros r LEFT JOIN res_centos c ON c.id=r.centos_id LEFT JOIN res_ros rr ON rr.id=r.replacement_ros_id
        WHERE r.deleted=FALSE
        AND (?='' OR r.resource_code ILIKE '%'||?||'%' OR COALESCE(r.resource_name,'') ILIKE '%'||?||'%' OR COALESCE(r.record_name,'') ILIKE '%'||?||'%')
        AND (?='' OR r.online_status=?) AND (?='' OR r.auto_switch=CAST(? AS boolean))
        ORDER BY r.updated_at DESC,r.id DESC LIMIT ? OFFSET ?
        """,k,k,k,k,status,status,autoSwitch,autoSwitch,size,offset);}
    public Map<String,Object> ros(Long id){return one("""
        SELECT r.*,host(r.management_ip) management_ip_text,c.resource_code centos_code,c.resource_name centos_name,rr.resource_code replacement_ros_code,
          (SELECT count(DISTINCT bas_name) FROM res_line l WHERE l.ros_id=r.id AND l.deleted=FALSE AND bas_name IS NOT NULL AND bas_name<>'') bas_actual_count
        FROM res_ros r LEFT JOIN res_centos c ON c.id=r.centos_id LEFT JOIN res_ros rr ON rr.id=r.replacement_ros_id
        WHERE r.id=? AND r.deleted=FALSE
        """,id);}
    public List<Map<String,Object>> rosBas(Long id){return jdbc.queryForList("""
        SELECT COALESCE(NULLIF(bas_name,''),'UNKNOWN') bas_name,MAX(bas_address) bas_address,count(*) line_total,
          count(*) FILTER(WHERE online_status='ONLINE') online_lines,
          count(DISTINCT current_public_ip) FILTER(WHERE current_public_ip IS NOT NULL) current_ip_count
        FROM res_line WHERE ros_id=? AND deleted=FALSE GROUP BY COALESCE(NULLIF(bas_name,''),'UNKNOWN') ORDER BY line_total DESC
        """,id);}
    public int updateRosAutoSwitch(Long id,boolean enabled,Long actor){return jdbc.update("UPDATE res_ros SET auto_switch=?,updated_at=now(),updated_by=?,version=version+1 WHERE id=? AND deleted=FALSE",enabled,actor,id);}
    public int updateRosHa(Long id,String role,Long replacementId,String switchStatus,Long actor){return jdbc.update("UPDATE res_ros SET ha_role=?,replacement_ros_id=?,switch_status=?,updated_at=now(),updated_by=?,version=version+1 WHERE id=? AND deleted=FALSE",role,replacementId,switchStatus,actor,id);}

    public long countLines(String k,String status,Long rosId,String type,String province,String city,String carrier,Long customerId){return jdbc.queryForObject("""
        SELECT count(*) FROM res_line l WHERE l.deleted=FALSE
        AND (?='' OR l.resource_code ILIKE '%'||?||'%' OR COALESCE(l.line_alias,'') ILIKE '%'||?||'%' OR COALESCE(l.broadband_account,'') ILIKE '%'||?||'%' OR host(l.current_public_ip) ILIKE '%'||?||'%' OR COALESCE(l.domain_name,'') ILIKE '%'||?||'%')
        AND (?='' OR l.online_status=?) AND (CAST(? AS BIGINT) IS NULL OR l.ros_id=CAST(? AS BIGINT)) AND (?='' OR COALESCE(l.line_type,'')=?)
        AND (?='' OR COALESCE(l.province_code,l.region_code,'')=?) AND (?='' OR COALESCE(l.city_code,'')=?) AND (?='' OR COALESCE(l.carrier_code,'')=?)
        AND (CAST(? AS BIGINT) IS NULL OR l.customer_id=CAST(? AS BIGINT))
        """,Long.class,k,k,k,k,k,k,status,status,rosId,rosId,type,type,province,province,city,city,carrier,carrier,customerId,customerId);}
    public List<Map<String,Object>> listLines(String k,String status,Long rosId,String type,String province,String city,String carrier,Long customerId,int size,int offset){return jdbc.queryForList("""
        SELECT l.id,l.resource_code,l.resource_name,l.line_alias,l.ros_id,r.resource_code ros_code,r.resource_name ros_name,c.id centos_id,c.resource_code centos_code,
          l.broadband_account,l.broadband_account_mask,l.account_prefix,host(l.current_public_ip) current_public_ip,l.http_port,l.socks5_port,l.domain_name,
          l.customer_id,cu.customer_name,l.managed_flag,l.line_type,l.user_limit,l.active_users,l.restart_interval_minutes,l.available_time_range,l.vpn_supported,
          l.vpn_redial_monitor,l.bas_name,l.bas_address,l.province_code,l.city_code,l.region_code,l.carrier_code,l.online_status,l.dial_status,l.latency_ms,
          l.last_dial_at,l.last_ip_changed_at,l.last_ros_report_at,l.last_sync_at
        FROM res_line l JOIN res_ros r ON r.id=l.ros_id LEFT JOIN res_centos c ON c.id=r.centos_id LEFT JOIN customer cu ON cu.id=l.customer_id
        WHERE l.deleted=FALSE
        AND (?='' OR l.resource_code ILIKE '%'||?||'%' OR COALESCE(l.line_alias,'') ILIKE '%'||?||'%' OR COALESCE(l.broadband_account,'') ILIKE '%'||?||'%' OR host(l.current_public_ip) ILIKE '%'||?||'%' OR COALESCE(l.domain_name,'') ILIKE '%'||?||'%')
        AND (?='' OR l.online_status=?) AND (CAST(? AS BIGINT) IS NULL OR l.ros_id=CAST(? AS BIGINT)) AND (?='' OR COALESCE(l.line_type,'')=?)
        AND (?='' OR COALESCE(l.province_code,l.region_code,'')=?) AND (?='' OR COALESCE(l.city_code,'')=?) AND (?='' OR COALESCE(l.carrier_code,'')=?)
        AND (CAST(? AS BIGINT) IS NULL OR l.customer_id=CAST(? AS BIGINT)) ORDER BY l.updated_at DESC,l.id DESC LIMIT ? OFFSET ?
        """,k,k,k,k,k,k,status,status,rosId,rosId,type,type,province,province,city,city,carrier,carrier,customerId,customerId,size,offset);}
    public Map<String,Object> line(Long id){return one("""
        SELECT l.*,host(l.current_public_ip) current_public_ip_text,r.resource_code ros_code,r.resource_name ros_name,c.resource_code centos_code,c.resource_name centos_name,
          cu.customer_name,(SELECT count(*) FROM res_pool_line pl WHERE pl.line_id=l.id AND pl.enabled=TRUE) pool_count,
          (SELECT string_agg(p.pool_name,', ' ORDER BY p.pool_name) FROM res_pool_line pl JOIN res_pool p ON p.id=pl.pool_id WHERE pl.line_id=l.id AND pl.enabled=TRUE) pool_names
        FROM res_line l JOIN res_ros r ON r.id=l.ros_id LEFT JOIN res_centos c ON c.id=r.centos_id LEFT JOIN customer cu ON cu.id=l.customer_id
        WHERE l.id=? AND l.deleted=FALSE
        """,id);}
    public List<Map<String,Object>> lineIpHistory(Long id,int limit){return jdbc.queryForList("SELECT id,host(ip_address) ip_address,first_seen_at,last_seen_at,change_type,source_record_id FROM res_line_ip_history WHERE line_id=? ORDER BY last_seen_at DESC LIMIT ?",id,limit);}
    public List<Map<String,Object>> lineOperations(Long id,int limit){return jdbc.queryForList("SELECT id,operation_type,operation_source,result_status,result_message,operator_id,created_at FROM res_line_operation_log WHERE line_id=? ORDER BY created_at DESC LIMIT ?",id,limit);}
    public int updateLineType(Long id,String type,Long actor){return jdbc.update("UPDATE res_line SET line_type=?,updated_at=now(),updated_by=?,version=version+1 WHERE id=? AND deleted=FALSE",type,actor,id);}

    public long countPools(String k,String status,String province,String city,String carrier){return jdbc.queryForObject("""
        SELECT count(*) FROM res_pool p WHERE p.deleted=FALSE
        AND (?='' OR p.pool_code ILIKE '%'||?||'%' OR p.pool_name ILIKE '%'||?||'%')
        AND (?='' OR p.status=?) AND (?='' OR COALESCE(p.province_code,'')=?)
        AND (?='' OR COALESCE(p.city_code,'')=?) AND (?='' OR COALESCE(p.carrier_code,'')=?)
        """,Long.class,k,k,k,status,status,province,province,city,city,carrier,carrier);}
    public List<Map<String,Object>> listPools(String k,String status,String province,String city,String carrier,int size,int offset){return jdbc.queryForList("""
        SELECT p.id,p.pool_code,p.pool_name,p.purpose,p.province_code,p.city_code,p.carrier_code,p.status,
          count(DISTINCT r.id) FILTER(WHERE pl.enabled=TRUE) ros_count,
          count(pl.line_id) FILTER(WHERE pl.enabled=TRUE) line_count,
          count(pl.line_id) FILTER(WHERE pl.enabled=TRUE AND l.line_type='SHARED') shared_line_count,
          count(pl.line_id) FILTER(WHERE pl.enabled=TRUE AND l.line_type='LONG') long_line_count,
          count(pl.line_id) FILTER(WHERE pl.enabled=TRUE AND l.online_status='ONLINE') online_line_count,
          count(pl.line_id) FILTER(WHERE pl.enabled=TRUE AND l.online_status='ABNORMAL') abnormal_line_count,
          count(pl.line_id) FILTER(WHERE pl.enabled=TRUE AND l.current_public_ip IS NOT NULL) current_ip_count,
          count(DISTINCT l.current_public_ip) FILTER(WHERE pl.enabled=TRUE AND l.current_public_ip IS NOT NULL) unique_ip_count,
          count(DISTINCT (split_part(host(l.current_public_ip),'.',1)||'.'||split_part(host(l.current_public_ip),'.',2))) FILTER(WHERE pl.enabled=TRUE AND l.current_public_ip IS NOT NULL AND family(l.current_public_ip)=4) b_prefix_count,
          count(DISTINCT (split_part(host(l.current_public_ip),'.',1)||'.'||split_part(host(l.current_public_ip),'.',2)||'.'||split_part(host(l.current_public_ip),'.',3))) FILTER(WHERE pl.enabled=TRUE AND l.current_public_ip IS NOT NULL AND family(l.current_public_ip)=4) c_prefix_count
        FROM res_pool p
        LEFT JOIN res_pool_line pl ON pl.pool_id=p.id
        LEFT JOIN res_line l ON l.id=pl.line_id AND l.deleted=FALSE
        LEFT JOIN res_ros r ON r.id=l.ros_id AND r.deleted=FALSE
        WHERE p.deleted=FALSE
        AND (?='' OR p.pool_code ILIKE '%'||?||'%' OR p.pool_name ILIKE '%'||?||'%')
        AND (?='' OR p.status=?) AND (?='' OR COALESCE(p.province_code,'')=?)
        AND (?='' OR COALESCE(p.city_code,'')=?) AND (?='' OR COALESCE(p.carrier_code,'')=?)
        GROUP BY p.id ORDER BY p.updated_at DESC,p.id DESC LIMIT ? OFFSET ?
        """,k,k,k,status,status,province,province,city,city,carrier,carrier,size,offset);}
    public Map<String,Object> pool(Long id){return one("""
        SELECT p.id,p.pool_code,p.pool_name,p.purpose,p.province_code,p.city_code,p.carrier_code,p.status,p.created_at,p.updated_at,
          count(DISTINCT r.id) FILTER(WHERE pl.enabled=TRUE) ros_count,
          count(pl.line_id) FILTER(WHERE pl.enabled=TRUE) line_count,
          count(pl.line_id) FILTER(WHERE pl.enabled=TRUE AND l.line_type='SHARED') shared_line_count,
          count(pl.line_id) FILTER(WHERE pl.enabled=TRUE AND l.line_type='LONG') long_line_count,
          count(pl.line_id) FILTER(WHERE pl.enabled=TRUE AND l.online_status='ONLINE') online_line_count,
          count(pl.line_id) FILTER(WHERE pl.enabled=TRUE AND l.online_status='ABNORMAL') abnormal_line_count,
          count(pl.line_id) FILTER(WHERE pl.enabled=TRUE AND l.current_public_ip IS NOT NULL) current_ip_count,
          count(DISTINCT l.current_public_ip) FILTER(WHERE pl.enabled=TRUE AND l.current_public_ip IS NOT NULL) unique_ip_count,
          count(DISTINCT (split_part(host(l.current_public_ip),'.',1)||'.'||split_part(host(l.current_public_ip),'.',2))) FILTER(WHERE pl.enabled=TRUE AND l.current_public_ip IS NOT NULL AND family(l.current_public_ip)=4) b_prefix_count,
          count(DISTINCT (split_part(host(l.current_public_ip),'.',1)||'.'||split_part(host(l.current_public_ip),'.',2)||'.'||split_part(host(l.current_public_ip),'.',3))) FILTER(WHERE pl.enabled=TRUE AND l.current_public_ip IS NOT NULL AND family(l.current_public_ip)=4) c_prefix_count
        FROM res_pool p
        LEFT JOIN res_pool_line pl ON pl.pool_id=p.id
        LEFT JOIN res_line l ON l.id=pl.line_id AND l.deleted=FALSE
        LEFT JOIN res_ros r ON r.id=l.ros_id AND r.deleted=FALSE
        WHERE p.id=? AND p.deleted=FALSE GROUP BY p.id
        """,id);}
    public List<Map<String,Object>> poolLines(Long id,String keyword,String status,String type,int size,int offset){return jdbc.queryForList("""
        SELECT l.id,l.resource_code,l.line_alias,r.resource_code ros_code,host(l.current_public_ip) current_public_ip,
          l.province_code,l.city_code,l.carrier_code,l.online_status,l.line_type,l.latency_ms,l.bas_name,pl.source_reason,pl.joined_at
        FROM res_pool_line pl JOIN res_line l ON l.id=pl.line_id JOIN res_ros r ON r.id=l.ros_id
        WHERE pl.pool_id=? AND pl.enabled=TRUE AND l.deleted=FALSE
          AND (?='' OR l.resource_code ILIKE '%'||?||'%' OR COALESCE(l.line_alias,'') ILIKE '%'||?||'%' OR host(l.current_public_ip) ILIKE '%'||?||'%')
          AND (?='' OR l.online_status=?) AND (?='' OR COALESCE(l.line_type,'')=?)
        ORDER BY l.resource_code LIMIT ? OFFSET ?
        """,id,keyword,keyword,keyword,keyword,status,status,type,type,size,offset);}
    public long countPoolLines(Long id,String keyword,String status,String type){return jdbc.queryForObject("""
        SELECT count(*) FROM res_pool_line pl JOIN res_line l ON l.id=pl.line_id
        WHERE pl.pool_id=? AND pl.enabled=TRUE AND l.deleted=FALSE
          AND (?='' OR l.resource_code ILIKE '%'||?||'%' OR COALESCE(l.line_alias,'') ILIKE '%'||?||'%' OR host(l.current_public_ip) ILIKE '%'||?||'%')
          AND (?='' OR l.online_status=?) AND (?='' OR COALESCE(l.line_type,'')=?)
        """,Long.class,id,keyword,keyword,keyword,keyword,status,status,type,type);}
    public List<Map<String,Object>> candidatePoolLines(Long poolId,String keyword,String type,int size,int offset){return jdbc.queryForList("""
        SELECT l.id,l.resource_code,l.line_alias,r.resource_code ros_code,host(l.current_public_ip) current_public_ip,
          l.province_code,l.city_code,l.carrier_code,l.online_status,l.line_type,l.latency_ms,l.bas_name
        FROM res_line l JOIN res_ros r ON r.id=l.ros_id CROSS JOIN res_pool p
        WHERE p.id=? AND p.deleted=FALSE AND l.deleted=FALSE AND l.line_type IN ('SHARED','LONG')
          AND (COALESCE(p.province_code,'')='' OR COALESCE(l.province_code,l.region_code,'')=p.province_code)
          AND (COALESCE(p.city_code,'')='' OR COALESCE(l.city_code,'')=p.city_code)
          AND (COALESCE(p.carrier_code,'')='' OR COALESCE(l.carrier_code,'')=p.carrier_code)
          AND NOT EXISTS(SELECT 1 FROM res_pool_line pl WHERE pl.pool_id=p.id AND pl.line_id=l.id AND pl.enabled=TRUE)
          AND (?='' OR l.resource_code ILIKE '%'||?||'%' OR COALESCE(l.line_alias,'') ILIKE '%'||?||'%' OR host(l.current_public_ip) ILIKE '%'||?||'%')
          AND (?='' OR l.line_type=?)
        ORDER BY CASE WHEN l.online_status='ONLINE' THEN 0 ELSE 1 END,l.resource_code LIMIT ? OFFSET ?
        """,poolId,keyword,keyword,keyword,keyword,type,type,size,offset);}
    public long countCandidatePoolLines(Long poolId,String keyword,String type){return jdbc.queryForObject("""
        SELECT count(*) FROM res_line l CROSS JOIN res_pool p
        WHERE p.id=? AND p.deleted=FALSE AND l.deleted=FALSE AND l.line_type IN ('SHARED','LONG')
          AND (COALESCE(p.province_code,'')='' OR COALESCE(l.province_code,l.region_code,'')=p.province_code)
          AND (COALESCE(p.city_code,'')='' OR COALESCE(l.city_code,'')=p.city_code)
          AND (COALESCE(p.carrier_code,'')='' OR COALESCE(l.carrier_code,'')=p.carrier_code)
          AND NOT EXISTS(SELECT 1 FROM res_pool_line pl WHERE pl.pool_id=p.id AND pl.line_id=l.id AND pl.enabled=TRUE)
          AND (?='' OR l.resource_code ILIKE '%'||?||'%' OR COALESCE(l.line_alias,'') ILIKE '%'||?||'%' OR host(l.current_public_ip) ILIKE '%'||?||'%')
          AND (?='' OR l.line_type=?)
        """,Long.class,poolId,keyword,keyword,keyword,keyword,type,type);}
    public boolean lineEligibleForPool(Long poolId,Long lineId){Long n=jdbc.queryForObject("""
        SELECT count(*) FROM res_line l CROSS JOIN res_pool p
        WHERE p.id=? AND p.deleted=FALSE AND l.id=? AND l.deleted=FALSE AND l.line_type IN ('SHARED','LONG')
          AND (COALESCE(p.province_code,'')='' OR COALESCE(l.province_code,l.region_code,'')=p.province_code)
          AND (COALESCE(p.city_code,'')='' OR COALESCE(l.city_code,'')=p.city_code)
          AND (COALESCE(p.carrier_code,'')='' OR COALESCE(l.carrier_code,'')=p.carrier_code)
        """,Long.class,poolId,lineId);return n!=null&&n>0;}
    public Long insertPool(String code,String name,String purpose,String province,String city,String carrier,String regionsJson,String carriersJson,Long actor){return jdbc.queryForObject("""
        INSERT INTO res_pool(pool_code,pool_name,pool_type,purpose,province_code,city_code,carrier_code,region_codes,carrier_codes,source_scope,member_mode,admission_rule,detection_rule,dedup_rule,priority,status,created_by,updated_by)
        VALUES(?,?,'SOURCE',?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),'SELF','LINE','{}','{}','{}',100,'ACTIVE',?,?) RETURNING id
        """,Long.class,code,name,purpose,province,city,carrier,regionsJson,carriersJson,actor,actor);}
    public int updatePool(Long id,String name,String purpose,String province,String city,String carrier,String regionsJson,String carriersJson,Long actor){return jdbc.update("""
        UPDATE res_pool SET pool_name=?,purpose=?,pool_type='SOURCE',province_code=?,city_code=?,carrier_code=?,region_codes=CAST(? AS jsonb),carrier_codes=CAST(? AS jsonb),source_scope='SELF',member_mode='LINE',updated_at=now(),updated_by=?,version=version+1
        WHERE id=? AND deleted=FALSE
        """,name,purpose,province,city,carrier,regionsJson,carriersJson,actor,id);}
    public int addPoolLines(Long poolId,List<Long> lineIds,String reason){int n=0;for(Long lineId:lineIds){n+=jdbc.update("INSERT INTO res_pool_line(pool_id,line_id,source_reason,enabled,joined_at,removed_at) VALUES(?,?,?,TRUE,now(),NULL) ON CONFLICT(pool_id,line_id) DO UPDATE SET source_reason=EXCLUDED.source_reason,enabled=TRUE,joined_at=now(),removed_at=NULL",poolId,lineId,reason);}return n;}
    public int removePoolLines(Long poolId,List<Long> lineIds,String reason){int n=0;for(Long lineId:lineIds){n+=jdbc.update("UPDATE res_pool_line SET enabled=FALSE,source_reason=?,removed_at=now() WHERE pool_id=? AND line_id=? AND enabled=TRUE",reason,poolId,lineId);}return n;}
    public int replacePoolLines(Long poolId,List<Long> lineIds,String reason){jdbc.update("UPDATE res_pool_line SET enabled=FALSE,source_reason=?,removed_at=now() WHERE pool_id=? AND enabled=TRUE",reason,poolId);return addPoolLines(poolId,lineIds,reason);}
    public Map<String,Object> poolUsage(Long poolId){return one("""
        SELECT count(DISTINCT b.service_id) service_count,count(DISTINCT s.customer_id) customer_count,
          COALESCE(string_agg(DISTINCT s.product_type,', ' ORDER BY s.product_type),'') product_types
        FROM svc_resource_binding b JOIN svc_instance s ON s.id=b.service_id
        WHERE b.resource_type='POOL' AND b.resource_id=? AND b.status='ACTIVE' AND s.deleted=FALSE
        """,poolId);}
    public List<Map<String,Object>> poolOperations(Long poolId,int limit){return jdbc.queryForList("""
        SELECT operator_name,operation,reason,result,error_message,created_at
        FROM sys_operation_log WHERE object_type='RESOURCE_POOL' AND object_id=? ORDER BY created_at DESC LIMIT ?
        """,String.valueOf(poolId),limit);}

    public List<Map<String,Object>> optionsRos(){return jdbc.queryForList("SELECT id,resource_code code,COALESCE(resource_name,resource_code) name FROM res_ros WHERE deleted=FALSE ORDER BY resource_code");}
    public List<Map<String,Object>> optionsLines(){return jdbc.queryForList("SELECT id,resource_code code,COALESCE(line_alias,resource_name,resource_code) name,host(current_public_ip) current_ip,line_type,province_code,city_code,carrier_code FROM res_line WHERE deleted=FALSE ORDER BY resource_code");}
    public List<Map<String,Object>> optionsCustomers(){return jdbc.queryForList("SELECT id,customer_code code,customer_name name FROM customer WHERE deleted=FALSE AND status='ACTIVE' ORDER BY customer_name");}
    public List<Map<String,Object>> optionsSuppliers(){return jdbc.queryForList("SELECT id,supplier_code code,supplier_name name FROM res_supplier WHERE deleted=FALSE AND status='ACTIVE' ORDER BY supplier_name");}
}
