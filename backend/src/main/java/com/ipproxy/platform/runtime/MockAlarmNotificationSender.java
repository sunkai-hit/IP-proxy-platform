package com.ipproxy.platform.runtime;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("m8-mock")
public class MockAlarmNotificationSender implements AlarmNotificationSender {
    @Override public Result send(String channel,String message){return new Result("SUCCESS","M8_MOCK_"+channel,null);}
}
