package com.ipproxy.platform.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
@Profile("!m8-mock")
public class DefaultAlarmNotificationSender implements AlarmNotificationSender {
    private final ObjectMapper json;
    private final String dingTalkWebhook;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    public DefaultAlarmNotificationSender(ObjectMapper json,@Value("${app.alarm.dingtalk-webhook:}") String dingTalkWebhook){this.json=json;this.dingTalkWebhook=dingTalkWebhook==null?"":dingTalkWebhook.trim();}

    @Override public Result send(String channel,String message){
        if("PLATFORM".equals(channel))return new Result("SUCCESS","PLATFORM",null);
        if("DINGTALK".equals(channel)){
            if(dingTalkWebhook.isBlank())return new Result("FAILURE","DINGTALK","钉钉机器人Webhook未配置");
            try{
                String body=json.writeValueAsString(Map.of("msgtype","text","text",Map.of("content",message)));
                HttpRequest req=HttpRequest.newBuilder(URI.create(dingTalkWebhook)).timeout(Duration.ofSeconds(5)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
                HttpResponse<String> r=http.send(req,HttpResponse.BodyHandlers.ofString());
                if(r.statusCode()>=200&&r.statusCode()<300)return new Result("SUCCESS",mask(dingTalkWebhook),null);
                return new Result("FAILURE",mask(dingTalkWebhook),"钉钉HTTP状态="+r.statusCode());
            }catch(Exception e){return new Result("FAILURE",mask(dingTalkWebhook),e.getMessage());}
        }
        return new Result("FAILURE",channel,"当前通知通道尚未配置发送器");
    }
    private String mask(String v){if(v==null||v.length()<12)return "***";return v.substring(0,8)+"***"+v.substring(v.length()-4);}
}
