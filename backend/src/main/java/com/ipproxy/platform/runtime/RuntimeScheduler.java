package com.ipproxy.platform.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
public class RuntimeScheduler {
    private static final Logger log=LoggerFactory.getLogger(RuntimeScheduler.class);
    private final MonitorService monitor;private final StatisticsService statistics;
    private final boolean monitorEnabled,statisticsEnabled;
    public RuntimeScheduler(MonitorService monitor,StatisticsService statistics,@Value("${app.monitor.scheduled-enabled:true}") boolean monitorEnabled,@Value("${app.statistics.scheduled-enabled:true}") boolean statisticsEnabled){this.monitor=monitor;this.statistics=statistics;this.monitorEnabled=monitorEnabled;this.statisticsEnabled=statisticsEnabled;}
    @Scheduled(fixedDelayString="${app.monitor.collect-interval-ms:60000}",initialDelayString="${app.monitor.initial-delay-ms:15000}")
    public void collect(){if(!monitorEnabled)return;try{monitor.collect("SCHEDULED",null);}catch(Exception e){log.error("M8 scheduled monitor collection failed",e);}}
    @Scheduled(cron="${app.statistics.cron:0 5 * * * *}",zone="UTC")
    public void calculate(){if(!statisticsEnabled)return;try{statistics.recalculate(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1),"SCHEDULED",null);}catch(Exception e){log.error("M8 scheduled statistics calculation failed",e);}}
}
