package com.ipproxy.platform.longmonitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipproxy.platform.common.audit.OperationAuditRepository;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.resource.security.ResourceSecretCipher;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * A5 isolated incident state-machine regression.
 * Adapter responses are synthetic: no network probe or Bot call is performed.
 */
class LongMonitorServiceTest {
    private final LongMonitorMapper db=mock(LongMonitorMapper.class);
    private final LongProxyCheckAdapter checker=mock(LongProxyCheckAdapter.class);
    private final CustomerBotNotificationAdapter notifier=mock(CustomerBotNotificationAdapter.class);
    private final ResourceSecretCipher cipher=mock(ResourceSecretCipher.class);
    private final OperationAuditRepository audit=mock(OperationAuditRepository.class);
    private final LongMonitorService service=new LongMonitorService(db,checker,notifier,cipher,new ObjectMapper(),audit);

    private Map<String,Object> monitor(String status,int failures,int successes,long botId){
        Map<String,Object> m=new HashMap<>();
        m.put("id",41L);m.put("monitor_no","CI-LM-41");
        m.put("customer_id",22L);m.put("service_id",52L);
        m.put("target_host","ci-only.invalid");m.put("target_port",8080);
        m.put("current_status",status);m.put("consecutive_failures",failures);
        m.put("consecutive_successes",successes);
        m.put("failure_threshold",3);m.put("recovery_threshold",2);
        if(botId>0)m.put("bot_id",botId);
        return m;
    }
    private Map<String,Object> open(){return Map.of("id",61L,"status","OPEN");}

    @Test void unknownProbeCannotRecoverAnExistingOpenIncidentOrWriteFalseFailureTimestamp(){
        when(db.monitor(41L)).thenReturn(monitor("ABNORMAL",3,1,0));
        when(db.openAlarm(41L)).thenReturn(open());
        when(checker.check(anyMap())).thenReturn(LongProxyCheckAdapter.CheckResult.unknown("Adapter unavailable"));
        var out=service.checkOne(41L);
        assertEquals("UNKNOWN",out.get("after"));
        assertEquals(0,out.get("consecutiveSuccesses"));
        assertEquals(0,out.get("consecutiveFailures"));
        verify(db).updateMonitorCheck(41L,"UNKNOWN",0,0,"Adapter unavailable",false,false);
        verify(db,never()).recoverAlarm(anyLong(),anyString());
        verify(db,never()).insertAlarm(anyString(),anyLong(),anyLong(),anyLong(),anyString());
        verifyNoInteractions(notifier);
    }

    @Test void firstRealNormalAfterUnknownCannotRecoverOpenIncidentBeforeThreshold(){
        when(db.monitor(41L)).thenReturn(monitor("UNKNOWN",0,0,0));
        when(db.openAlarm(41L)).thenReturn(open());
        when(checker.check(anyMap())).thenReturn(LongProxyCheckAdapter.CheckResult.normal(14,"198.51.100.21",200));
        var result=service.checkOne(41L);
        assertEquals("UNKNOWN",result.get("after"));
        assertEquals(1,result.get("consecutiveSuccesses"));
        verify(db).updateMonitorCheck(41L,"UNKNOWN",0,1,null,true,false);
        verify(db,never()).recoverAlarm(anyLong(),anyString());
        verifyNoInteractions(notifier);
    }

    @Test void onlySecondConsecutiveRealNormalRecoversThePersistedIncident(){
        when(db.monitor(41L)).thenReturn(monitor("UNKNOWN",0,1,0));
        when(db.openAlarm(41L)).thenReturn(open());
        when(db.recoverAlarm(eq(61L),anyString())).thenReturn(1);
        when(checker.check(anyMap())).thenReturn(LongProxyCheckAdapter.CheckResult.normal(9,"198.51.100.21",200));
        var result=service.checkOne(41L);
        assertEquals("NORMAL",result.get("after"));
        assertEquals(2,result.get("consecutiveSuccesses"));
        verify(db).recoverAlarm(eq(61L),anyString());
        verify(db,never()).insertAlarm(anyString(),anyLong(),anyLong(),anyLong(),anyString());
    }

    @Test void confirmedAbnormalAfterUnknownDoesNotDuplicateOpenAlarmOrNotification(){
        when(db.monitor(41L)).thenReturn(monitor("UNKNOWN",0,0,9));
        when(db.openAlarm(41L)).thenReturn(open());
        when(checker.check(anyMap())).thenReturn(LongProxyCheckAdapter.CheckResult.abnormal("TEST_FAILURE","Synthetic outage"));
        var out=service.checkOne(41L);
        assertEquals("ABNORMAL",out.get("after"));
        verify(db,never()).insertAlarm(anyString(),anyLong(),anyLong(),anyLong(),anyString());
        verify(db,never()).insertNotification(any(),anyLong(),anyLong(),anyString(),anyString(),anyString(),anyString(),anyString(),any());
        verifyNoInteractions(notifier);
    }

    @Test void notifierExceptionIsRecordedAsFailedDeliveryWithoutLosingDetectedAlarm(){
        when(db.monitor(41L)).thenReturn(monitor("UNKNOWN",2,0,9));
        when(db.openAlarm(41L)).thenReturn(null);
        when(checker.check(anyMap())).thenReturn(LongProxyCheckAdapter.CheckResult.abnormal("TEST_FAILURE","Synthetic outage"));
        when(db.insertAlarm(anyString(),eq(41L),eq(22L),eq(52L),anyString())).thenReturn(61L);
        when(db.bot(9L)).thenReturn(Map.of("id",9L,"customer_id",22L,"bot_type","DINGTALK","enabled",true));
        when(notifier.send(anyMap(),eq("ALARM"),anyMap())).thenThrow(new IllegalStateException("internal secret webhook token"));
        var result=service.checkOne(41L);
        assertEquals("ABNORMAL",result.get("after"));
        verify(db).insertAlarm(anyString(),eq(41L),eq(22L),eq(52L),anyString());
        verify(db).insertNotification(eq(61L),eq(41L),eq(9L),eq("ALARM"),eq("DINGTALK"),
            anyString(),anyString(),eq("FAILED"),eq("通知适配器执行异常，投递未确认"));
        verify(db).updateMonitorCheck(eq(41L),eq("ABNORMAL"),eq(3),eq(0),anyString(),eq(false),eq(true));
    }

    @Test void invalidManualReasonIsRejectedBeforeAnyBotOrProbeSideEffect(){
        assertThrows(BusinessException.class,()->service.check(41L,"  ",null,"ci"));
        assertThrows(BusinessException.class,()->service.testBot(9L,"",null,"ci"));
        verifyNoInteractions(db,checker,notifier);
    }
}
