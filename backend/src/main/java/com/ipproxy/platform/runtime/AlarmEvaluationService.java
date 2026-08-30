package com.ipproxy.platform.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class AlarmEvaluationService {
    private final RuntimeOperationsRepository db;
    private final AlarmNotificationSender sender;
    private final ObjectMapper json;
    public AlarmEvaluationService(RuntimeOperationsRepository db,AlarmNotificationSender sender,ObjectMapper json){this.db=db;this.sender=sender;this.json=json;}

    public record Result(int triggered,int recovered){}

    public Result evaluate(String objectType,Long objectId,String objectName,Map<String,Object> metrics){
        int triggered=0,recovered=0;
        for(Map<String,Object> rule:db.activeRules()){
            if(!objectType.equals(s(rule.get("object_type"))))continue;
            String metric=s(rule.get("metric_code"));
            if(!metrics.containsKey(metric))continue;
            Object current=metrics.get(metric);
            Object threshold=threshold(rule.get("threshold_json"));
            int consecutive=Math.max(1,intv(rule.get("consecutive_count"),1));
            int duration=Math.max(0,intv(rule.get("duration_seconds"),0));
            boolean qualified=qualified(objectType,objectId,metric,s(rule.get("operator")),threshold,consecutive,duration);
            Map<String,Object> active=db.activeAlarm(lv(rule.get("id")),objectType,objectId,metric);
            if(qualified){
                String currentJson=j(Map.of("value",safe(current)));
                if(active==null){
                    String no="ALM-"+System.currentTimeMillis()+"-"+objectType+"-"+objectId;
                    Long alarmId=db.insertAlarm(lv(rule.get("id")),no,objectType,objectId,objectName,s(rule.get("severity")),metric,s(rule.get("threshold_json")),currentJson);
                    Map<String,Object> event=db.alarm(alarmId);
                    notify(event,rule,false);
                    triggered++;
                }else{
                    Long alarmId=lv(active.get("id"));
                    db.touchAlarm(alarmId,currentJson);
                    Integer repeat=nullableInt(rule.get("repeat_interval_seconds"));
                    if(repeat!=null&&repeat>0&&repeatDue(alarmId,rule,repeat))notify(db.alarm(alarmId),rule,false);
                }
            }else if(active!=null){
                Long alarmId=lv(active.get("id"));
                String before=s(active.get("status"));
                db.recoverAlarm(alarmId,j(Map.of("value",safe(current))));
                db.addAlarmProcess(alarmId,"RECOVER",before,"RECOVERED",null,"监控指标恢复，系统自动恢复告警");
                if(bool(rule.get("notify_recovery"),true))notify(db.alarm(alarmId),rule,true);
                recovered++;
            }
        }
        return new Result(triggered,recovered);
    }

    private boolean qualified(String type,Long objectId,String metric,String operator,Object threshold,int consecutive,int duration){
        int need=Math.max(consecutive,Math.min(120, duration>0?120:consecutive));
        List<Map<String,Object>> samples=db.recentMetricSamples(type,objectId,metric,need);
        if(samples.isEmpty())return false;
        int matched=0;OffsetDateTime oldest=null;
        for(Map<String,Object> sample:samples){
            Object v=sample.get("metric_value")!=null?sample.get("metric_value"):sample.get("metric_text");
            if(!compare(v,operator,threshold))break;
            matched++;Object t=sample.get("collected_at");if(t instanceof OffsetDateTime o)oldest=o;
        }
        if(matched<consecutive)return false;
        if(duration>0){
            if(oldest==null)return false;
            return !oldest.isAfter(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(duration));
        }
        return true;
    }

    private boolean compare(Object current,String op,Object threshold){
        if("IN".equals(op)||"NOT_IN".equals(op)){
            List<?> values=threshold instanceof List<?> l?l:List.of(threshold);
            boolean hit=values.stream().anyMatch(x->eq(current,x));
            return "IN".equals(op)?hit:!hit;
        }
        if("EQ".equals(op))return eq(current,threshold);
        if("NE".equals(op))return !eq(current,threshold);
        Double c=num(current),t=num(threshold);if(c==null||t==null)return false;
        return switch(op){case "GT"->c>t;case "GTE"->c>=t;case "LT"->c<t;case "LTE"->c<=t;default->false;};
    }
    private boolean eq(Object a,Object b){Double an=num(a),bn=num(b);if(an!=null&&bn!=null)return Double.compare(an,bn)==0;return Objects.equals(s(a),s(b));}

    private boolean repeatDue(Long alarmId,Map<String,Object> rule,int repeat){
        for(String channel:channels(rule.get("notification_channels_json"))){OffsetDateTime last=db.lastNotificationAt(alarmId,channel);if(last==null||last.plusSeconds(repeat).isBefore(OffsetDateTime.now(ZoneOffset.UTC)))return true;}
        return false;
    }
    private void notify(Map<String,Object> event,Map<String,Object> rule,boolean recovery){
        if(event==null)return;
        String message=(recovery?"【告警恢复】":"【监控告警】")+"["+s(event.get("severity"))+ "] "+s(event.get("object_name"))+" / "+s(event.get("metric_code"))+"，当前="+value(event,recovery?"recovery_value":"current_value")+"，阈值="+s(event.get("threshold"))+"，告警号="+s(event.get("alarm_no"));
        for(String channel:channels(rule.get("notification_channels_json"))){AlarmNotificationSender.Result r=sender.send(channel,message);db.insertNotification(lv(event.get("id")),channel,r.target(),message,r.result(),r.errorMessage());}
    }
    private String value(Map<String,Object> e,String key){Object v=e.get(key);return v==null?"-":String.valueOf(v);}
    private List<String> channels(Object raw){try{String x=s(raw);if(x.isBlank())return List.of("PLATFORM");return json.readValue(x,new TypeReference<List<String>>(){});}catch(Exception e){return List.of("PLATFORM");}}
    private Object threshold(Object raw){try{Map<String,Object> m=json.readValue(s(raw),new TypeReference<Map<String,Object>>(){});if(m.containsKey("values"))return m.get("values");return m.get("value");}catch(Exception e){return null;}}
    private String j(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException(e);}}
    private Object safe(Object x){return x==null?"":x;}
    private Double num(Object x){if(x==null)return null;if(x instanceof Number n)return n.doubleValue();try{return Double.parseDouble(s(x));}catch(Exception e){return null;}}
    private Long lv(Object x){return x instanceof Number n?n.longValue():Long.valueOf(s(x));}
    private int intv(Object x,int d){if(x==null)return d;return x instanceof Number n?n.intValue():Integer.parseInt(s(x));}
    private Integer nullableInt(Object x){if(x==null||s(x).isBlank())return null;return intv(x,0);}
    private boolean bool(Object x,boolean d){if(x==null)return d;return x instanceof Boolean b?b:Boolean.parseBoolean(s(x));}
    private String s(Object x){return x==null?"":String.valueOf(x);}
}
