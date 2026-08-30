package com.ipproxy.platform.runtime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AlarmRuleWriter {
    private final JdbcTemplate jdbc;
    public AlarmRuleWriter(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public Long create(String code,String name,String type,String metric,String operator,String thresholdJson,int duration,int consecutive,String severity,String channelsJson,boolean notifyRecovery,Integer repeat,Long userId){
        return jdbc.queryForObject("""
            INSERT INTO alarm_rule(
              rule_code,rule_name,object_type,metric_code,operator,threshold,
              duration_seconds,consecutive_count,severity,notification_channels,
              notify_recovery,repeat_interval_seconds,status,created_by,updated_by
            ) VALUES(?,?,?,?,?,CAST(? AS jsonb),?,?,?,CAST(? AS jsonb),?,?,'ACTIVE',?,?)
            RETURNING id
            """,Long.class,code,name,type,metric,operator,thresholdJson,duration,consecutive,severity,channelsJson,notifyRecovery,repeat,userId,userId);
    }
}
