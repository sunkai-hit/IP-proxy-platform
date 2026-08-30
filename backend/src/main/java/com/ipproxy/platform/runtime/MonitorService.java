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
    private final MonitorProbeAdapter probe;
    private final AlarmEvaluationService alarms;
    private final ObjectMapper json;
    private final long staleSeconds;
    public MonitorService(RuntimeOperationsRepository db,MonitorProbeAdapter probe,AlarmEvaluationService alarms,ObjectMapper json,@Value("${app.monitor.stale-seconds:300}") long staleSeconds){this.db=db;this.probe=probe;this.alarms=alarms;this.json=json;this.staleSeconds=Math.max(30,staleSeconds);}

    public synchronized Map<String,Object> collect(String triggerType,Long userId){
        String trigger=Set.of("MANUAL","SCHEDULED","CI").contains(triggerType)?triggerType:"MANUAL";
        Long runId=db.startCollect(trigger,userId);int objects=0,samples=0,triggered=0,recovered=0;
        try{
            for(String type:List.of("CENTOS","ROS","LINE","IP","POOL","SUPPLIER","SERVICE")){
                for(Map<String,Object> row:targets(type)){
                    objects++;Long id=lv(row.get("object_id"));String name=s(row.get("object_name"));
                    Map<String,Object> metrics=metrics(type,row);
                    Map<String,Object> supplemental=probe.probe(type,Collections.unmodifiableMap(row));
                    if(supplemental!=null)for(var e:supplemental.entrySet())metrics.put(normalizeMetric(e.getKey()),e.getValue());
                    String status=effectiveStatus(type,s(row.get("source_status")),row.get("source_at"));
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
        for(String t:List.of("CENTOS","ROS","LINE","IP","POOL","SUPPLIER","SERVICE")){
            long total=rows.stream().filter(x->t.equals(s(x.get("object_type")))).count();
            long healthy=rows.stream().filter(x->t.equals(s(x.get("object_type")))&&healthy(s(x.get("display_status")))).count();
            long abnormal=rows.stream().filter(x->t.equals(s(x.get("object_type")))&&Set.of("ABNORMAL","OFFLINE").contains(s(x.get("display_status")))).count();
            long unknown=rows.stream().filter(x->t.equals(s(x.get("object_type")))&&"UNKNOWN".equals(s(x.get("display_status")))).count();
            types.put(t,Map.of("total",total,"healthy",healthy,"abnormal",abnormal,"unknown",unknown));
        }
        out.put("types",types);out.put("lastCollect",db.lastCollect());out.put("staleSeconds",staleSeconds);return out;
    }

    public PageResult<Map<String,Object>> objects(int page,int size,String objectType,String keyword){int p=Math.max(1,page),s=Math.min(200,Math.max(1,size));String type=objectType==null?"":objectType.trim().toUpperCase();long total=db.monitorCount(type,keyword);return new PageResult<>(p,s,total,decorate(db.monitorRows(type,keyword,s,(p-1)*s)));}
    public List<Map<String,Object>> metrics(String type,Long id,String code,int hours){int h=Math.min(168,Math.max(1,hours));return db.metricHistory(type.toUpperCase(),id,code==null?"":code.trim().toUpperCase(),OffsetDateTime.now(ZoneOffset.UTC).minusHours(h));}

    private List<Map<String,Object>> decorate(List<Map<String,Object>> rows){List<Map<String,Object>> out=new ArrayList<>();OffsetDateTime cutoff=OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(staleSeconds);for(Map<String,Object> row:rows){Map<String,Object> m=new LinkedHashMap<>(row);OffsetDateTime t=offset(row.get("last_collected_at"));boolean stale=t==null||t.isBefore(cutoff);m.put("stale",stale);m.put("display_status",stale?"UNKNOWN":row.get("status"));out.add(m);}return out;}
    private List<Map<String,Object>> targets(String type){return switch(type){case "CENTOS"->db.centosTargets();case "ROS"->db.rosTargets();case "LINE"->db.lineTargets();case "IP"->db.ipTargets();case "POOL"->db.poolTargets();case "SUPPLIER"->db.supplierTargets();case "SERVICE"->db.serviceTargets();default->List.of();};}
    private Map<String,Object> metrics(String type,Map<String,Object> r){Map<String,Object> m=new LinkedHashMap<>();switch(type){
        case "CENTOS"->{put(m,"CPU_USAGE",r.get("cpu_usage"));put(m,"MEMORY_USAGE",r.get("memory_usage"));put(m,"DISK_USAGE",r.get("disk_usage"));put(m,"NETWORK_IN_BPS",r.get("network_in_bps"));put(m,"NETWORK_OUT_BPS",r.get("network_out_bps"));}
        case "ROS"->{put(m,"LINE_TOTAL",r.get("line_total"));put(m,"LINE_ONLINE",r.get("line_online"));put(m,"LINE_ABNORMAL",r.get("line_abnormal"));put(m,"CURRENT_IP_COUNT",r.get("current_ip_count"));}
        case "LINE"->{put(m,"LATENCY_MS",r.get("latency_ms"));put(m,"AVAILABILITY_RATE",r.get("availability_rate"));m.put("DIAL_STATUS",s(r.get("dial_status")));m.put("DIAL_CONNECTED", "CONNECTED".equals(s(r.get("dial_status")))?1:0);}
        case "IP"->{put(m,"LATENCY_MS",r.get("latency_ms"));put(m,"QUALITY_SCORE",r.get("quality_score"));m.put("DUPLICATE_FLAG",Boolean.TRUE.equals(r.get("duplicate_flag"))?1:0);}
        case "POOL"->{put(m,"IP_TOTAL",r.get("ip_total"));put(m,"IP_AVAILABLE",r.get("ip_available"));put(m,"IP_UNAVAILABLE",r.get("ip_unavailable"));double total=num(r.get("ip_total"),0),available=num(r.get("ip_available"),0);m.put("IP_AVAILABLE_RATE",total<=0?0:available*100.0/total);}
        case "SUPPLIER"->{m.put("INTERFACE_STATUS",s(r.get("interface_status")));m.put("INTERFACE_UP","NORMAL".equals(s(r.get("interface_status")))?1:0);}
        case "SERVICE"->{put(m,"API_REQUESTS_5M",r.get("api_requests_5m"));put(m,"API_FAILURES_5M",r.get("api_failures_5m"));put(m,"RETURNED_IPS_5M",r.get("returned_ips_5m"));double req=num(r.get("api_requests_5m"),0),fail=num(r.get("api_failures_5m"),0);m.put("API_FAILURE_RATE",req<=0?0:fail*100.0/req);m.put("SERVICE_ACTIVE","ACTIVE".equals(s(r.get("source_status")))?1:0);}
    }return m;}
    private String effectiveStatus(String type,String source,Object sourceAt){String mapped=switch(type){case "IP"->switch(source){case "AVAILABLE"->"NORMAL";case "UNAVAILABLE"->"ABNORMAL";case "DISABLED"->"OFFLINE";default->"UNKNOWN";};case "POOL"->switch(source){case "ACTIVE"->"NORMAL";case "ABNORMAL"->"ABNORMAL";case "DISABLED"->"OFFLINE";default->"UNKNOWN";};case "SUPPLIER"->switch(source){case "NORMAL"->"NORMAL";case "ABNORMAL"->"ABNORMAL";case "DISABLED"->"OFFLINE";default->"UNKNOWN";};case "SERVICE"->switch(source){case "ACTIVE"->"NORMAL";case "PROVISION_FAILED"->"ABNORMAL";case "SUSPENDED","EXPIRED","TERMINATED"->"OFFLINE";default->"UNKNOWN";};default->switch(source){case "ONLINE"->"ONLINE";case "OFFLINE"->"OFFLINE";case "ABNORMAL"->"ABNORMAL";default->"UNKNOWN";};};OffsetDateTime t=offset(sourceAt);if(t!=null&&t.isBefore(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(Math.max(staleSeconds,300))))return "UNKNOWN";return mapped;}
    private boolean healthy(String s){return "NORMAL".equals(s)||"ONLINE".equals(s);}
    private Double health(String s){return healthy(s)?100d:"ABNORMAL".equals(s)?50d:"OFFLINE".equals(s)?0d:null;}
    private void put(Map<String,Object> m,String k,Object v){if(v!=null)m.put(k,v);}
    private String normalizeMetric(String k){return k==null?"UNKNOWN":k.trim().toUpperCase().replace('-','_');}
    private String j(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException(e);}}
    private Long lv(Object x){return x instanceof Number n?n.longValue():Long.valueOf(s(x));}
    private double num(Object x,double d){if(x==null)return d;if(x instanceof Number n)return n.doubleValue();try{return Double.parseDouble(s(x));}catch(Exception e){return d;}}
    private String s(Object x){return x==null?"":String.valueOf(x);}
    private OffsetDateTime offset(Object x){if(x==null)return null;if(x instanceof OffsetDateTime o)return o;if(x instanceof java.sql.Timestamp t)return t.toInstant().atOffset(ZoneOffset.UTC);if(x instanceof java.time.LocalDateTime l)return l.atOffset(ZoneOffset.UTC);try{return OffsetDateTime.parse(s(x));}catch(Exception e){return null;}}
    private String cut(String x,int n){if(x==null)return null;return x.length()<=n?x:x.substring(0,n);}
}
