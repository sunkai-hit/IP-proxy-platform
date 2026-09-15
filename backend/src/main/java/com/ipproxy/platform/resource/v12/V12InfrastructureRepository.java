package com.ipproxy.platform.resource.v12;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * V20 基础设施查询模型。
 * 当前版本：CentOS <-> ROS 为 1:1；CentOS 承担管理与 B/C 段聚合，ROS 只承担拨号执行与结果上报。
 */
@Repository
public class V12InfrastructureRepository {
    private final JdbcTemplate jdbc;
    public V12InfrastructureRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}

    private Map<String,Object> one(String sql,Object... args){
        List<Map<String,Object>> rows=jdbc.queryForList(sql,args);
        return rows.isEmpty()?null:rows.getFirst();
    }

    public long countCentos(String keyword,String status){
        return jdbc.queryForObject("""
            SELECT count(*) FROM res_centos c
            WHERE c.deleted=FALSE
              AND (?='' OR c.resource_code ILIKE '%'||?||'%' OR COALESCE(c.resource_name,'') ILIKE '%'||?||'%' OR COALESCE(c.host_account,'') ILIKE '%'||?||'%' OR host(c.management_ip) ILIKE '%'||?||'%')
              AND (?='' OR c.online_status=?)
            """,Long.class,keyword,keyword,keyword,keyword,keyword,status,status);
    }

    public List<Map<String,Object>> listCentos(String keyword,String status,int size,int offset){
        return jdbc.queryForList("""
            SELECT c.id,c.resource_code,c.resource_name,c.host_type,c.host_account,host(c.management_ip) management_ip,
              c.location_name,c.region_code,c.carrier_code,c.online_status,c.cpu_usage,c.memory_usage,c.system_load,c.disk_usage,
              c.vpn_supported,c.pppoe_server_name,c.remark,c.last_communicated_at,c.last_sync_at,
              (SELECT count(*) FROM res_line l JOIN res_ros r ON r.id=l.ros_id
                 WHERE r.centos_id=c.id AND r.deleted=FALSE AND l.deleted=FALSE) line_count,
              (SELECT count(DISTINCT (split_part(host(l.current_public_ip),'.',1)||'.'||split_part(host(l.current_public_ip),'.',2)))
                 FROM res_line l JOIN res_ros r ON r.id=l.ros_id
                 WHERE r.centos_id=c.id AND r.deleted=FALSE AND l.deleted=FALSE AND l.current_public_ip IS NOT NULL AND family(l.current_public_ip)=4) b_prefix_count,
              (SELECT count(DISTINCT (split_part(host(l.current_public_ip),'.',1)||'.'||split_part(host(l.current_public_ip),'.',2)||'.'||split_part(host(l.current_public_ip),'.',3)))
                 FROM res_line l JOIN res_ros r ON r.id=l.ros_id
                 WHERE r.centos_id=c.id AND r.deleted=FALSE AND l.deleted=FALSE AND l.current_public_ip IS NOT NULL AND family(l.current_public_ip)=4) c_prefix_count,
              (SELECT r.resource_code FROM res_ros r WHERE r.centos_id=c.id AND r.deleted=FALSE ORDER BY r.id LIMIT 1) corresponding_ros_code,
              (SELECT r.record_name FROM res_ros r WHERE r.centos_id=c.id AND r.deleted=FALSE ORDER BY r.id LIMIT 1) corresponding_ros_record
            FROM res_centos c
            WHERE c.deleted=FALSE
              AND (?='' OR c.resource_code ILIKE '%'||?||'%' OR COALESCE(c.resource_name,'') ILIKE '%'||?||'%' OR COALESCE(c.host_account,'') ILIKE '%'||?||'%' OR host(c.management_ip) ILIKE '%'||?||'%')
              AND (?='' OR c.online_status=?)
            ORDER BY c.updated_at DESC,c.id DESC LIMIT ? OFFSET ?
            """,keyword,keyword,keyword,keyword,keyword,status,status,size,offset);
    }

    public Map<String,Object> centos(Long id){
        return one("""
            SELECT c.id,c.resource_code,c.resource_name,c.host_type,c.host_account,host(c.management_ip) management_ip_text,
              c.location_name,c.region_code,c.carrier_code,c.online_status,c.cpu_usage,c.memory_usage,c.system_load,c.disk_usage,
              c.network_in_bps,c.network_out_bps,c.vpn_supported,c.pppoe_server_name,c.remark,c.last_communicated_at,c.last_sync_at,
              (SELECT count(*) FROM res_line l JOIN res_ros r ON r.id=l.ros_id
                 WHERE r.centos_id=c.id AND r.deleted=FALSE AND l.deleted=FALSE) line_count,
              (SELECT count(DISTINCT (split_part(host(l.current_public_ip),'.',1)||'.'||split_part(host(l.current_public_ip),'.',2)))
                 FROM res_line l JOIN res_ros r ON r.id=l.ros_id
                 WHERE r.centos_id=c.id AND r.deleted=FALSE AND l.deleted=FALSE AND l.current_public_ip IS NOT NULL AND family(l.current_public_ip)=4) b_prefix_count,
              (SELECT count(DISTINCT (split_part(host(l.current_public_ip),'.',1)||'.'||split_part(host(l.current_public_ip),'.',2)||'.'||split_part(host(l.current_public_ip),'.',3)))
                 FROM res_line l JOIN res_ros r ON r.id=l.ros_id
                 WHERE r.centos_id=c.id AND r.deleted=FALSE AND l.deleted=FALSE AND l.current_public_ip IS NOT NULL AND family(l.current_public_ip)=4) c_prefix_count
            FROM res_centos c WHERE c.id=? AND c.deleted=FALSE
            """,id);
    }

    public Map<String,Object> centosRos(Long centosId){
        return one("""
            SELECT r.id,r.resource_code,r.resource_name,r.record_name,r.online_status,r.line_total,r.line_online,r.line_abnormal,
              r.current_ip_count,r.bas_count,r.cpu_usage,r.memory_usage,r.system_load,r.disk_usage,r.last_report_at,r.last_sync_at
            FROM res_ros r
            WHERE r.centos_id=? AND r.deleted=FALSE
            ORDER BY r.id LIMIT 1
            """,centosId);
    }

    public long countRos(String keyword,String status){
        return jdbc.queryForObject("""
            SELECT count(*) FROM res_ros r
            WHERE r.deleted=FALSE
              AND (?='' OR r.resource_code ILIKE '%'||?||'%' OR COALESCE(r.resource_name,'') ILIKE '%'||?||'%' OR COALESCE(r.record_name,'') ILIKE '%'||?||'%')
              AND (?='' OR r.online_status=?)
            """,Long.class,keyword,keyword,keyword,keyword,status,status);
    }

    public List<Map<String,Object>> listRos(String keyword,String status,int size,int offset){
        return jdbc.queryForList("""
            SELECT r.id,r.resource_code,r.resource_name,r.record_name,r.host_type,host(r.management_ip) management_ip,
              r.region_code,r.carrier_code,r.online_status,r.cpu_usage,r.memory_usage,r.system_load,r.disk_usage,
              r.line_total,r.line_online,r.line_abnormal,r.current_ip_count,r.bas_count,r.uptime_seconds,
              r.last_report_at,r.last_sync_at
            FROM res_ros r
            WHERE r.deleted=FALSE
              AND (?='' OR r.resource_code ILIKE '%'||?||'%' OR COALESCE(r.resource_name,'') ILIKE '%'||?||'%' OR COALESCE(r.record_name,'') ILIKE '%'||?||'%')
              AND (?='' OR r.online_status=?)
            ORDER BY r.updated_at DESC,r.id DESC LIMIT ? OFFSET ?
            """,keyword,keyword,keyword,keyword,status,status,size,offset);
    }

    public Map<String,Object> ros(Long id){
        return one("""
            SELECT r.id,r.resource_code,r.resource_name,r.record_name,r.host_type,host(r.management_ip) management_ip_text,
              r.region_code,r.carrier_code,r.online_status,r.cpu_usage,r.memory_usage,r.system_load,r.disk_usage,
              r.line_total,r.line_online,r.line_abnormal,r.current_ip_count,r.bas_count,r.uptime_seconds,
              r.last_communicated_at,r.last_report_at,r.last_sync_at
            FROM res_ros r WHERE r.id=? AND r.deleted=FALSE
            """,id);
    }

    public List<Map<String,Object>> rosBas(Long id){
        return jdbc.queryForList("""
            SELECT COALESCE(NULLIF(bas_name,''),'UNKNOWN') bas_name,MAX(bas_address) bas_address,count(*) line_total,
              count(*) FILTER(WHERE online_status='ONLINE') online_lines,
              count(DISTINCT current_public_ip) FILTER(WHERE current_public_ip IS NOT NULL) current_ip_count
            FROM res_line
            WHERE ros_id=? AND deleted=FALSE
            GROUP BY COALESCE(NULLIF(bas_name,''),'UNKNOWN')
            ORDER BY line_total DESC
            """,id);
    }

    public long countLines(String keyword,String status,Long rosId,String type,String province,String city,String carrier){
        return jdbc.queryForObject("""
            SELECT count(*) FROM res_line l
            WHERE l.deleted=FALSE
              AND (?='' OR l.resource_code ILIKE '%'||?||'%' OR COALESCE(l.line_alias,'') ILIKE '%'||?||'%' OR COALESCE(l.broadband_account,'') ILIKE '%'||?||'%' OR host(l.current_public_ip) ILIKE '%'||?||'%' OR COALESCE(l.domain_name,'') ILIKE '%'||?||'%')
              AND (?='' OR l.online_status=?)
              AND (CAST(? AS BIGINT) IS NULL OR l.ros_id=CAST(? AS BIGINT))
              AND (?='' OR COALESCE(l.line_type,'')=?)
              AND (?='' OR COALESCE(l.province_code,l.region_code,'')=?)
              AND (?='' OR COALESCE(l.city_code,'')=?)
              AND (?='' OR COALESCE(l.carrier_code,'')=?)
            """,Long.class,keyword,keyword,keyword,keyword,keyword,keyword,status,status,rosId,rosId,type,type,province,province,city,city,carrier,carrier);
    }

    public List<Map<String,Object>> listLines(String keyword,String status,Long rosId,String type,String province,String city,String carrier,int size,int offset){
        return jdbc.queryForList("""
            SELECT l.id,l.resource_code,l.resource_name,l.line_alias,l.ros_id,r.resource_code ros_code,r.resource_name ros_name,
              c.id centos_id,c.resource_code centos_code,l.broadband_account,l.broadband_account_mask,l.account_prefix,
              host(l.current_public_ip) current_public_ip,l.http_port,l.socks5_port,l.domain_name,l.managed_flag,l.line_type,
              l.user_limit,l.active_users,l.restart_interval_minutes,l.available_time_range,l.vpn_supported,l.vpn_redial_monitor,
              l.bas_name,l.bas_address,l.province_code,l.city_code,l.region_code,l.carrier_code,l.online_status,l.dial_status,l.latency_ms,
              l.last_dial_at,l.last_ip_changed_at,l.last_ros_report_at,l.last_sync_at
            FROM res_line l
            JOIN res_ros r ON r.id=l.ros_id
            LEFT JOIN res_centos c ON c.id=r.centos_id
            WHERE l.deleted=FALSE
              AND (?='' OR l.resource_code ILIKE '%'||?||'%' OR COALESCE(l.line_alias,'') ILIKE '%'||?||'%' OR COALESCE(l.broadband_account,'') ILIKE '%'||?||'%' OR host(l.current_public_ip) ILIKE '%'||?||'%' OR COALESCE(l.domain_name,'') ILIKE '%'||?||'%')
              AND (?='' OR l.online_status=?)
              AND (CAST(? AS BIGINT) IS NULL OR l.ros_id=CAST(? AS BIGINT))
              AND (?='' OR COALESCE(l.line_type,'')=?)
              AND (?='' OR COALESCE(l.province_code,l.region_code,'')=?)
              AND (?='' OR COALESCE(l.city_code,'')=?)
              AND (?='' OR COALESCE(l.carrier_code,'')=?)
            ORDER BY l.updated_at DESC,l.id DESC LIMIT ? OFFSET ?
            """,keyword,keyword,keyword,keyword,keyword,keyword,status,status,rosId,rosId,type,type,province,province,city,city,carrier,carrier,size,offset);
    }

    public Map<String,Object> line(Long id){
        return one("""
            SELECT l.id,l.resource_code,l.resource_name,l.line_alias,l.ros_id,r.resource_code ros_code,r.resource_name ros_name,
              c.id centos_id,c.resource_code centos_code,c.resource_name centos_name,l.broadband_account,l.broadband_account_mask,
              l.account_prefix,host(l.current_public_ip) current_public_ip_text,l.http_port,l.socks5_port,l.domain_name,
              l.managed_flag,l.line_type,l.user_limit,l.active_users,l.restart_interval_minutes,l.available_time_range,
              l.vpn_supported,l.vpn_redial_monitor,l.bas_name,l.bas_address,l.province_code,l.city_code,l.region_code,l.carrier_code,
              l.online_status,l.dial_status,l.latency_ms,l.availability_rate,l.last_dial_at,l.last_ip_changed_at,l.last_ros_report_at,l.last_sync_at,
              (SELECT count(*) FROM res_pool_line pl WHERE pl.line_id=l.id AND pl.enabled=TRUE) pool_count,
              (SELECT string_agg(p.pool_name,', ' ORDER BY p.pool_name)
                 FROM res_pool_line pl JOIN res_pool p ON p.id=pl.pool_id
                 WHERE pl.line_id=l.id AND pl.enabled=TRUE) pool_names
            FROM res_line l
            JOIN res_ros r ON r.id=l.ros_id
            LEFT JOIN res_centos c ON c.id=r.centos_id
            WHERE l.id=? AND l.deleted=FALSE
            """,id);
    }
}
