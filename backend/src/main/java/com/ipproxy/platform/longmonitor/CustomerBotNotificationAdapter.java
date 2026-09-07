package com.ipproxy.platform.longmonitor;

import java.util.Map;

/** 客户钉钉/飞书机器人通知适配边界。 */
public interface CustomerBotNotificationAdapter {
    Result send(Map<String,Object> bot,String notificationType,Map<String,Object> payload);
    record Result(boolean success,String message,Map<String,Object> response){
        public static Result pending(String message){return new Result(false,message,Map.of("adapter","NOT_CONFIGURED"));}
        public static Result ok(String message,Map<String,Object> response){return new Result(true,message,response==null?Map.of():response);}
    }
}
