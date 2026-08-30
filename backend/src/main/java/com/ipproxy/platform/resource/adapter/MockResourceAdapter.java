package com.ipproxy.platform.resource.adapter;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.util.*;

/** 仅用于M4 CI/本地联调，不代表真实运维或供应商接口字段。 */
@Component
@Profile("m4-mock")
public class MockResourceAdapter implements ResourceAdapter {
    @Override public List<Map<String,Object>> fetch(String sourceType,Map<String,Object> supplier){
        return switch(sourceType){
            case "CENTOS" -> List.of(
                row("sourceId","c-001","resourceName","CI-CentOS-01","managementIp","10.10.0.11","regionCode","MOCK-R1","onlineStatus","ONLINE","cpuUsage",21.5,"memoryUsage",38.2,"diskUsage",45.0),
                row("sourceId","c-002","resourceName","CI-CentOS-02","managementIp","10.10.0.12","regionCode","MOCK-R1","onlineStatus","ONLINE","cpuUsage",18.1,"memoryUsage",42.0,"diskUsage",51.0));
            case "ROS" -> List.of(
                row("sourceId","r-001","parentSourceId","c-001","resourceName","CI-ROS-01","managementIp","10.20.0.11","regionCode","MOCK-R1","carrierCode","UNICOM","onlineStatus","ONLINE"),
                row("sourceId","r-002","parentSourceId","c-002","resourceName","CI-ROS-02","managementIp","10.20.0.12","regionCode","MOCK-R1","carrierCode","TELECOM","onlineStatus","ONLINE"));
            case "LINE" -> List.of(
                row("sourceId","l-001","parentSourceId","r-001","resourceName","CI-Line-01","regionCode","MOCK-R1","carrierCode","UNICOM","broadbandAccountMask","ci***001","currentPublicIp","198.51.100.11","onlineStatus","ONLINE","dialStatus","CONNECTED","latencyMs",28),
                row("sourceId","l-002","parentSourceId","r-002","resourceName","CI-Line-02","regionCode","MOCK-R1","carrierCode","TELECOM","broadbandAccountMask","ci***002","currentPublicIp","198.51.100.12","onlineStatus","ONLINE","dialStatus","CONNECTED","latencyMs",34));
            case "IP" -> List.of(
                row("sourceId","ip-001","parentSourceId","l-001","ipAddress","198.51.100.11","regionCode","MOCK-R1","carrierCode","UNICOM","availableStatus","AVAILABLE","latencyMs",28,"qualityScore",95.5),
                row("sourceId","ip-002","parentSourceId","l-002","ipAddress","198.51.100.12","regionCode","MOCK-R1","carrierCode","TELECOM","availableStatus","AVAILABLE","latencyMs",34,"qualityScore",92.0));
            case "SUPPLIER" -> List.of(
                row("sourceId","ext-001","ipAddress","203.0.113.21","regionCode","MOCK-R2","carrierCode","MOBILE","availableStatus","AVAILABLE","latencyMs",46,"qualityScore",88.0),
                row("sourceId","ext-002","ipAddress","203.0.113.22","regionCode","MOCK-R2","carrierCode","UNICOM","availableStatus","AVAILABLE","latencyMs",42,"qualityScore",90.0));
            default -> List.of();
        };
    }
    @Override public SupplierTestResult testSupplier(Map<String,Object> supplierContext){return new SupplierTestResult(true,"M4 Mock适配器连通验证通过");}
    private Map<String,Object> row(Object... values){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)m.put(String.valueOf(values[i]),values[i+1]);return m;}
}
