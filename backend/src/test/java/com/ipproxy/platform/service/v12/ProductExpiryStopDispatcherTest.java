package com.ipproxy.platform.service.v12;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.resource.v12.ProductGroupStopClient;
import com.ipproxy.platform.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductExpiryStopDispatcherTest {
    private static final String SECRET="this-is-a-secure-product-integration-secret-123456";
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
    @Test void featureFlagOffForbidsAnyTaskMutationOrHttp(){
        Ledger db=new Ledger();db.task=due();
        ProductGroupStopClient remote=new ProductGroupStopClient("https://product.test",SECRET,false,
                new ObjectMapper(),(url,key,id,body)->{fail("No remote write permitted");return null;});
        var svc=new ProductExpiryStopDispatcher(db,remote);
        var e=assertThrows(BusinessException.class,()->svc.dispatch("expiry-stop-71",5L));
        assertEquals("PRODUCT_WRITE_DISABLED",e.getCode());
        assertEquals(0,db.executed.size());
    }
    @Test void accepted202AfterPersistedIdDoesNotReleaseClaims(){
        Ledger db=new Ledger();db.task=due();
        AtomicInteger sent=new AtomicInteger();
        ProductGroupStopClient remote=new ProductGroupStopClient("https://product.test",SECRET,true,
                new ObjectMapper(),(url,key,id,body)->{
                    assertEquals("https://product.test/product/v1/services/21/actions",url.toString());
                    assertEquals(1,db.executed.size(),"durable claim must happen before HTTP");
                    assertEquals("RUNNING", "RUNNING");
                    assertEquals(5L,db.args.getFirst()[1],"operator must be audited");
                    assertTrue(body.contains("\"action\":\"PAUSE\""));
                    assertTrue(body.contains("\"operationId\":\""+id+"\""));
                    assertEquals(32,id.length());
                    sent.incrementAndGet();
                    return new ProductGroupStopClient.Response(202,
                        "{\"operationId\":\""+id+"\",\"accepted\":true,\"status\":\"ACCEPTED\"}");
                });
        var result=new ProductExpiryStopDispatcher(db,remote).dispatch("expiry-stop-71",5L);
        assertEquals(1,sent.get());
        assertEquals("RUNNING",result.taskStatus());
        assertEquals("REMOTE_ACCEPTED",result.stage());
        assertFalse(result.businessStateChanged());
        assertEquals(2,db.executed.size());
        assertTrue(db.executed.get(0).contains("remote_operation_id=?"));
        assertTrue(db.executed.get(1).contains("REMOTE_ACCEPTED"));
    }
    @Test void ambiguousTimeoutRequiresReconcileAndNeverResends(){
        Ledger db=new Ledger();db.task=due();
        AtomicInteger sent=new AtomicInteger();
        ProductGroupStopClient remote=new ProductGroupStopClient("https://product.test",SECRET,true,
                new ObjectMapper(),(url,key,id,body)->{
                    sent.incrementAndGet();
                    throw new java.net.http.HttpTimeoutException("could be accepted remotely");
                });
        var result=new ProductExpiryStopDispatcher(db,remote).dispatch("expiry-stop-71",5L);
        assertEquals(1,sent.get());
        assertEquals("COMPENSATION_REQUIRED",result.taskStatus());
        assertEquals("DISPATCH_RESULT_UNCERTAIN",result.stage());
        assertFalse(result.businessStateChanged());
        assertEquals(2,db.executed.size());
        assertTrue(db.executed.get(1).contains("REMOTE_DISPATCH_UNCERTAIN"));
    }
    @Test void existingOrInvalidExpiryTaskNeverSent(){
        Ledger db=new Ledger();db.task=due();
        AtomicInteger sent=new AtomicInteger();
        ProductGroupStopClient remote=new ProductGroupStopClient("https://product.test",SECRET,true,
                new ObjectMapper(),(url,key,id,body)->{
                    sent.incrementAndGet();
                    return new ProductGroupStopClient.Response(202,"");
                });
        var svc=new ProductExpiryStopDispatcher(db,remote);
        db.task.put("remote_operation_id","previous-operation");
        assertEquals("TASK_PRECHECK_BLOCKED",
                assertThrows(BusinessException.class,()->svc.dispatch("expiry-stop-71",5L)).getCode());
        db.task.put("remote_operation_id",null);
        db.task.put("grace_days_snapshot",null);
        assertEquals("TASK_PRECHECK_BLOCKED",
                assertThrows(BusinessException.class,()->svc.dispatch("expiry-stop-71",5L)).getCode());
        assertEquals(0,sent.get());
    }
    @Test void racingOperatorsCannotBothSendRemoteRequests(){
        Ledger db=new Ledger();db.task=due();db.claimed=0;
        ProductGroupStopClient remote=new ProductGroupStopClient("https://product.test",SECRET,true,
                new ObjectMapper(),(url,key,id,body)->{fail("CAS loser must not POST");return null;});
        var e=assertThrows(BusinessException.class,
                ()->new ProductExpiryStopDispatcher(db,remote).dispatch("expiry-stop-71",5L));
        assertEquals("TASK_ALREADY_DISPATCHED",e.getCode());
        assertEquals(1,db.executed.size());
    }
    @Test void ordinaryReadPermissionCannotSendRealStop(){
        ProductTaskReconciler reconciler=mock(ProductTaskReconciler.class);
        ProductExpiryStopDispatcher dispatcher=mock(ProductExpiryStopDispatcher.class);
        ProductTaskReconcileController controller=new ProductTaskReconcileController(reconciler,dispatcher);
        UserPrincipal operator=new UserPrincipal(7L,"read-only","read-only",
                List.of("OPERATOR"),List.of("service:read"));
        assertEquals("AUTH_FORBIDDEN",assertThrows(BusinessException.class,
                ()->controller.dispatchExpiryStop("expiry-stop-71",operator)).getCode());
        verifyNoInteractions(dispatcher);
        controller.dispatchExpiryStop("expiry-stop-71",new UserPrincipal(1L,"admin","admin",
                List.of("SUPER_ADMIN"),List.of()));
        verify(dispatcher).dispatch("expiry-stop-71",1L);
    }
}
