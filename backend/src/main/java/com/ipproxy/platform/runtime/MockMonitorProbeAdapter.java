package com.ipproxy.platform.runtime;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.util.*;

/** 仅用于 M8 CI / 本地联调，禁止在正式环境作为真实监控数据。 */
@Component
@Profile("m8-mock")
public class MockMonitorProbeAdapter implements MonitorProbeAdapter {
    @Override public Map<String,Object> probe(String objectType,Map<String,Object> ctx){
        Map<String,Object> out=new LinkedHashMap<>();
        long id=((Number)ctx.getOrDefault("object_id",0)).longValue();
        if("LINE".equals(objectType))out.put("active_probe_latency_ms",id%2==0?46:26);
        if("CENTOS".equals(objectType))out.put("active_probe_available",1);
        out.put("probe_source","M8_MOCK");
        return out;
    }
}
