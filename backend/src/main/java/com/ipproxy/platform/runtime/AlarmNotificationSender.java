package com.ipproxy.platform.runtime;

public interface AlarmNotificationSender {
    record Result(String result,String target,String errorMessage){}
    Result send(String channel,String message);
}
