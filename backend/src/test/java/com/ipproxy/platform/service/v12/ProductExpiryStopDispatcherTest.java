package com.ipproxy.platform.service.v12;

import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.resource.v12.ProductGroupStopClient;
import com.ipproxy.platform.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductExpiryStopDispatcherTest {
    static class Ledger extends JdbcTemplate {
        Map<String,Object> task;
        List<String> executed=new ArrayList<>();
        List<Object[]> args=new ArrayList<>();
        int claimed=1;
        @Override public List<Map<String,Object>> queryForList(String sql,Object... params){
            assertTrue(sql.contains("svc_action_task"));
            return task==null?List.of():List.of(task);
        }
        @Override public int update(String sql,Object... params){
            executed.add(sql);args.add(params);
            if(sql.contains("DISPATCH_IN_FLIGHT")&&sql.contains("attempt_count"))return claimed;
            return 1;
        }
    }
    static Map<String,Object> due(){
        Map<String,Object> row=new HashMap<>();
        row.put("id",71L);row.put("operation_key","expiry-stop-71");
        row.put("service_id",21L);row.put("action_type","STOP_FOR_GRACE");
        row.put("task_status","PRECHECK_BLOCKED");row.put("remote_operation_id",null);
        row.put("product_type","LONG_IP");row.put("delivery_mode","SELF_OWNED");
        row.put("service_status","ACTIVE");row.put("expiry_phase","STOP_PENDING");
        row.put("grace_days_snapshot",2);
        row.put("expire_at",OffsetDateTime.parse("2020-01-01T00:00:00Z"));
        return row;
    }
    private ProductGroupStopClient ready() {
        ProductGroupStopClient client=mock(ProductGroupStopClient.class);
        when(client.ready()).thenReturn(true);
        return client;
    }
    @Test void disabledIntegrationForbidsTaskMutationOrHttp(){
        Ledger db=new Ledger();db.task=due();
        ProductGroupStopClient remote=mock(ProductGroupStopClient.class);
        when(remote.ready()).thenReturn(false);
        var e=assertThrows(BusinessException.class,
                ()->new ProductExpiryStopDispatcher(db,remote).dispatch("expiry-stop-71",5L));
        assertEquals("PRODUCT_WRITE_DISABLED",e.getCode());
        assertEquals(0,db.executed.size());
        verify(remote,never()).pauseForExpiry(anyLong(),anyString(),anyString());
    }
    @Test void accepted202AfterPersistedIdDoesNotReleaseClaims(){
        Ledger db=new Ledger();db.task=due();
        ProductGroupStopClient remote=ready();
        when(remote.pauseForExpiry(eq(21L),anyString(),anyString())).thenAnswer(invocation->{
            assertEquals(1,db.executed.size(),"durable claim must precede remote HTTP");
            assertEquals(5L,db.args.getFirst()[1],"operator must be audited");
            String id=invocation.getArgument(1,String.class);
            assertEquals(32,id.length());
            return new ProductGroupStopClient.Acceptance(id,"ACCEPTED");
        });
        var result=new ProductExpiryStopDispatcher(db,remote).dispatch("expiry-stop-71",5L);
        verify(remote,times(1)).pauseForExpiry(eq(21L),eq(result.remoteOperationId()),anyString());
        assertEquals("RUNNING",result.taskStatus());
        assertEquals("REMOTE_ACCEPTED",result.stage());
        assertFalse(result.businessStateChanged());
        assertEquals(2,db.executed.size());
        assertTrue(db.executed.get(0).contains("remote_operation_id=?"));
        assertTrue(db.executed.get(1).contains("REMOTE_ACCEPTED"));
    }
    @Test void ambiguousTimeoutRequiresReconcileAndNeverResends(){
        Ledger db=new Ledger();db.task=due();
        ProductGroupStopClient remote=ready();
        when(remote.pauseForExpiry(eq(21L),anyString(),anyString())).thenThrow(
                new BusinessException("PRODUCT_TASK_ACK_UNCERTAIN","timeout"));
        var result=new ProductExpiryStopDispatcher(db,remote).dispatch("expiry-stop-71",5L);
        verify(remote,times(1)).pauseForExpiry(eq(21L),anyString(),anyString());
        assertEquals("COMPENSATION_REQUIRED",result.taskStatus());
        assertEquals("DISPATCH_RESULT_UNCERTAIN",result.stage());
        assertFalse(result.businessStateChanged());
        assertEquals(2,db.executed.size());
        assertTrue(db.executed.get(1).contains("REMOTE_DISPATCH_UNCERTAIN"));
    }
    @Test void invalidExpiryTaskNeverSent(){
        Ledger db=new Ledger();db.task=due();
        ProductGroupStopClient remote=ready();
        var svc=new ProductExpiryStopDispatcher(db,remote);
        db.task.put("remote_operation_id","previous-operation");
        assertEquals("TASK_PRECHECK_BLOCKED",
                assertThrows(BusinessException.class,()->svc.dispatch("expiry-stop-71",5L)).getCode());
        db.task.put("remote_operation_id",null);
        db.task.put("grace_days_snapshot",null);
        assertEquals("TASK_PRECHECK_BLOCKED",
                assertThrows(BusinessException.class,()->svc.dispatch("expiry-stop-71",5L)).getCode());
        verify(remote,never()).pauseForExpiry(anyLong(),anyString(),anyString());
    }
    @Test void concurrentOperatorsCannotBothSendRemoteRequests(){
        Ledger db=new Ledger();db.task=due();db.claimed=0;
        ProductGroupStopClient remote=ready();
        var e=assertThrows(BusinessException.class,
                ()->new ProductExpiryStopDispatcher(db,remote).dispatch("expiry-stop-71",5L));
        assertEquals("TASK_ALREADY_DISPATCHED",e.getCode());
        verify(remote,never()).pauseForExpiry(anyLong(),anyString(),anyString());
    }
    @Test void readPermissionCannotSendRealStop(){
        ProductTaskReconciler reconciler=mock(ProductTaskReconciler.class);
        ProductExpiryStopDispatcher dispatcher=mock(ProductExpiryStopDispatcher.class);
        ProductTaskReconcileController controller=new ProductTaskReconcileController(reconciler,dispatcher);
        UserPrincipal user=new UserPrincipal(7L,"reader","reader",
                List.of("OPERATOR"),List.of("service:read"));
        assertEquals("AUTH_FORBIDDEN",assertThrows(BusinessException.class,
                ()->controller.dispatchExpiryStop("expiry-stop-71",user)).getCode());
        verifyNoInteractions(dispatcher);
        controller.dispatchExpiryStop("expiry-stop-71",new UserPrincipal(1L,"admin","admin",
                List.of("SUPER_ADMIN"),List.of()));
        verify(dispatcher).dispatch("expiry-stop-71",1L);
    }
}
