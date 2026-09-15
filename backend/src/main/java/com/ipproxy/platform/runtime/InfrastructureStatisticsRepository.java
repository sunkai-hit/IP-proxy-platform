package com.ipproxy.platform.runtime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

/** V20基础资源统计：B/C段归属CentOS管理口径，不再使用ROS维度统计字段。 */
@Repository
public class InfrastructureStatisticsRepository {
    private final JdbcTemplate jdbc;
    public InfrastructureStatisticsRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public int calculateResource(OffsetDateTime hour,OffsetDateTime end){
        jdbc.update("DELETE FROM stat_resource_hourly WHERE stat_hour=?",hour);
        return jdbc.update("""
            INSERT INTO stat_resource_hourly(
              stat_hour,region_code,carrier_code,centos_total,centos_online,ros_total,ros_online,
              line_total,line_online,line_abnormal,b_prefix_count,c_prefix_count)
            SELECT ?,'','',
              (SELECT COUNT(*) FROM res_centos WHERE deleted=FALSE),
              (SELECT COUNT(*) FROM res_centos WHERE deleted=FALSE AND online_status='ONLINE'),
              (SELECT COUNT(*) FROM res_ros WHERE deleted=FALSE),
              (SELECT COUNT(*) FROM res_ros WHERE deleted=FALSE AND online_status='ONLINE'),
              (SELECT COUNT(*) FROM res_line WHERE deleted=FALSE),
              (SELECT COUNT(*) FROM res_line WHERE deleted=FALSE AND online_status='ONLINE'),
              (SELECT COUNT(*) FROM res_line WHERE deleted=FALSE AND online_status='ABNORMAL'),
              (SELECT COUNT(DISTINCT (split_part(host(l.current_public_ip),'.',1)||'.'||split_part(host(l.current_public_ip),'.',2)))
                 FROM res_line l JOIN res_ros r ON r.id=l.ros_id JOIN res_centos c ON c.id=r.centos_id
                 WHERE l.deleted=FALSE AND r.deleted=FALSE AND c.deleted=FALSE AND l.current_public_ip IS NOT NULL AND family(l.current_public_ip)=4),
              (SELECT COUNT(DISTINCT (split_part(host(l.current_public_ip),'.',1)||'.'||split_part(host(l.current_public_ip),'.',2)||'.'||split_part(host(l.current_public_ip),'.',3)))
                 FROM res_line l JOIN res_ros r ON r.id=l.ros_id JOIN res_centos c ON c.id=r.centos_id
                 WHERE l.deleted=FALSE AND r.deleted=FALSE AND c.deleted=FALSE AND l.current_public_ip IS NOT NULL AND family(l.current_public_ip)=4)
            """,hour);
    }
}
