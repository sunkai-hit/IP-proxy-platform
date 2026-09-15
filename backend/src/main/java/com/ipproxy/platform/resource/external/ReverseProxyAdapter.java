package com.ipproxy.platform.resource.external;

import java.util.Map;

/**
 * 外部供应商资源通过反向代理交付的数据面适配边界。
 * 真实环境可在此对接 Nginx/OpenResty/HAProxy/自研代理服务；管理平台不直接承担代理转发。
 */
public interface ReverseProxyAdapter {
    Result apply(Long reverseProxyId,Map<String,Object> config);
    Result test(Long reverseProxyId,Map<String,Object> config);
    Result disable(Long reverseProxyId,Map<String,Object> config);
    record Result(boolean success,String message,Map<String,Object> detail){
        public static Result ok(String message){return new Result(true,message,Map.of());}
        public static Result pending(String message){return new Result(true,message,Map.of("adapter","NOT_CONFIGURED"));}
        public static Result fail(String message){return new Result(false,message,Map.of());}
    }
}
