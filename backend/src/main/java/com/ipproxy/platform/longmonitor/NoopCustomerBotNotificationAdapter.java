package com.ipproxy.platform.longmonitor;

import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class NoopCustomerBotNotificationAdapter implements CustomerBotNotificationAdapter {
    @Override public Result send(Map<String,Object> bot,String notificationType,Map<String,Object> payload){return Result.pending("客户机器人真实发送Adapter尚未配置");}
}
