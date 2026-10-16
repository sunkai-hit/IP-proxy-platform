package com.ipproxy.platform.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipproxy.platform.common.api.PageResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
public class MonitorService {
    private final RuntimeOperationsRepository db;
    private final InfrastructureMonitorRepository infrastructure;
    private final MonitorProbeAdapter probe;
    private final AlarmEvaluationService alarms;
    private final ObjectMapper json;
    private final long staleSeconds;

    public MonitorService(RuntimeOperationsRepository db,InfrastructureMonitorRepository infrastructure,MonitorProbeAdapter probe,AlarmEvaluationService alarms,ObjectMapper json,@Value("${app.monitor.stale-seconds:300}") long staleSeconds){
        this.db=db;this.infrastructure=infrastructure;this.probe=probe;this.alarms=alarms;this.json=json;this.staleSeconds=Math.max(30,staleSeconds);
    }

    public synchronized Map<String,Object> collect(String triggerType,Long userId){
        String trigger=Set.of("MANUAL","SCHEDULED","CI").contains(triggerType)?triggerType:"MANUAL";
        Long runId=db.startCollect(trigger,userId);int objects=0,samples=0,triggered=0,recovered=0;
        try{
            for(String type:List.of("CENTOS","ROS")){
                for(Map<String,Object> row:targets(type)){
                    objects++;Long id=lv(row.get("object_id"));String name=s(row.get("object_name"));
                    Map<String,Object> metrics=metrics(type,row);
                    Map<String,Object> supplemental=probe.probe(type,Collections.unmodifiableMap(row));
                    if(supplemental!=null)for(var e:supplemental.entrySet())metrics.put(normalizeMetric(e.getKey()),e.getValue());
                    String status=effectiveStatus(s(row.get("source_status")),row.get("source_at"));
                    metrics.put("SOURCE_STATUS",status);
                    metrics.put("STATUS_VALUE",healthy(status)?1:0);
                    boolean success=!"UNKNOWN".equals(status);
                    db.upsertObjectStatus(type,id,status,health(status),j(metrics),success,"UNKNOWN".equals(status)?"上游状态未知或数据已过期":null);
                    OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
                    for(var e:metrics.entrySet()){
                        Object v=e.getValue();if(v==null)continue;
                        if(v instanceof Number n)db.insertMetric(type,id,e.getKey(),n,null,"{}",now);
                        else if(v instanceof Boolean b)db.insertMetric(type,id,e.getKey(),b?1:0,String.valueOf(b),"{}",now);
                        else db.insertMetric(type,id,e.getKey(),null,String.valueOf(v),"{}",now);
                        samples++;
                    }
                    AlarmEvaluationService.Result er=alarms.evaluate(type,id,name,metrics);triggered+=er.triggered();recovered+=er.recovered();
                }
            }
            db.finishCollect(runId,"SUCCESS",objects,samples,triggered,recovered,null);
            return Map.of("runId",runId,"status","SUCCESS","objectCount",objects,"sampleCount",samples,"alarmTriggerCount",triggered,"alarmRecoveryCount",recovered);
        }catch(Exception e){db.finishCollect(runId,"FAILURE",objects,samples,triggered,recovered,cut(e.getMessage(),1900));throw e;}
    }

    public Map<String,Object> overview(){
        List<Map<String,Object>> rows=decorate(db.monitorRows("","",10000,0));
        Map<String,Object> out=new LinkedHashMap<>();Map<String,Object> types=new LinkedHashMap<>();
        for(String t:List.of("CENTOS","ROS")){
            long total=rows.stream().filter(x->t.equals(s(x.get("object_type")))).count();
            long healthy=rows.stream().filter(x->t.equals(s(x.get("object_type")))&&healthy(s(x.get("display_status")))).count();
            long abnormal=rows.stream().filter(x->t.equals(s(x.get("object_type")))&&Set.of("ABNORMAL","OFFLINE").contains(s(x.get("display_status")))).count();
            long unknown=rows.stream().filter(x->t.equals(s(x.get("object_type")))&&"UNKNOWN".equals(s(x.get("display_status")))).count();
            types.put(t,Map.of("total",total,"healthy",healthy,"abnormal",abnormal,"unknown",unknown));
        }
        out.put("types",types);out.put("lastCollect",db.lastCollect());out.put("staleSeconds",staleSeconds);return out;
    }

