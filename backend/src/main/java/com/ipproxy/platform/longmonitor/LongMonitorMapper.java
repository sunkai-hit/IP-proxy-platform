package com.ipproxy.platform.longmonitor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.*;

@Repository
public class LongMonitorMapper {
    private final JdbcTemplate jdbc;
    public LongMonitorMapper(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private Map<String,Object> one(String sql,Object...args){List<Map<String,Object>> rows=jdbc.queryForList(sql,args);return rows.isEmpty()?null:rows.getFirst();}

    public Map<String,Object> overview(){return one("""
      SELECT (SELECT count(*) FROM lm_monitor WHERE deleted=FALSE) monitor_total,
        (SELECT count(*) FROM lm_monitor WHERE deleted=FALSE AND enabled=TRUE) monitor_enabled,
        (SELECT count(*) FROM lm_monitor WHERE deleted=FALSE AND enabled=TRUE AND current_status='ABNORMAL') monitor_abnormal,
        (SELECT count(*) FROM lm_alarm WHERE status='OPEN') alarm_open,
        (SELECT count(*) FROM lm_bot WHERE deleted=FALSE AND enabled=TRUE) bot_enabled,
        (SELECT count(*) FROM lm_notification WHERE created_at>=now()-interval '24 hour' AND send_status='FAILED') notification_failed_24h
      """);}

    public long countBots(String k,Long customerId,Boolean enabled){return jdbc.queryForObject("""
      SELECT count(*) FROM lm_bot b JOIN customer c ON c.id=b.customer_id WHERE b.deleted=FALSE
      AND (?='' OR b.bot_no ILIKE '%'||?||'%' OR b.bot_name ILIKE '%'||?||'%' OR c.customer_name ILIKE '%'||?||'%')
      AND (? IS NULL OR b.customer_id=?) AND (? IS NULL OR b.enabled=?)
      """,Long.class,k,k,k,k,customerId,customerId,enabled,enabled);}
    public List<Map<String,Object>> bots(String k,Long customerId,Boolean enabled,int size,int offset){return jdbc.queryForList("""
      SELECT b.id,b.bot_no,b.customer_id,c.customer_name,b.bot_name,b.bot_type,b.webhook_url,(b.access_token_cipher IS NOT NULL) access_token_configured,(b.secret_cipher IS NOT NULL) secret_configured,b.enabled,b.last_test_at,b.last_test_status,b.last_test_message,b.remark,b.created_at,b.updated_at
      FROM lm_bot b JOIN customer c ON c.id=b.customer_id WHERE b.deleted=FALSE
      AND (?='' OR b.bot_no ILIKE '%'||?||'%' OR b.bot_name ILIKE '%'||?||'%' OR c.customer_name ILIKE '%'||?||'%')
      AND (? IS NULL OR b.customer_id=?) AND (? IS NULL OR b.enabled=?) ORDER BY b.updated_at DESC,b.id DESC LIMIT ? OFFSET ?
      """,k,k,k,k,customerId,customerId,enabled,enabled,size,offset);}
    public Map<String,Object> bot(Long id){return one("SELECT b.*,c.customer_name FROM lm_bot b JOIN customer c ON c.id=b.customer_id WHERE b.id=? AND b.deleted=FALSE",id);}
    public Long insertBot(String no,Long customerId,String name,String type,String webhook,String tokenCipher,String secretCipher,String remark,Long actor){return jdbc.queryForObject("INSERT INTO lm_bot(bot_no,customer_id,bot_name,bot_type,webhook_url,access_token_cipher,secret_cipher,remark,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?) RETURNING id",Long.class,no,customerId,name,type,webhook,tokenCipher,secretCipher,remark,actor,actor);}
    public int updateBot(Long id,String name,String type,String webhook,String tokenCipher,String secretCipher,String remark,Long actor){return jdbc.update("UPDATE lm_bot SET bot_name=?,bot_type=?,webhook_url=?,access_token_cipher=COALESCE(?,access_token_cipher),secret_cipher=COALESCE(?,secret_cipher),remark=?,updated_at=now(),updated_by=?,version=version+1 WHERE id=? AND deleted=FALSE",name,type,webhook,tokenCipher,secretCipher,remark,actor,id);}
    public int botEnabled(Long id,boolean enabled,Long actor){return jdbc.update("UPDATE lm_bot SET enabled=?,updated_at=now(),updated_by=?,version=version+1 WHERE id=? AND deleted=FALSE",enabled,actor,id);}
    public int botTestResult(Long id,String status,String message){return jdbc.update("UPDATE lm_bot SET last_test_at=now(),last_test_status=?,last_test_message=?,updated_at=now() WHERE id=? AND deleted=FALSE",status,message,id);}

    public long countMonitors(String k,String status,Long customerId,Boolean enabled){return jdbc.queryForObject("""
      SELECT count(*) FROM lm_monitor m JOIN customer c ON c.id=m.customer_id JOIN svc_instance s ON s.id=m.service_id WHERE m.deleted=FALSE
      AND (?='' OR m.monitor_no ILIKE '%'||?||'%' OR c.customer_name ILIKE '%'||?||'%' OR s.service_no ILIKE '%'||?||'%' OR m.target_host ILIKE '%'||?||'%')
      AND (?='' OR m.current_status=?) AND (? IS NULL OR m.customer_id=?) AND (? IS NULL OR m.enabled=?)
      """,Long.class,k,k,k,k,k,status,status,customerId,customerId,enabled,enabled);}
    public List<Map<String,Object>> monitors(String k,String status,Long customerId,Boolean enabled,int size,int offset){return jdbc.queryForList("""
      SELECT m.id,m.monitor_no,m.customer_id,c.customer_name,m.service_id,s.service_no,s.product_type,m.line_id,l.resource_code line_code,m.reverse_proxy_id,rp.proxy_no,
        m.target_host,m.target_port,m.proxy_protocol,m.check_method,m.check_url,m.interval_seconds,m.timeout_seconds,m.failure_threshold,m.recovery_threshold,
        m.bot_id,b.bot_name,b.bot_type,m.enabled,m.current_status,m.consecutive_failures,m.consecutive_successes,m.last_checked_at,m.last_success_at,m.last_failure_at,m.last_error,m.remark
      FROM lm_monitor m JOIN customer c ON c.id=m.customer_id JOIN svc_instance s ON s.id=m.service_id LEFT JOIN res_line l ON l.id=m.line_id LEFT JOIN res_reverse_proxy rp ON rp.id=m.reverse_proxy_id LEFT JOIN lm_bot b ON b.id=m.bot_id
      WHERE m.deleted=FALSE AND (?='' OR m.monitor_no ILIKE '%'||?||'%' OR c.customer_name ILIKE '%'||?||'%' OR s.service_no ILIKE '%'||?||'%' OR m.target_host ILIKE '%'||?||'%')
      AND (?='' OR m.current_status=?) AND (? IS NULL OR m.customer_id=?) AND (? IS NULL OR m.enabled=?) ORDER BY m.updated_at DESC,m.id DESC LIMIT ? OFFSET ?
      """,k,k,k,k,k,status,status,customerId,customerId,enabled,enabled,size,offset);}
    public Map<String,Object> monitor(Long id){return one("""
      SELECT m.*,c.customer_name,s.service_no,s.product_type,l.resource_code line_code,rp.proxy_no,b.bot_name,b.bot_type FROM lm_monitor m
      JOIN customer c ON c.id=m.customer_id JOIN svc_instance s ON s.id=m.service_id LEFT JOIN res_line l ON l.id=m.line_id LEFT JOIN res_reverse_proxy rp ON rp.id=m.reverse_proxy_id LEFT JOIN lm_bot b ON b.id=m.bot_id
      WHERE m.id=? AND m.deleted=FALSE
      """,id);}
    public Long insertMonitor(String no,Long customerId,Long serviceId,Long lineId,Long reverseProxyId,String host,Integer port,String protocol,String method,String checkUrl,Integer interval,Integer timeout,Integer failureThreshold,Integer recoveryThreshold,Long botId,String remark,Long actor){return jdbc.queryForObject("""
      INSERT INTO lm_monitor(monitor_no,customer_id,service_id,line_id,reverse_proxy_id,target_host,target_port,proxy_protocol,check_method,check_url,interval_seconds,timeout_seconds,failure_threshold,recovery_threshold,bot_id,remark,created_by,updated_by)
      VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id
      """,Long.class,no,customerId,serviceId,lineId,reverseProxyId,host,port,protocol,method,checkUrl,interval,timeout,failureThreshold,recoveryThreshold,botId,remark,actor,actor);}
    public int updateMonitor(Long id,Long lineId,Long reverseProxyId,String host,Integer port,String protocol,String method,String checkUrl,Integer interval,Integer timeout,Integer failureThreshold,Integer recoveryThreshold,Long botId,String remark,Long actor){return jdbc.update("""
      UPDATE lm_monitor SET line_id=?,reverse_proxy_id=?,target_host=?,target_port=?,proxy_protocol=?,check_method=?,check_url=?,interval_seconds=?,timeout_seconds=?,failure_threshold=?,recovery_threshold=?,bot_id=?,remark=?,updated_at=now(),updated_by=?,version=version+1 WHERE id=? AND deleted=FALSE
      """,lineId,reverseProxyId,host,port,protocol,method,checkUrl,interval,timeout,failureThreshold,recoveryThreshold,botId,remark,actor,id);}
    public int monitorEnabled(Long id,boolean enabled,Long actor){return jdbc.update("UPDATE lm_monitor SET enabled=?,updated_at=now(),updated_by=?,version=version+1 WHERE id=? AND deleted=FALSE",enabled,actor,id);}
    public List<Map<String,Object>> dueMonitors(int limit){return jdbc.queryForList("""
      SELECT * FROM lm_monitor WHERE deleted=FALSE AND enabled=TRUE AND (last_checked_at IS NULL OR last_checked_at + make_interval(secs=>interval_seconds) <= now()) ORDER BY COALESCE(last_checked_at,to_timestamp(0)) LIMIT ?
      """,limit);}
    public Long insertCheck(Long monitorId,boolean success,Integer latency,String outboundIp,Integer responseCode,String errorType,String errorMessage,String detailJson){return jdbc.queryForObject("INSERT INTO lm_check_record(monitor_id,success,latency_ms,outbound_ip,response_code,error_type,error_message,detail) VALUES(?,?,?,CAST(NULLIF(?,'') AS inet),?,?,?,CAST(? AS jsonb)) RETURNING id",Long.class,monitorId,success,latency,outboundIp,responseCode,errorType,errorMessage,detailJson);}
    public int updateMonitorCheck(Long id,String status,int failures,int successes,String error,boolean success){return jdbc.update("UPDATE lm_monitor SET current_status=?,consecutive_failures=?,consecutive_successes=?,last_checked_at=now(),last_success_at=CASE WHEN ? THEN now() ELSE last_success_at END,last_failure_at=CASE WHEN ? THEN last_failure_at ELSE now() END,last_error=?,updated_at=now() WHERE id=? AND deleted=FALSE",status,failures,successes,success,success,error,id);}
    public List<Map<String,Object>> checkHistory(Long id,int limit){return jdbc.queryForList("SELECT id,checked_at,success,latency_ms,host(outbound_ip) outbound_ip,response_code,error_type,error_message,detail FROM lm_check_record WHERE monitor_id=? ORDER BY checked_at DESC LIMIT ?",id,limit);}

    public Map<String,Object> openAlarm(Long monitorId){return one("SELECT * FROM lm_alarm WHERE monitor_id=? AND status='OPEN' ORDER BY id DESC LIMIT 1",monitorId);}
    public Long insertAlarm(String no,Long monitorId,Long customerId,Long serviceId,String failureJson){return jdbc.queryForObject("INSERT INTO lm_alarm(alarm_no,monitor_id,customer_id,service_id,failure_snapshot) VALUES(?,?,?,?,CAST(? AS jsonb)) RETURNING id",Long.class,no,monitorId,customerId,serviceId,failureJson);}
    public int recoverAlarm(Long id,String recoveryJson){return jdbc.update("UPDATE lm_alarm SET status='RECOVERED',recovered_at=now(),recovery_snapshot=CAST(? AS jsonb) WHERE id=? AND status='OPEN'",recoveryJson,id);}
    public int closeAlarm(Long id,String reason){return jdbc.update("UPDATE lm_alarm SET status='CLOSED',closed_at=now(),close_reason=? WHERE id=? AND status IN ('OPEN','RECOVERED')",reason,id);}
    public long countAlarms(String status,Long customerId){return jdbc.queryForObject("SELECT count(*) FROM lm_alarm a WHERE (?='' OR a.status=?) AND (? IS NULL OR a.customer_id=?)",Long.class,status,status,customerId,customerId);}
    public List<Map<String,Object>> alarms(String status,Long customerId,int size,int offset){return jdbc.queryForList("""
      SELECT a.id,a.alarm_no,a.monitor_id,m.monitor_no,a.customer_id,c.customer_name,a.service_id,s.service_no,a.alarm_type,a.status,a.opened_at,a.recovered_at,a.closed_at,a.close_reason,m.target_host,m.target_port,m.bot_id
      FROM lm_alarm a JOIN lm_monitor m ON m.id=a.monitor_id JOIN customer c ON c.id=a.customer_id JOIN svc_instance s ON s.id=a.service_id
      WHERE (?='' OR a.status=?) AND (? IS NULL OR a.customer_id=?) ORDER BY a.opened_at DESC LIMIT ? OFFSET ?
      """,status,status,customerId,customerId,size,offset);}
    public Map<String,Object> alarm(Long id){return one("SELECT a.*,m.monitor_no,c.customer_name,s.service_no,m.bot_id FROM lm_alarm a JOIN lm_monitor m ON m.id=a.monitor_id JOIN customer c ON c.id=a.customer_id JOIN svc_instance s ON s.id=a.service_id WHERE a.id=?",id);}

    public Long insertNotification(Long alarmId,Long monitorId,Long botId,String type,String channel,String requestJson,String responseJson,String status,String error){return jdbc.queryForObject("INSERT INTO lm_notification(alarm_id,monitor_id,bot_id,notification_type,channel,request_snapshot,response_snapshot,send_status,sent_at,error_message) VALUES(?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,CASE WHEN ?='SUCCESS' THEN now() ELSE NULL END,?) RETURNING id",Long.class,alarmId,monitorId,botId,type,channel,requestJson,responseJson,status,status,error);}
    public long countNotifications(String status,Long customerId){return jdbc.queryForObject("SELECT count(*) FROM lm_notification n JOIN lm_monitor m ON m.id=n.monitor_id WHERE (?='' OR n.send_status=?) AND (? IS NULL OR m.customer_id=?)",Long.class,status,status,customerId,customerId);}
    public List<Map<String,Object>> notifications(String status,Long customerId,int size,int offset){return jdbc.queryForList("""
      SELECT n.id,n.alarm_id,n.monitor_id,m.monitor_no,m.customer_id,c.customer_name,n.bot_id,b.bot_name,b.bot_type,n.notification_type,n.channel,n.send_status,n.sent_at,n.error_message,n.created_at
      FROM lm_notification n JOIN lm_monitor m ON m.id=n.monitor_id JOIN customer c ON c.id=m.customer_id LEFT JOIN lm_bot b ON b.id=n.bot_id
      WHERE (?='' OR n.send_status=?) AND (? IS NULL OR m.customer_id=?) ORDER BY n.created_at DESC LIMIT ? OFFSET ?
      """,status,status,customerId,customerId,size,offset);}

    public List<Map<String,Object>> customerOptions(){return jdbc.queryForList("SELECT id,customer_code code,customer_name name FROM customer WHERE deleted=FALSE AND status='ACTIVE' ORDER BY customer_name");}
    public List<Map<String,Object>> longServiceOptions(Long customerId){return jdbc.queryForList("SELECT id,service_no code,service_no||' / '||product_type name,customer_id,product_type,delivery_mode,status FROM svc_instance WHERE deleted=FALSE AND product_type='LONG_IP' AND status IN ('ACTIVE','SUSPENDED') AND (? IS NULL OR customer_id=?) ORDER BY created_at DESC",customerId,customerId);}
    public List<Map<String,Object>> lineOptions(Long customerId){return jdbc.queryForList("SELECT id,resource_code code,COALESCE(line_alias,resource_name,resource_code) name,host(current_public_ip) current_ip,customer_id FROM res_line WHERE deleted=FALSE AND (? IS NULL OR customer_id=? OR customer_id IS NULL) ORDER BY resource_code",customerId,customerId);}
    public List<Map<String,Object>> reverseProxyOptions(Long customerId){return jdbc.queryForList("SELECT id,proxy_no code,platform_host||':'||platform_port name,customer_id,status FROM res_reverse_proxy WHERE deleted=FALSE AND status IN ('PENDING','ACTIVE','ABNORMAL') AND (? IS NULL OR customer_id=?) ORDER BY created_at DESC",customerId,customerId);}
    public List<Map<String,Object>> botOptions(Long customerId){return jdbc.queryForList("SELECT id,bot_no code,bot_name name,bot_type FROM lm_bot WHERE deleted=FALSE AND enabled=TRUE AND (? IS NULL OR customer_id=?) ORDER BY bot_name",customerId,customerId);}
}
