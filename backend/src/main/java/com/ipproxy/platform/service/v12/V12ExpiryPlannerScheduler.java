package com.ipproxy.platform.service.v12;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Automatic discovery only: never interpret a scheduled run as successful network suspension. */
@Component
public class V12ExpiryPlannerScheduler {
    private static final Logger log=LoggerFactory.getLogger(V12ExpiryPlannerScheduler.class);
    private final V12ExpiryTaskPlanner planner;
    private final boolean enabled;
    public V12ExpiryPlannerScheduler(V12ExpiryTaskPlanner planner,
            @Value("${app.service-expiry.planner-enabled:true}") boolean enabled){
        this.planner=planner;this.enabled=enabled;
    }
    @Scheduled(fixedDelayString="${app.service-expiry.planner-delay-ms:300000}",
               initialDelayString="${app.service-expiry.planner-initial-delay-ms:60000}")
    public void plan(){
        if(!enabled)return;
        try{
            var result=planner.planDue(200);
            if(((Number)result.get("created")).intValue()>0)
                log.warn("R3 expiry plan created {} blocked tasks. Real stop/release not verified.",
                    result.get("created"));
        }catch(Exception ex){
            log.error("R3 expiry planner failed; resources remain held, no false release",ex);
        }
    }
}
