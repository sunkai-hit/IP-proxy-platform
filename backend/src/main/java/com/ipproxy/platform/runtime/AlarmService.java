package com.ipproxy.platform.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipproxy.platform.common.api.PageResult;
import com.ipproxy.platform.common.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AlarmService {
    private static final Set<String> TYPES=Set.of("CENTOS","ROS","LINE","IP","POOL","SUPPLIER","SERVICE");
    private static final Set<String> OPS=Set.of("EQ","NE","GT","GTE","LT","LTE","IN","NOT_IN");
    private static final Set<String> SEVERITIES=Set.of("INFO","MINOR","MAJOR","CRITICAL");
    private static final Set<String> CHANNELS=Set.of("PLATFORM","DINGTALK","WEBHOOK");
    private final RuntimeOperationsRepository db;private final AlarmRuleWriter ruleWriter;private final ObjectMapper json;
    public AlarmService(RuntimeOperationsRepository db,AlarmRuleWriter ruleWriter,ObjectMapper json){this.db=db;this.ruleWriter=ruleWriter;this.json=json;}

    public PageResult<Map<String,Object>> alarms(int page,int size,String status,String severity,String keyword){int p=Math.max(1,page),s=Math.min(200,Math.max(1,size));return new PageResult<>(p,s,db.alarmCount(status,severity,keyword),db.alarms(status,severity,keyword,s,(p-1)*s));}
    public Map<String,Object> alarm(Long id){Map<String,Object> e=need(db.alarm(id),"ALARM_NOT_FOUND","告警不存在");Map<String,Object> out=new LinkedHashMap<>(e);out.put("processes",db.alarmProcesses(id));out.put("notifications",db.alarmNotifications(id));return out;}
    public PageResult<Map<String,Object>> rules(int page,int size,String keyword){int p=Math.max(1,page),s=Math.min(200,Math.max(1,size));List<Map<String,Object>> items=new ArrayList<>();for(Map<String,Object> r:db.rules(keyword,s,(p-1)*s))items.add(decorateRule(r));return new PageResult<>(p,s,db.ruleCount(keyword),items);}
    public PageResult<Map<String,Object>> notifications(int page,int size,String channel,String result){int p=Math.max(1,page),s=Math.min(200,Math.max(1,size));return new PageResult<>(p,s,db.notificationCount(channel,result),db.notifications(channel,result,s,(p-1)*s));}

    public Map<String,Object> createRule(Map<String,Object> b,Long userId){
        String code=req(b,"ruleCode").toUpperCase(),name=req(b,"ruleName"),type=enumv(b,"objectType",TYPES),metric=req(b,"metricCode").toUpperCase(),op=enumv(b,"operator",OPS),severity=enumv(b,"severity",SEVERITIES);
        Object threshold=b.get("threshold");if(threshold==null)throw err("ALARM_THRESHOLD_REQUIRED","阈值不能为空");
        int duration=intv(b.get("durationSeconds"),0),consecutive=Math.max(1,intv(b.get("consecutiveCount"),1));Integer repeat=nullableInt(b.get("repeatIntervalSeconds"));boolean notifyRecovery=bool(b.get("notifyRecovery"),true);List<String> channels=channels(b.get("notificationChannels"));
        try{Long id=ruleWriter.create(code,name,type,metric,op,j(thresholdEnvelope(threshold)),duration,consecutive,severity,j(channels),notifyRecovery,repeat,userId);return decorateRule(need(db.rule(id),"ALARM_RULE_NOT_FOUND","规则不存在"));}
        catch(DataIntegrityViolationException e){throw err("ALARM_RULE_CODE_EXISTS","告警规则编码已存在或参数违反约束");}
    }
    public Map<String,Object> updateRule(Long id,Map<String,Object> b,Long userId){Map<String,Object> old=need(db.rule(id),"ALARM_RULE_NOT_FOUND","规则不存在");String name=str(b.getOrDefault("ruleName",old.get("rule_name"))),type=enumRaw(str(b.getOrDefault("objectType",old.get("object_type"))),TYPES,"objectType"),metric=str(b.getOrDefault("metricCode",old.get("metric_code"))).toUpperCase(),op=enumRaw(str(b.getOrDefault("operator",old.get("operator"))),OPS,"operator"),severity=enumRaw(str(b.getOrDefault("severity",old.get("severity"))),SEVERITIES,"severity");Object threshold=b.containsKey("threshold")?b.get("threshold"):thresholdValue(old.get("threshold_json"));int duration=intv(b.getOrDefault("durationSeconds",old.get("duration_seconds")),0),consecutive=Math.max(1,intv(b.getOrDefault("consecutiveCount",old.get("consecutive_count")),1));Integer repeat=b.containsKey("repeatIntervalSeconds")?nullableInt(b.get("repeatIntervalSeconds")):nullableInt(old.get("repeat_interval_seconds"));boolean notifyRecovery=b.containsKey("notifyRecovery")?bool(b.get("notifyRecovery"),true):bool(old.get("notify_recovery"),true);List<String> channels=b.containsKey("notificationChannels")?channels(b.get("notificationChannels")):channelsFromJson(old.get("notification_channels_json"));db.updateRule(id,name,type,metric,op,j(thresholdEnvelope(threshold)),duration,consecutive,severity,j(channels),notifyRecovery,repeat,userId);return decorateRule(need(db.rule(id),"ALARM_RULE_NOT_FOUND","规则不存在"));}
    public Map<String,Object> setRuleStatus(Long id,String status,Long userId){String s=status==null?"":status.toUpperCase();if(!Set.of("ACTIVE","DISABLED").contains(s))throw err("ALARM_RULE_STATUS_INVALID","规则状态仅支持 ACTIVE/DISABLED");if(db.setRuleStatus(id,s,userId)==0)throw err("ALARM_RULE_NOT_FOUND","规则不存在");return decorateRule(db.rule(id));}
    public void deleteRule(Long id,Long userId){if(db.softDeleteRule(id,userId)==0)throw err("ALARM_RULE_NOT_FOUND","规则不存在");}

    public Map<String,Object> acknowledge(Long id,Long userId,String remark){return transition(id,Set.of("OPEN"),"ACKNOWLEDGED","ACKNOWLEDGE",userId,remark);}
    public Map<String,Object> startProcessing(Long id,Long userId,String remark){return transition(id,Set.of("OPEN","ACKNOWLEDGED"),"PROCESSING","START_PROCESS",userId,remark);}
    public Map<String,Object> close(Long id,Long userId,String remark){return transition(id,Set.of("OPEN","ACKNOWLEDGED","PROCESSING","RECOVERED"),"CLOSED","CLOSE",userId,remark);}
    public Map<String,Object> addNote(Long id,Long userId,String remark){Map<String,Object> e=need(db.alarm(id),"ALARM_NOT_FOUND","告警不存在");if(remark==null||remark.isBlank())throw err("ALARM_REMARK_REQUIRED","处理备注不能为空");db.addAlarmProcess(id,"ADD_NOTE",str(e.get("status")),str(e.get("status")),userId,remark.trim());return alarm(id);}

    private Map<String,Object> transition(Long id,Set<String> allowed,String to,String action,Long userId,String remark){Map<String,Object> e=need(db.alarm(id),"ALARM_NOT_FOUND","告警不存在");String before=str(e.get("status"));if(!allowed.contains(before))throw err("ALARM_STATUS_INVALID","当前告警状态不允许执行此操作");String csv=String.join(",",allowed);if(db.changeAlarmStatus(id,csv,to,userId)==0)throw err("ALARM_CONCURRENT_CHANGED","告警状态已变化，请刷新后重试");db.addAlarmProcess(id,action,before,to,userId,remark==null?null:remark.trim());return alarm(id);}
    private Map<String,Object> decorateRule(Map<String,Object> r){if(r==null)return null;Map<String,Object> m=new LinkedHashMap<>(r);m.put("threshold",thresholdValue(r.get("threshold_json")));m.put("notification_channels",channelsFromJson(r.get("notification_channels_json")));return m;}
    private Object thresholdEnvelope(Object x){if(x instanceof Map<?,?> m&&(m.containsKey("value")||m.containsKey("values")))return x;if(x instanceof Collection<?>)return Map.of("values",x);return Map.of("value",x);}
    private Object thresholdValue(Object raw){try{Map<String,Object> m=json.readValue(str(raw),new TypeReference<Map<String,Object>>(){});return m.containsKey("values")?m.get("values"):m.get("value");}catch(Exception e){return null;}}
    private List<String> channels(Object x){List<String> in=new ArrayList<>();if(x instanceof Collection<?> c)for(Object v:c)in.add(str(v).toUpperCase());else if(x!=null&&!str(x).isBlank())for(String v:str(x).split(","))in.add(v.trim().toUpperCase());if(in.isEmpty())in.add("PLATFORM");for(String v:in)if(!CHANNELS.contains(v))throw err("ALARM_CHANNEL_INVALID","不支持的通知通道: "+v);return in.stream().distinct().toList();}
    private List<String> channelsFromJson(Object raw){try{return json.readValue(str(raw),new TypeReference<List<String>>(){});}catch(Exception e){return List.of("PLATFORM");}}
    private String req(Map<String,Object>b,String k){String v=str(b.get(k)).trim();if(v.isEmpty())throw err("ALARM_RULE_FIELD_REQUIRED",k+"不能为空");return v;}
    private String enumv(Map<String,Object>b,String k,Set<String>s){return enumRaw(req(b,k).toUpperCase(),s,k);}
    private String enumRaw(String v,Set<String>s,String k){String x=v==null?"":v.trim().toUpperCase();if(!s.contains(x))throw err("ALARM_RULE_FIELD_INVALID",k+"取值无效");return x;}
    private <T> T need(T x,String c,String m){if(x==null)throw err(c,m);return x;}
    private BusinessException err(String c,String m){return new BusinessException(c,m);}
    private String j(Object x){try{return json.writeValueAsString(x);}catch(Exception e){throw new IllegalStateException(e);}}
    private String str(Object x){return x==null?"":String.valueOf(x);}
    private int intv(Object x,int d){if(x==null||str(x).isBlank())return d;return x instanceof Number n?n.intValue():Integer.parseInt(str(x));}
    private Integer nullableInt(Object x){if(x==null||str(x).isBlank())return null;return intv(x,0);}
    private boolean bool(Object x,boolean d){if(x==null)return d;return x instanceof Boolean b?b:Boolean.parseBoolean(str(x));}
}
