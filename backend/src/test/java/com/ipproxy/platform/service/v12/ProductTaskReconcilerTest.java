package com.ipproxy.platform.service.v12;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.resource.v12.ProductGroupReadClient;
import com.ipproxy.platform.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductTaskReconcilerTest {
    static class StubJdbc extends JdbcTemplate {
        Map<String,Object> task;
        int updates;
        int updatedRows=1;
        String updateSql;
        Object[] updateArgs;
        @Override public List<Map<String,Object>> queryForList(String sql,Object... args) {
            assertTrue(sql.contains("FROM svc_action_task"));
            return task==null?List.of():List.of(task);
        }
        @Override public int update(String sql,Object... args) {
            updates++;
            updateSql=sql;
            updateArgs=args;
            return updatedRows;
        }
    }
    private static Map<String,Object> task(String status,String remote) {
        Map<String,Object> row=new HashMap<>();
        row.put("id",17L);
        row.put("service_id",52L);
        row.put("operation_key","expiry-52-1");
        row.put("action_type","STOP_FOR_GRACE");
        row.put("task_status",status);
        row.put("stage","WAITING_REAL_ADAPTER");
        row.put("remote_operation_id",remote);
        return row;
    }
    private ProductTaskReconciler reconciler(StubJdbc jdbc,ProductGroupReadClient product) {
        return new ProductTaskReconciler(jdbc,product,new ObjectMapper());
    }
    @Test void expiryTaskWithoutRemoteIdRemainsBlockedAndNeverCallsNetwork(){
        StubJdbc db=new StubJdbc();db.task=task("PRECHECK_BLOCKED",null);
        ProductGroupReadClient product=mock(ProductGroupReadClient.class);
        var result=reconciler(db,product).reconcile("expiry-52-1");
        assertEquals("NOT_SENT",result.remoteStatus());
        assertEquals("PRECHECK_BLOCKED",result.taskStatus());
        assertFalse(result.businessStateChanged());
        assertEquals(0,db.updates);
        verifyNoInteractions(product);
    }
    @Test void asynchronousAcceptanceIsOnlyPendingNotBusinessSuccess(){
        StubJdbc db=new StubJdbc();db.task=task("PENDING","op-52");
        ProductGroupReadClient product=mock(ProductGroupReadClient.class);
        when(product.taskStatus("op-52")).thenReturn(Map.of("status","ACCEPTED"));
        var result=reconciler(db,product).reconcile("expiry-52-1");
        assertEquals("PENDING",result.taskStatus());
        assertEquals("REMOTE_ACCEPTED",result.stage());
        assertFalse(result.networkVerified());
        assertFalse(result.businessStateChanged());
        assertEquals(1,db.updates);
        assertTrue(db.updateSql.contains("WHERE id=? AND task_status=? AND remote_operation_id=?"));
        assertEquals("PENDING",db.updateArgs[0]);
        assertEquals("PENDING",db.updateArgs[6]);
        assertEquals("op-52",db.updateArgs[7]);
        verify(product,never()).actualService(anyString());
    }
    @Test void remoteSuccessAndVerifiedStopRequireSeparateBusinessCommit(){
        StubJdbc db=new StubJdbc();db.task=task("RUNNING","op-52");
        ProductGroupReadClient product=mock(ProductGroupReadClient.class);
        when(product.taskStatus("op-52")).thenReturn(Map.of("status","SUCCEEDED"));
        when(product.actualService("52")).thenReturn(Map.of(
                "actualStatus","STOPPED","verificationStatus","VERIFIED",
                "verifiedAt","2026-10-15T02:00:00Z"));
        var result=reconciler(db,product).reconcile("expiry-52-1");
        assertTrue(result.networkVerified());
        assertEquals("VERIFYING",result.taskStatus());
        assertEquals("NEEDS_BUSINESS_COMMIT",result.stage());
        assertFalse(result.businessStateChanged());
        assertEquals("VERIFYING",db.updateArgs[0]);
        assertFalse(db.updateSql.contains("UPDATE svc_instance"));
        assertFalse(db.updateSql.contains("UPDATE svc_egress_claim"));
    }
    @Test void remoteSuccessWithoutProofKeepsPendingVerification(){
        StubJdbc db=new StubJdbc();db.task=task("RUNNING","op-52");
        ProductGroupReadClient product=mock(ProductGroupReadClient.class);
        when(product.taskStatus("op-52")).thenReturn(Map.of("status","SUCCEEDED"));
        when(product.actualService("52")).thenReturn(Map.of(
                "actualStatus","RUNNING","verificationStatus","VERIFIED",
                "verifiedAt","2026-10-15T02:00:00Z"));
        var result=reconciler(db,product).reconcile("expiry-52-1");
        assertEquals("WAITING_NETWORK_VERIFICATION",result.stage());
        assertFalse(result.networkVerified());
        assertFalse(result.businessStateChanged());
    }
    @Test void remoteUnknownStopsAutomaticRetries(){
        StubJdbc db=new StubJdbc();db.task=task("RUNNING","op-52");
        ProductGroupReadClient product=mock(ProductGroupReadClient.class);
        when(product.taskStatus("op-52")).thenReturn(Map.of("status","UNKNOWN"));
        var result=reconciler(db,product).reconcile("expiry-52-1");
        assertEquals("COMPENSATION_REQUIRED",result.taskStatus());
        assertEquals("REMOTE_NEEDS_MANUAL_REVIEW",result.stage());
        assertEquals("REMOTE_STATUS_UNCERTAIN",db.updateArgs[3]);
    }
    @Test void transportTimeoutCannotChangeBusinessOrRetryAutomatically(){
        StubJdbc db=new StubJdbc();db.task=task("RUNNING","op-52");
        ProductGroupReadClient product=mock(ProductGroupReadClient.class);
        when(product.taskStatus("op-52")).thenThrow(new BusinessException(
                "PRODUCT_INTEGRATION_UNAVAILABLE","timeout"));
        var result=reconciler(db,product).reconcile("expiry-52-1");
        assertEquals("UNAVAILABLE",result.remoteStatus());
        assertEquals("RUNNING",result.taskStatus());
        assertEquals(0,db.updates);
        verify(product,times(1)).taskStatus("op-52");
    }
    @Test void terminalTaskAndConcurrentChangesRemainImmutable(){
        StubJdbc db=new StubJdbc();db.task=task("SUCCEEDED","op-52");
        ProductGroupReadClient product=mock(ProductGroupReadClient.class);
        var result=reconciler(db,product).reconcile("expiry-52-1");
        assertEquals("NOT_POLLED",result.remoteStatus());
        verifyNoInteractions(product);

        db.task=task("RUNNING","op-52");db.updatedRows=0;
        when(product.taskStatus("op-52")).thenReturn(Map.of("status","PENDING"));
        BusinessException error=assertThrows(BusinessException.class,
                ()->reconciler(db,product).reconcile("expiry-52-1"));
        assertEquals("TASK_CONCURRENT_MODIFICATION",error.getCode());
    }
    @Test void operatorPermissionRequiredAndNotImplicitFromReadPermission(){
        ProductTaskReconciler product=mock(ProductTaskReconciler.class);
        ProductTaskReconcileController ctrl=new ProductTaskReconcileController(product);
        UserPrincipal user=new UserPrincipal(8L,"operator","operator",
                List.of("OPERATOR"),List.of("service:read"));
        BusinessException error=assertThrows(BusinessException.class,
                ()->ctrl.reconcile("expiry-52-1",user));
        assertEquals("AUTH_FORBIDDEN",error.getCode());
        verifyNoInteractions(product);
        ctrl.reconcile("expiry-52-1",new UserPrincipal(1L,"admin","admin",
                List.of("SUPER_ADMIN"),List.of()));
        verify(product).reconcile("expiry-52-1");
    }
}