    public PageResult<Map<String,Object>> objects(int page,int size,String objectType,String keyword){
        int p=Math.max(1,page),s=Math.min(200,Math.max(1,size));String type=objectType==null?"":objectType.trim().toUpperCase();
        if(!type.isBlank()&&!Set.of("CENTOS","ROS").contains(type))return new PageResult<>(p,s,0,List.of());
        long total=db.monitorCount(type,keyword);return new PageResult<>(p,s,total,decorate(db.monitorRows(type,keyword,s,(p-1)*s)));
    }
    public List<Map<String,Object>> metrics(String type,Long id,String code,int hours){
        String t=type==null?"":type.trim().toUpperCase();if(!Set.of("CENTOS","ROS").contains(t))return List.of();
        int h=Math.min(168,Math.max(1,hours));return db.metricHistory(t,id,code==null?"":code.trim().toUpperCase(),OffsetDateTime.now(ZoneOffset.UTC).minusHours(h));
    }

    private List<Map<String,Object>> decorate(List<Map<String,Object>> rows){
        List<Map<String,Object>> out=new ArrayList<>();OffsetDateTime cutoff=OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(staleSeconds);
        for(Map<String,Object> row:rows){
            if(!Set.of("CENTOS","ROS").contains(s(row.get("object_type"))))continue;
            Map<String,Object> m=new LinkedHashMap<>(row);OffsetDateTime t=offset(row.get("last_collected_at"));boolean stale=t==null||t.isBefore(cutoff);
            m.put("stale",stale);m.put("display_status",stale?"UNKNOWN":row.get("status"));out.add(m);
        }
        return out;
    }

    private List<Map<String,Object>> targets(String type){return switch(type){case "CENTOS"->infrastructure.centosTargets();case "ROS"->infrastructure.rosTargets();default->List.of();};}

    private Map<String,Object> metrics(String type,Map<String,Object> r){
        Map<String,Object> m=new LinkedHashMap<>();
        if("CENTOS".equals(type)){
            put(m,"CPU_USAGE",r.get("cpu_usage"));put(m,"MEMORY_USAGE",r.get("memory_usage"));put(m,"SYSTEM_LOAD",r.get("system_load"));put(m,"DISK_USAGE",r.get("disk_usage"));
            put(m,"B_PREFIX_COUNT",r.get("b_prefix_count"));put(m,"C_PREFIX_COUNT",r.get("c_prefix_count"));put(m,"NETWORK_IN_BPS",r.get("network_in_bps"));put(m,"NETWORK_OUT_BPS",r.get("network_out_bps"));
        }else if("ROS".equals(type)){
            put(m,"CPU_USAGE",r.get("cpu_usage"));put(m,"MEMORY_USAGE",r.get("memory_usage"));put(m,"SYSTEM_LOAD",r.get("system_load"));put(m,"DISK_USAGE",r.get("disk_usage"));
            put(m,"LINE_TOTAL",r.get("line_total"));put(m,"LINE_ONLINE",r.get("line_online"));put(m,"LINE_ABNORMAL",r.get("line_abnormal"));put(m,"CURRENT_IP_COUNT",r.get("current_ip_count"));put(m,"BAS_COUNT",r.get("bas_count"));
        }
        return m;
    }

    private String effectiveStatus(String source,Object sourceAt){
        String mapped=switch(source){case "ONLINE"->"ONLINE";case "OFFLINE"->"OFFLINE";case "ABNORMAL"->"ABNORMAL";default->"UNKNOWN";};
        OffsetDateTime t=offset(sourceAt);if(t!=null&&t.isBefore(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(Math.max(staleSeconds,300))))return "UNKNOWN";return mapped;
    }
    private boolean healthy(String s){return "NORMAL".equals(s)||"ONLINE".equals(s);}
    private Double health(String s){return healthy(s)?100d:"ABNORMAL".equals(s)?50d:"OFFLINE".equals(s)?0d:null;}
    private void put(Map<String,Object> m,String k,Object v){if(v!=null)m.put(k,v);}
    private String normalizeMetric(String k){return k==null?"UNKNOWN":k.trim().toUpperCase().replace('-','_');}
    private String j(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException(e);}}
    private Long lv(Object x){return x instanceof Number n?n.longValue():Long.valueOf(s(x));}
    private String s(Object x){return x==null?"":String.valueOf(x);}
    private OffsetDateTime offset(Object x){if(x==null)return null;if(x instanceof OffsetDateTime o)return o;if(x instanceof java.sql.Timestamp t)return t.toInstant().atOffset(ZoneOffset.UTC);if(x instanceof java.time.LocalDateTime l)return l.atOffset(ZoneOffset.UTC);try{return OffsetDateTime.parse(s(x));}catch(Exception e){return null;}}
    private String cut(String x,int n){if(x==null)return null;return x.length()<=n?x:x.substring(0,n);}
}
