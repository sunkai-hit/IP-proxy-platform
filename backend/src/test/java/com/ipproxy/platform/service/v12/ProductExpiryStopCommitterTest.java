package com.ipproxy.platform.service.v12;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductExpiryStopCommitterTest {
    private static final OffsetDateTime EXPIRY=OffsetDateTime.parse("2026-10-14T10:00:00Z");
    static class Ledger extends JdbcTemplate {
        Map<String,Object> task;
        List<Map<String,Object>> claims=new ArrayList<>();
        List<String> updates=new ArrayList<>();
        List<Object[]> arguments=new ArrayList<>();
        int held=1;
        @Override public List<Map<String,Object>> queryForList(String sql,Object...args) {
            if(sql.contains("FROM svc_action_task t JOIN svc_instance s")){
                assertTrue(sql.contains("FOR UPDATE OF s,t"));
                return task==null?List.of():List.of(task);
            }
            if(sql.contains("FROM svc_egress_claim")){
                assertTrue(sql.contains("FOR UPDATE"));
                return claims;
            }
            fail("Unexpected SQL query: "+sql);
            return List.of();
        }
        @Override public int update(String sql,Object...args){
            updates.add(sql);arguments.add(args);
            if(sql.contains("UPDATE svc_egress_claim"))return held;
            return 1;
        }
    }
    private static Map<String,Object> baseline(){
        Map<String,Object> row=new HashMap<>();
        row.put("task_id",91L);row.put("operation_key","expiry-stop-91");
        row.put("service_id",52L);row.put("action_type","STOP_FOR_GRACE");
        row.put("task_status","VERIFYING");row.put("stage","NEEDS_BUSINESS_COMMIT");
        row.put("remote_operation_id","op52");
        row.put("product_type","LONG_IP");row.put("delivery_mode","SELF_OWNED");
        row.put("service_status","ACTIVE");row.put("expiry_phase","STOP_PENDING");
        row.put("expire_at",EXPIRY);row.put("grace_until_at",EXPIRY.plusDays(2));
        row.put("grace_days_snapshot",2);
        row.put("task_request","{\"effectiveExpireAt\":\"2026-10-14T10:00:00Z\",\"graceDaysSnapshot\":2}");
        row.put("task_result","{\"operationId\":\"op52\",\"remoteStatus\":\"SUCCEEDED\","
                +"\"networkVerified\":true,\"networkEvidence\":{\"source\":\"PRODUCT_GROUP_HTTP\","
                +"\"actualStatus\":\"STOPPED\",\"verificationStatus\":\"VERIFIED\","
                +"\"verifiedAt\":\"2026-10-14T10:02:00Z\",\"sampledAt\":\"2026-10-14T10:03:00Z\"}}");
        return row;
    }
    private static Map<String,Object> claim(String mode,String state){
        return Map.of("id",7L,"claim_mode",mode,"claim_status",state);
    }
    private static Ledger ledger(){
        Ledger db=new Ledger();db.task=baseline();db.claims.add(claim("SHARED","ACTIVE"));return db;
    }
    @Test void verifiedStopCommitsOnlyCurrentServiceAndKeepsFixedIpv4(){
        Ledger db=ledger();
        var result=new ProductExpiryStopCommitter(db,new ObjectMapper()).commit("expiry-stop-91",3L);
        assertEquals("EXPIRED",result.serviceStatus());
        assertEquals("GRACE_HELD",result.expiryPhase());
        assertEquals("SUCCEEDED",result.taskStatus());
        assertEquals(1,result.heldClaims());
        assertFalse(result.egressReleased());
        assertTrue(result.businessStateChanged());
        assertEquals(4,db.updates.size());
        assertTrue(db.updates.get(0).contains("status='EXPIRED'"));
        assertTrue(db.updates.get(1).contains("claim_status='GRACE_HELD'"));
        assertTrue(db.updates.get(1).contains("WHERE service_id=?"));
        assertTrue(db.updates.get(2).contains("status='FROZEN'"));
        assertTrue(db.updates.get(3).contains("stage='BUSINESS_COMMITTED'"));
        assertTrue(db.updates.get(3).contains("businessCommittedBy"));
        assertFalse(db.updates.get(3).contains("actor_user_id=?"),
                "preserve original task dispatch actor in the durable audit trail");
        for(String sql:db.updates){
            assertFalse(sql.contains("res_exclusive_allocation"),"never release legacy exclusive allocation");
            assertFalse(sql.contains("claim_status='RELEASED'"),"never recycle fixed IPv4 during grace");
        }
    }
    @Test void sharedLongOtherCustomersAreNeverUpdated(){
        Ledger db=ledger();
        new ProductExpiryStopCommitter(db,new ObjectMapper()).commit("expiry-stop-91",3L);
        assertEquals(EXPIRY.plusDays(2),db.arguments.get(1)[0]);
        assertEquals(52L,db.arguments.get(1)[1]);
        assertTrue(db.updates.get(1).contains("WHERE service_id=?"));
        assertFalse(db.updates.get(1).contains("WHERE egress_ipv4="));
    }
    @Test void zeroGraceStillNeedsRealStopAndKeepsClaimUntilRealRelease(){
        Ledger db=ledger();db.task.put("grace_days_snapshot",0);
        db.task.put("grace_until_at",EXPIRY);
        var result=new ProductExpiryStopCommitter(db,new ObjectMapper()).commit("expiry-stop-91",3L);
        assertEquals("GRACE_HELD",result.expiryPhase());
        assertFalse(result.egressReleased());
    }
    @Test void renewalOrChangedTermPreventsOldExpiryProofFromBeingApplied(){
        Ledger db=ledger();
        db.task.put("expire_at",EXPIRY.plusDays(10));
        db.task.put("grace_until_at",EXPIRY.plusDays(12));
        var ex=assertThrows(BusinessException.class,
                ()->new ProductExpiryStopCommitter(db,new ObjectMapper()).commit("expiry-stop-91",3L));
        assertTrue(Set.of("GRACE_SNAPSHOT_INVALID","TASK_PROOF_INVALID").contains(ex.getCode()));
        assertTrue(db.updates.isEmpty());
    }
    @Test void wrongOperationOrUnverifiedProductReceiptFailsClosed(){
        Ledger db=ledger();
        db.task.put("remote_operation_id","another-op");
        assertEquals("TASK_PROOF_INVALID",assertThrows(BusinessException.class,
                ()->new ProductExpiryStopCommitter(db,new ObjectMapper()).commit("expiry-stop-91",3L)).getCode());
        db.task.put("remote_operation_id","op52");
        db.task.put("task_result","{\"operationId\":\"op52\",\"remoteStatus\":\"SUCCEEDED\",\"networkVerified\":false}");
        assertEquals("TASK_PROOF_INVALID",assertThrows(BusinessException.class,
                ()->new ProductExpiryStopCommitter(db,new ObjectMapper()).commit("expiry-stop-91",3L)).getCode());
        assertTrue(db.updates.isEmpty());
    }
    @Test void missingOrInconsistentExclusiveClaimsMustBeReconciled(){
        Ledger db=ledger();db.task.put("product_type","EXCLUSIVE_IP");
        assertEquals("EGRESS_CLAIM_INCONSISTENT",assertThrows(BusinessException.class,
                ()->new ProductExpiryStopCommitter(db,new ObjectMapper()).commit("expiry-stop-91",3L)).getCode());
        db.claims.clear();
        assertEquals("EGRESS_CLAIM_MISSING",assertThrows(BusinessException.class,
                ()->new ProductExpiryStopCommitter(db,new ObjectMapper()).commit("expiry-stop-91",3L)).getCode());
        assertTrue(db.updates.isEmpty());
    }
    @Test void claimUpdateRaceCausesTransactionRollback() throws NoSuchMethodException {
        Ledger db=ledger();db.held=0;
        assertEquals("EGRESS_CLAIM_CONCURRENT",assertThrows(BusinessException.class,
                ()->new ProductExpiryStopCommitter(db,new ObjectMapper()).commit("expiry-stop-91",3L)).getCode());
        assertTrue(ProductExpiryStopCommitter.class.getMethod("commit",String.class,Long.class)
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class));
    }
    @Test void alreadyCommittedIsIdempotentWithoutExtraUpdates(){
        Ledger db=ledger();
        db.task.put("task_status","SUCCEEDED");db.task.put("stage","BUSINESS_COMMITTED");
        var result=new ProductExpiryStopCommitter(db,new ObjectMapper()).commit("expiry-stop-91",3L);
        assertFalse(result.businessStateChanged());
        assertEquals("SUCCEEDED",result.taskStatus());
        assertTrue(db.updates.isEmpty());
    }
    @Test void onlyReleaseAuthorizedBusinessOperatorMayCommit(){
        ProductTaskReconciler reconciler=mock(ProductTaskReconciler.class);
        ProductExpiryStopDispatcher dispatcher=mock(ProductExpiryStopDispatcher.class);
        ProductExpiryStopCommitter committer=mock(ProductExpiryStopCommitter.class);
        var ctrl=new ProductTaskReconcileController(reconciler,dispatcher,committer);
        var reader=new UserPrincipal(9L,"read","read",List.of("OPERATOR"),List.of("service:read"));
        assertEquals("AUTH_FORBIDDEN",assertThrows(BusinessException.class,
                ()->ctrl.commitExpiryStop("expiry-stop-91",reader)).getCode());
        verifyNoInteractions(committer);
        var admin=new UserPrincipal(3L,"admin","admin",List.of("SUPER_ADMIN"),List.of());
        ctrl.commitExpiryStop("expiry-stop-91",admin);
        verify(committer).commit("expiry-stop-91",3L);
    }
}
