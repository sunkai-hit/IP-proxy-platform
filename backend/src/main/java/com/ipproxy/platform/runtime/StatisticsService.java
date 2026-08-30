package com.ipproxy.platform.runtime;

import org.springframework.stereotype.Service;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class StatisticsService {
    private final RuntimeOperationsRepository db;
    public StatisticsService(RuntimeOperationsRepository db){this.db=db;}

    public synchronized Map<String,Object> recalculate(OffsetDateTime requestedHour,String triggerType,Long userId){
        OffsetDateTime hour=(requestedHour==null?OffsetDateTime.now(ZoneOffset.UTC):requestedHour.withOffsetSameInstant(ZoneOffset.UTC)).truncatedTo(ChronoUnit.HOURS);OffsetDateTime end=hour.plusHours(1);
        String trigger=Set.of("MANUAL","SCHEDULED","CI").contains(triggerType)?triggerType:"MANUAL";Long runId=db.startStatRun(hour,trigger,userId);int r=0,ip=0,cu=0,ps=0,su=0;
        try{r=db.calculateResource(hour,end);ip=db.calculateIp(hour,end);cu=db.calculateCustomerUsage(hour,end);ps=db.calculateProductService(hour,end);su=db.calculateSupplier(hour,end);db.finishStatRun(runId,"SUCCESS",r,ip,cu,ps,su,null);return Map.of("runId",runId,"status","SUCCESS","statHour",hour,"resourceRows",r,"ipRows",ip,"customerUsageRows",cu,"productServiceRows",ps,"supplierRows",su);}
        catch(Exception e){db.finishStatRun(runId,"FAILURE",r,ip,cu,ps,su,cut(e.getMessage(),1900));throw e;}
    }
    public Map<String,Object> overview(){Map<String,Object> m=new LinkedHashMap<>(db.dashboard());m.put("lastCalculation",db.lastStatRun());return m;}
    public List<Map<String,Object>> resource(OffsetDateTime from,OffsetDateTime to){Range r=range(from,to);return db.resourceStats(r.from,r.to);}
    public List<Map<String,Object>> ip(OffsetDateTime from,OffsetDateTime to){Range r=range(from,to);return db.ipStats(r.from,r.to);}
    public List<Map<String,Object>> customers(OffsetDateTime from,OffsetDateTime to){Range r=range(from,to);return db.customerStats(r.from,r.to);}
    public List<Map<String,Object>> products(OffsetDateTime from,OffsetDateTime to){Range r=range(from,to);return db.productStats(r.from,r.to);}
    public List<Map<String,Object>> suppliers(OffsetDateTime from,OffsetDateTime to){Range r=range(from,to);return db.supplierStats(r.from,r.to);}
    public Map<String,Object> dashboard(){return db.dashboard();}
    private Range range(OffsetDateTime from,OffsetDateTime to){OffsetDateTime end=(to==null?OffsetDateTime.now(ZoneOffset.UTC).plusHours(1):to.withOffsetSameInstant(ZoneOffset.UTC));OffsetDateTime start=(from==null?end.minusHours(24):from.withOffsetSameInstant(ZoneOffset.UTC));if(!start.isBefore(end))throw new IllegalArgumentException("from必须早于to");if(Duration.between(start,end).toDays()>366)throw new IllegalArgumentException("统计查询范围不能超过366天");return new Range(start,end);}
    private record Range(OffsetDateTime from,OffsetDateTime to){}
    private String cut(String x,int n){if(x==null)return null;return x.length()<=n?x:x.substring(0,n);}
}
