package com.ipproxy.platform.longmonitor;

import java.util.Map;

/** 长效代理真实可用性检测适配边界。 */
public interface LongProxyCheckAdapter {
    CheckResult check(Map<String,Object> monitor);
    record CheckResult(String status,Integer latencyMs,String outboundIp,Integer responseCode,String errorType,String message,Map<String,Object> detail){
        public static CheckResult normal(Integer latency,String outboundIp,Integer responseCode){return new CheckResult("NORMAL",latency,outboundIp,responseCode,null,"检测正常",Map.of());}
        public static CheckResult abnormal(String type,String message){return new CheckResult("ABNORMAL",null,null,null,type,message,Map.of());}
        public static CheckResult unknown(String message){return new CheckResult("UNKNOWN",null,null,null,"ADAPTER_NOT_CONFIGURED",message,Map.of());}
    }
}
