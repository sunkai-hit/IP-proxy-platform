package com.ipproxy.platform.runtime;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@Profile("!m8-mock")
public class NoopMonitorProbeAdapter implements MonitorProbeAdapter {
    @Override public Map<String,Object> probe(String objectType,Map<String,Object> objectContext){return Map.of();}
}
