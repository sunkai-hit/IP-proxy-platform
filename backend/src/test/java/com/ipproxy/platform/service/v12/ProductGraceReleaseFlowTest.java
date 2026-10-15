package com.ipproxy.platform.service.v12;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.resource.v12.ProductGroupStopClient;
import com.ipproxy.platform.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductGraceReleaseFlowTest {
    static class Ledger extends JdbcTemplate {
        Map<String,Object> task;
        Map<String,Object> claim;
        long peers;
        long legacy;
        int updates;
        int claimCount=1;
        List<String> sqls=new ArrayList<>();
        @Override public List<Map<String,Object>> queryForList(String sql,Object...args){
            if(sql.contains("FROM svc_action_task t"))return List.of(task);
            if(sql.contains("FROM svc_egress_claim WHERE service_id"))return List.of(claim);
            fail("Unexpected query: "+sql);return List.of();
        }
        @Override public <T> T queryForObject(String sql,Class<T> type,Object...args){
            if(sql.contains("pg_advisory_xact_lock"))return null;
            if(sql.contains("res_exclusive_allocation"))return type.cast(Long.valueOf(legacy));
            if(sql.contains("count(*) FROM svc_egress_claim"))return type.cast(Long.valueOf(peers));
            fail("Unexpected single query: "+sql);return null;
        }
        @Override public int update(String sql,Object...args){
            sqls.add(sql);updates++;
            if(sql.contains("UPDATE svc_egress_claim"))return claimCount;
            return 1;
        }
    }
    static final OffsetDateTime NOW=OffsetDateTime.now(ZoneOffset.UTC);
    static final OffsetDateTime EXPIRY=NOW.minusDays(4);
    static final OffsetDateTime HOLD=EXPIRY.plusDays(2);
    static final String OP="expiry-release-52";
    private static Map<String,Object> task(String mode,boolean validProof){
        Map<String,Object> t=new HashMap<>();
        t.put("task_id",20L);t.put("service_id",52L);
        t.put("action_type","RELEASE");t.put("task_status","VERIFYING");
        t.put("stage","NEEDS_BUSINESS_COMMIT");t.put("remote_operation_id","op52");
        t.put("service_status","EXPIRED");t.put("product_type","LONG_IP");
        t.put("delivery_mode","SELF_OWNED");t.put("expiry_phase","RELEASE_PENDING");
        t.put("expire_at",EXPIRY);t.put("grace_until_at",HOLD);t.put("grace_days_snapshot",2);
        t.put("request_json","{\"effectiveExpireAt\":\""+EXPIRY+"\",\"releaseMode\":\""+mode+"\",\"egressIpv4\":\"198.51.100.7\"}");
        t.put("result_json","{\"operationId\":\"op52\",\"remoteStatus\":\"SUCCEEDED\",\"networkVerified\":true,"
            +"\"networkEvidence\":{\"source\":\"PRODUCT_GROUP_HTTP\",\"actualStatus\":\"STOPPED\","
            +"\"verificationStatus\":\"VERIFIED\",\"verifiedAt\":\""+NOW.minusDays(1)+"\","
            +"\"releaseVerification\":{\"releaseMode\":\""+mode+"\",\"egressIpv4\":\"198.51.100.7\","
            +"\"serviceAccessRevoked\":true,\"allOldAccessRevoked\":true,\"switchedToShort\":"+validProof+","
            +"\"shortPoolHealthy\":true,\"resourceId\":\"r-1\",\"verifiedAt\":\""+NOW.minusMinutes(2)+"\"}}}");
        return t;
    }
    private static Ledger ledger(String mode,long peers){
        Ledger db=new Ledger();db.task=task(mode,true);db.peers=peers;
        Map<String,Object> c=new HashMap<>();
        c.put("id",8L);c.put("ipv4","198.51.100.7");c.put("claim_mode","SHARED");
        c.put("claim_status","GRACE_HELD");c.put("hold_until_at",HOLD);
        db.claim=c;return db;
    }
    @Test void oneSharedCustomerUnboundWhileOtherClientRemains(){
        Ledger db=ledger("UNBIND_SERVICE_ONLY",1);
        var r=new ProductGraceReleaseCommitter(db,new ObjectMapper()).commit(OP,5L);
        assertTrue(r.businessCommitted());assertFalse(r.productPoolVerified());
        assertEquals("RELEASED",r.phase());
        assertTrue(db.sqls.stream().anyMatch(sql->sql.contains("UPDATE svc_egress_claim")));
        assertTrue(db.sqls.stream().noneMatch(sql->sql.contains("WHERE egress_ipv4=")));
        assertTrue(db.sqls.stream().noneMatch(sql->sql.contains("UPDATE res_exclusive_allocation")));
    }
    @Test void lastSharedCustomerOnlyReturnsAfterVerifiedShortPool(){
        Ledger db=ledger("RETURN_TO_SHORT_AFTER_VERIFICATION",0);
        var result=new ProductGraceReleaseCommitter(db,new ObjectMapper()).commit(OP,5L);
        assertTrue(result.productPoolVerified());
        assertEquals("RETURN_TO_SHORT_AFTER_VERIFICATION",result.releaseMode());
        db=ledger("RETURN_TO_SHORT_AFTER_VERIFICATION",0);
        db.task=task("RETURN_TO_SHORT_AFTER_VERIFICATION",false);
        Ledger missingProof=db;
        assertEquals("RELEASE_SHORT_POOL_NOT_VERIFIED",assertThrows(BusinessException.class,
                ()->new ProductGraceReleaseCommitter(missingProof,new ObjectMapper()).commit(OP,5L)).getCode());
        assertEquals(0,missingProof.updates);
    }
    @Test void peersAddedAfterPreflightFailClosed(){
        Ledger db=ledger("RETURN_TO_SHORT_AFTER_VERIFICATION",1);
        assertEquals("RELEASE_PEERS_CHANGED",assertThrows(BusinessException.class,
                ()->new ProductGraceReleaseCommitter(db,new ObjectMapper()).commit(OP,5L)).getCode());
        assertEquals(0,db.updates);
    }
    @Test void missingLegacyProofOrRenewedServiceBlocksBusinessRelease(){
        Ledger db=ledger("RETURN_TO_SHORT_AFTER_VERIFICATION",0);
        db.legacy=1;
        assertEquals("RELEASE_LEGACY_UNRECONCILED",assertThrows(BusinessException.class,
                ()->new ProductGraceReleaseCommitter(db,new ObjectMapper()).commit(OP,5L)).getCode());
        db.legacy=0;db.task.put("expiry_phase","RENEW_RESTORE_PENDING");
        assertEquals("RELEASE_STATE_CHANGED",assertThrows(BusinessException.class,
                ()->new ProductGraceReleaseCommitter(db,new ObjectMapper()).commit(OP,5L)).getCode());
    }
    @Test void dispatcherMustPersistRemoteOperationBeforeSingleWriteAndNeverMarkReleased(){
        Ledger db=new Ledger();
        ProductGraceReleaseInspector inspector=mock(ProductGraceReleaseInspector.class);
        when(inspector.inspect(OP)).thenReturn(new ProductGraceReleaseInspector.Inspection(OP,52L,
                "READY_FOR_PRODUCT_REVIEW",true,false,List.of(
                  new ProductGraceReleaseInspector.ClaimPlan(8L,"198.51.100.7","SHARED",
                        "UNBIND_SERVICE_ONLY",1,false)),"ready"));
        ProductGroupStopClient remote=mock(ProductGroupStopClient.class);
        when(remote.ready()).thenReturn(true);
        when(remote.releaseAfterGrace(eq(52L),anyString(),eq("UNBIND_SERVICE_ONLY"),eq("198.51.100.7")))
             .thenAnswer(invocation->{
                assertEquals(1,db.updates,"durable operation ID must be recorded before product call");
                return new ProductGroupStopClient.Acceptance(invocation.getArgument(1),"ACCEPTED");
             });
        var result=new ProductGraceReleaseDispatcher(db,inspector,remote).dispatch(OP,5L);
        assertEquals("REMOTE_ACCEPTED",result.stage());
        assertFalse(result.egressReleased());
        verify(remote,times(1)).releaseAfterGrace(eq(52L),eq(result.remoteOperationId()),
                eq("UNBIND_SERVICE_ONLY"),eq("198.51.100.7"));
        assertEquals(2,db.updates);
    }
    @Test void dispatcherUncertainResponseDoesNotRetryOrRelease(){
        Ledger db=new Ledger();
        ProductGraceReleaseInspector inspector=mock(ProductGraceReleaseInspector.class);
        when(inspector.inspect(OP)).thenReturn(new ProductGraceReleaseInspector.Inspection(OP,52L,
                "READY_FOR_PRODUCT_REVIEW",true,false,List.of(
                    new ProductGraceReleaseInspector.ClaimPlan(8L,"198.51.100.7","SHARED",
                        "RETURN_TO_SHORT_AFTER_VERIFICATION",0,true)),"ready"));
        ProductGroupStopClient remote=mock(ProductGroupStopClient.class);
        when(remote.ready()).thenReturn(true);
        when(remote.releaseAfterGrace(anyLong(),anyString(),anyString(),anyString()))
            .thenThrow(new BusinessException("PRODUCT_TASK_ACK_UNCERTAIN","timeout"));
        var result=new ProductGraceReleaseDispatcher(db,inspector,remote).dispatch(OP,5L);
        assertEquals("COMPENSATION_REQUIRED",result.taskStatus());
        assertFalse(result.egressReleased());
        verify(remote,times(1)).releaseAfterGrace(anyLong(),anyString(),anyString(),anyString());
    }
    @Test void expiredReleaseEndpointsRequireBusinessPermission(){
        var inspector=mock(ProductGraceReleaseInspector.class);
        var dispatcher=mock(ProductGraceReleaseDispatcher.class);
        var committer=mock(ProductGraceReleaseCommitter.class);
        var ctl=new ProductGraceReleaseExecutionController(dispatcher,committer);
        var user=new UserPrincipal(7L,"reader","reader",List.of("OPERATOR"),List.of("service:read"));
        assertEquals("AUTH_FORBIDDEN",assertThrows(BusinessException.class,
                ()->ctl.dispatch(OP,user)).getCode());
        assertEquals("AUTH_FORBIDDEN",assertThrows(BusinessException.class,
                ()->ctl.commit(OP,user)).getCode());
        verifyNoInteractions(dispatcher,committer);
    }
}
