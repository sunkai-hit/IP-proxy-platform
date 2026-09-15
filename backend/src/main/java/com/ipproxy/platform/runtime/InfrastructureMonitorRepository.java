package com.ipproxy.platform.runtime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

/** V20监控数据源：CentOS负责管理与B/C段聚合，ROS仅负责拨号执行。 */
@Repository
public class InfrastructureMonitorRepository {
    private final JdbcTemplate jdbc;
    public InfrastructureMonitorRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public List<Map<String,Object>> centosTargets(){
        return jdbc.queryForList("""
            SELECT c.id object_id,c.resource_code object_code,COALESCE(c.resource_name,c.resource_code) object_name,
              c.online_status source_status,COALESCE(c.last_communicated_at,c.last_sync_at) source_at,
              c.cpu_usage,c.memory_usage,c.disk_usage,c.system_load,c.network_in_bps,c.network_out_bps,
              (SELECT count(DISTINCT (split_part(host(l.current_public_ip),'.',1)||'.'||split_part(host(l.current_public_ip),'.',2)))
                 FROM res_line l JOIN res_ros r ON r.id=l.ros_id
                 WHERE r.centos_id=c.id AND r.deleted=FALSE AND l.deleted=FALSE AND l.current_public_ip IS NOT NULL AND family(l.current_public_ip)=4) b_prefix_count,
              (SELECT count(DISTINCT (split_part(host(l.current_public_ip),'.',1)||'.'||split_part(host(l.current_public_ip),'.',2)||'.'||split_part(host(l.current_public_ip),'.',3)))
                 FROM res_line l JOIN res_ros r ON r.id=l.ros_id
                 WHERE r.centos_id=c.id AND r.deleted=FALSE AND l.deleted=FALSE AND l.current_public_ip IS NOT NULL AND family(l.current_public_ip)=4) c_prefix_count
            FROM res_centos c WHERE c.deleted=FALSE ORDER BY c.id
            """);
    }

    public List<Map<String,Object>> rosTargets(){
        return jdbc.queryForList("""
            SELECT r.id object_id,r.resource_code object_code,COALESCE(NULLIF(r.record_name,''),r.resource_name,r.resource_code) object_name,
              r.online_status source_status,COALESCE(r.last_report_at,r.last_sync_at) source_at,
              r.cpu_usage,r.memory_usage,r.disk_usage,r.system_load,
              r.line_total,r.line_online,r.line_abnormal,r.current_ip_count,r.bas_count
            FROM res_ros r WHERE r.deleted=FALSE ORDER BY r.id
            """);
    }
}
