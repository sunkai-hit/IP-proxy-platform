package com.ipproxy.platform.longmonitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipproxy.platform.common.api.PageResult;
import com.ipproxy.platform.common.audit.OperationAuditRepository;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.resource.security.ResourceSecretCipher;
import com.ipproxy.platform.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class LongMonitorService {
    private final LongMonitorMapper db; private final LongProxyCheckAdapter checker; private final CustomerBotNotificationAdapter notifier; private final ResourceSecretCipher cipher; private final ObjectMapper json; private final OperationAuditRepository audit;
    @Value("${app.long-monitor.scheduled-enabled:false}") private boolean scheduledEnabled;
    public LongMonitorService(LongMonitorMapper db,LongProxyCheckAdapter checker,CustomerBotNotificationAdapter notifier,ResourceSecretCipher cipher,ObjectMapper json,OperationAuditRepository audit){this.db=db;this.checker=checker;this.notifier=notifier;this.cipher=cipher;this.json=json;this.audit=audit;}

    public Map<String,Object> overview(){return db.overview();}
    public Map<String,Object> options(Long customerId){return Map.of("customers",db.customerOptions(),"services",db.longServiceOptions(customerId),"lines",db.lineOptions(customerId),"reverseProxies",db.reverseProxyOptions(customerId),"bots",db.botOptions(customerId));}

    public PageResult<Map<String,Object>> bots(int page,int size,String keyword,Long customerId,Boolean enabled){page=page(page);size=size(size);String k=n(keyword);return new PageResult<>(page,size,db.countBots(k,customerId,enabled),db.bots(k,customerId,enabled,size,(page-1)*size));}
    public Map<String,Object> bot(Long id){Map<String,Object>x=db.bot(id);if(x==null)throw notFound("客户机器人");Map<String,Object> out=new LinkedHashMap<>(x);out.remove("access_token_cipher");out.remove("secret_cipher");out.put("accessTokenConfigured",x.get("access_token_cipher")!=null);out.put("secretConfigured",x.get("secret_cipher")!=null);out.put("webhook_mask",maskUrl(String.valueOf(x.get("webhook_url"))));return out;}
    @Transactional public Long createBot(BotInput in,UserPrincipal actor,String ip){validateBot(in);String no=next("BOT");Long id=db.insertBot(no,in.customerId(),in.botName().trim(),in.botType().trim().toUpperCase(Locale.ROOT),in.webhookUrl().trim(),cipher.encrypt(in.accessToken()),cipher.encrypt(in.secret()),n(in.remark()),actor.userId());audit.success(actor,"LONG_MONITOR","BOT",id,"CREATE","新增客户机器人",ip);return id;}
    @Transactional public void updateBot(Long id,BotInput in,UserPrincipal actor,String ip){bot(id);validateBot(in);db.updateBot(id,in.botName().trim(),in.botType().trim().toUpperCase(Locale.ROOT),in.webhookUrl().trim(),n(in.accessToken()).isBlank()?null:cipher.encrypt(in.accessToken()),n(in.secret()).isBlank()?null:cipher.encrypt(in.secret()),n(in.remark()),actor.userId());audit.success(actor,"LONG_MONITOR","BOT",id,"UPDATE","修改客户机器人",ip);}
    @Transactional public void botEnabled(Long id,boolean enabled,String reason,UserPrincipal actor,String ip){bot(id);db.botEnabled(id,enabled,actor.userId());audit.success(actor,"LONG_MONITOR","BOT",id,enabled?"ENABLE":"DISABLE",required(reason,"操作原因不能为空"),ip);}
    @Transactional public Map<String,Object> testBot(Long id,String reason,UserPrincipal actor,String ip){Map<String,Object> raw=rawBot(id);Map<String,Object> payload=Map.of("title","IP代理平台机器人测试","message","这是一条V1.2客户机器人配置测试消息","time",OffsetDateTime.now().toString());CustomerBotNotificationAdapter.Result r=notifier.send(botConfig(raw),"TEST",payload);db.botTestResult(id,r.success()?"SUCCESS":"FAILED",r.message());audit.success(actor,"LONG_MONITOR","BOT",id,"TEST",required(reason,"操作原因不能为空"),ip);return Map.of("success",r.success(),"message",r.message(),"response",r.response());}

    public PageResult<Map<String,Object>> monitors(int page,int size,String keyword,String status,Long customerId,Boolean enabled){page=page(page);size=size(size);String k=n(keyword),s=n(status);return new PageResult<>(page,size,db.countMonitors(k,s,customerId,enabled),db.monitors(k,s,customerId,enabled,size,(page-1)*size));}
    public Map<String,Object> monitor(Long id){Map<String,Object>x=db.monitor(id);if(x==null)throw notFound("长效代理监控");x=new LinkedHashMap<>(x);x.put("checkHistory",db.checkHistory(id,200));return x;}
    @Transactional public Long createMonitor(MonitorInput in,UserPrincipal actor,String ip){validateMonitor(in);String no=next("LM");Long id=db.insertMonitor(no,in.customerId(),in.serviceId(),in.lineId(),in.reverseProxyId(),in.targetHost().trim(),in.targetPort(),up(in.proxyProtocol(),"HTTP"),up(in.checkMethod(),"PROXY_CONNECT"),n(in.checkUrl()),val(in.intervalSeconds(),60),val(in.timeoutSeconds(),10),val(in.failureThreshold(),3),val(in.recoveryThreshold(),2),in.botId(),n(in.remark()),actor.userId());audit.success(actor,"LONG_MONITOR","MONITOR",id,"CREATE","新增客户长效代理监控",ip);return id;}
    @Transactional public void updateMonitor(Long id,MonitorInput in,UserPrincipal actor,String ip){monitor(id);validateMonitor(in);db.updateMonitor(id,in.lineId(),in.reverseProxyId(),in.targetHost().trim(),in.targetPort(),up(in.proxyProtocol(),"HTTP"),up(in.checkMethod(),"PROXY_CONNECT"),n(in.checkUrl()),val(in.intervalSeconds(),60),val(in.timeoutSeconds(),10),val(in.failureThreshold(),3),val(in.recoveryThreshold(),2),in.botId(),n(in.remark()),actor.userId());audit.success(actor,"LONG_MONITOR","MONITOR",id,"UPDATE","修改客户长效代理监控",ip);}
    @Transactional public void monitorEnabled(Long id,boolean enabled,String reason,UserPrincipal actor,String ip){monitor(id);db.monitorEnabled(id,enabled,actor.userId());audit.success(actor,"LONG_MONITOR","MONITOR",id,enabled?"ENABLE":"DISABLE",required(reason,"操作原因不能为空"),ip);}
    @Transactional public Map<String,Object> check(Long id,String reason,UserPrincipal actor,String ip){Map<String,Object> result=checkOne(id);audit.success(actor,"LONG_MONITOR","MONITOR",id,"CHECK",required(reason,"检测原因不能为空"),ip);return result;}

    @Scheduled(fixedDelayString="${app.long-monitor.scan-delay-ms:30000}")
    public void scheduledCheck(){if(!scheduledEnabled)return;for(Map<String,Object> m:db.dueMonitors(100)){try{checkOne(((Number)m.get("id")).longValue());}catch(Exception ignored){}}}

    @Transactional public Map<String,Object> checkOne(Long id){Map<String,Object> m=db.monitor(id);if(m==null)throw notFound("长效代理监控");LongMonitorMapperUnusedGuard.guard(m);LongProxyCheckAdapter.CheckResult r=checker.check(m);String adapterStatus=up(r.status(),"UNKNOWN");int failures=number(m.get("consecutive_failures"),0),successes=number(m.get("consecutive_successes"),0);String before=String.valueOf(m.get("current_status"));String after=before;boolean success="NORMAL".equals(adapterStatus);
        if("NORMAL".equals(adapterStatus)){failures=0;successes++;int threshold=number(m.get("recovery_threshold"),2);if(!"ABNORMAL".equals(before)||successes>=threshold)after="NORMAL";}
        else if("ABNORMAL".equals(adapterStatus)){successes=0;failures++;if(failures>=number(m.get("failure_threshold"),3))after="ABNORMAL";}
        else {after="UNKNOWN";}
        db.insertCheck(id,success,r.latencyMs(),n(r.outboundIp()),r.responseCode(),n(r.errorType()),n(r.message()),j(r.detail()));db.updateMonitorCheck(id,after,failures,successes,"NORMAL".equals(adapterStatus)?null:r.message(),success);
        Map<String,Object> event=new LinkedHashMap<>();event.put("monitorId",id);event.put("monitorNo",m.get("monitor_no"));event.put("customerId",m.get("customer_id"));event.put("serviceId",m.get("service_id"));event.put("target",m.get("target_host")+":"+m.get("target_port"));event.put("status",after);event.put("message",r.message());event.put("latencyMs",r.latencyMs());
        if(!"ABNORMAL".equals(before)&&"ABNORMAL".equals(after))openAlarmAndNotify(m,event);
        if("ABNORMAL".equals(before)&&"NORMAL".equals(after))recoverAlarmAndNotify(m,event);
        return Map.of("before",before,"after",after,"adapterStatus",adapterStatus,"consecutiveFailures",failures,"consecutiveSuccesses",successes,"message",r.message());}

    private void openAlarmAndNotify(Map<String,Object> m,Map<String,Object> event){Long mid=num(m.get("id"));Map<String,Object> open=db.openAlarm(mid);Long aid=open==null?db.insertAlarm(next("ALM"),mid,num(m.get("customer_id")),num(m.get("service_id")),j(event)):num(open.get("id"));notifyBot(m,aid,"ALARM",event);}
    private void recoverAlarmAndNotify(Map<String,Object> m,Map<String,Object> event){Map<String,Object> open=db.openAlarm(num(m.get("id")));if(open==null)return;Long aid=num(open.get("id"));db.recoverAlarm(aid,j(event));notifyBot(m,aid,"RECOVERY",event);}
    private void notifyBot(Map<String,Object> m,Long alarmId,String type,Map<String,Object> event){Long botId=num(m.get("bot_id"));if(botId==null)return;Map<String,Object> raw=rawBot(botId);CustomerBotNotificationAdapter.Result r=notifier.send(botConfig(raw),type,event);db.insertNotification(alarmId,num(m.get("id")),botId,type,String.valueOf(raw.get("bot_type")),j(event),j(r.response()),r.success()?"SUCCESS":"FAILED",r.success()?null:r.message());}

    public PageResult<Map<String,Object>> alarms(int page,int size,String status,Long customerId){page=page(page);size=size(size);String s=n(status);return new PageResult<>(page,size,db.countAlarms(s,customerId),db.alarms(s,customerId,size,(page-1)*size));}
    public Map<String,Object> alarm(Long id){Map<String,Object>x=db.alarm(id);if(x==null)throw notFound("告警");return x;}
    @Transactional public void closeAlarm(Long id,String reason,UserPrincipal actor,String ip){alarm(id);if(db.closeAlarm(id,required(reason,"关闭原因不能为空"))==0)throw new BusinessException("LONG_ALARM_STATUS_INVALID","当前告警不可关闭");audit.success(actor,"LONG_MONITOR","ALARM",id,"CLOSE",reason,ip);}
    public PageResult<Map<String,Object>> notifications(int page,int size,String status,Long customerId){page=page(page);size=size(size);String s=n(status);return new PageResult<>(page,size,db.countNotifications(s,customerId),db.notifications(s,customerId,size,(page-1)*size));}

    private Map<String,Object> rawBot(Long id){Map<String,Object>x=db.bot(id);if(x==null)throw notFound("客户机器人");return x;}
    private Map<String,Object> botConfig(Map<String,Object> raw){Map<String,Object>x=new LinkedHashMap<>(raw);String t=(String)raw.get("access_token_cipher"),s=(String)raw.get("secret_cipher");x.put("accessToken",t==null?null:cipher.decrypt(t));x.put("secret",s==null?null:cipher.decrypt(s));x.remove("access_token_cipher");x.remove("secret_cipher");return x;}
    private void validateBot(BotInput in){if(in==null||in.customerId()==null)throw new BusinessException("BOT_CUSTOMER_REQUIRED","客户不能为空");required(in.botName(),"机器人名称不能为空");String type=up(in.botType(),"");if(!Set.of("DINGTALK","FEISHU").contains(type))throw new BusinessException("BOT_TYPE_INVALID","机器人类型仅支持钉钉或飞书");required(in.webhookUrl(),"Webhook/API地址不能为空");}
    private void validateMonitor(MonitorInput in){if(in==null||in.customerId()==null||in.serviceId()==null)throw new BusinessException("LONG_MONITOR_REQUIRED","客户和长效服务不能为空");required(in.targetHost(),"监控目标不能为空");if(in.targetPort()==null||in.targetPort()<1||in.targetPort()>65535)throw new BusinessException("LONG_MONITOR_PORT_INVALID","端口必须在1-65535之间");if(val(in.intervalSeconds(),60)<30)throw new BusinessException("LONG_MONITOR_INTERVAL_INVALID","检测周期不能小于30秒");}
    private int page(int x){return Math.max(1,x);}private int size(int x){return Math.min(200,Math.max(1,x));}private int val(Integer x,int d){return x==null?d:x;}private int number(Object x,int d){return x instanceof Number n?n.intValue():x==null?d:Integer.parseInt(String.valueOf(x));}private Long num(Object x){return x instanceof Number n?n.longValue():x==null?null:Long.valueOf(String.valueOf(x));}
    private String n(String x){return x==null?"":x.trim();}private String up(String x,String d){return n(x).isBlank()?d:n(x).toUpperCase(Locale.ROOT);}private String required(String x,String msg){if(n(x).isBlank())throw new BusinessException("LONG_MONITOR_REQUIRED",msg);return x.trim();}
    private String next(String prefix){return prefix+"-"+OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))+"-"+Integer.toHexString(new Random().nextInt(65536)).toUpperCase(Locale.ROOT);}
    private String j(Object x){try{return json.writeValueAsString(x==null?Map.of():x);}catch(Exception e){return "{}";}}
    private String maskUrl(String x){if(x==null||x.length()<18)return "已配置";return x.substring(0,12)+"••••••"+x.substring(x.length()-6);}
    private BusinessException notFound(String n){return new BusinessException("LONG_MONITOR_NOT_FOUND",n+"不存在");}

    public record BotInput(Long customerId,String botName,String botType,String webhookUrl,String accessToken,String secret,String remark){}
    public record MonitorInput(Long customerId,Long serviceId,Long lineId,Long reverseProxyId,String targetHost,Integer targetPort,String proxyProtocol,String checkMethod,String checkUrl,Integer intervalSeconds,Integer timeoutSeconds,Integer failureThreshold,Integer recoveryThreshold,Long botId,String remark){}

    /** 防止静态分析将m误判为未使用；运行时无副作用。 */
    private static final class LongMonitorMapperUnusedGuard{static void guard(Map<String,Object> ignored){}}
}
