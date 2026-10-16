package com.ipproxy.platform.service.v12;

import com.ipproxy.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ProductGraceReleaseInspectorTest {
    private static final OffsetDateTime EXP=OffsetDateTime.parse("2020-02-02T00:00:00Z");
    static class Ledger extends JdbcTemplate {
        Map<String,Object> task;
        List<Map<String,Object>> claims=new ArrayList<>();
        List<Map<String,Object>> peers=new ArrayList<>();
        long legacyCount;
        final List<String> sqls=new ArrayList<>();
        @Override public List<Map<String,Object>> queryForList(String sql,Object...args){
            sqls.add(sql);
            if(sql.contains("FROM svc_action_task t"))return task==null?List.of():List.of(task);
            if(sql.contains("FROM svc_egress_claim")&&sql.contains("service_id=?"))return claims;
            if(sql.contains("FROM svc_egress_claim c")&&sql.contains("c.service_id<>?"))return peers;
            fail("unexpected SQL: "+sql);return List.of();
        }
        @Override public <T> T queryForObject(String sql,Class<T> type,Object...args){
            sqls.add(sql);
            if(sql.contains("pg_advisory_xact_lock"))return null;
            if(sql.contains("res_exclusive_allocation"))return type.cast(Long.valueOf(legacyCount));
            fail("unexpected single SQL: "+sql);return null;
        }
    }
    private static Map<String,Object> task(){
        Map<String,Object> t=new HashMap<>();
        t.put("task_id",7L);t.put("service_id",52L);
        t.put("action_type","RELEASE");t.put("task_status","PRECHECK_BLOCKED");
        t.put("remote_operation_id",null);t.put("service_status","EXPIRED");
        t.put("expiry_phase","RELEASE_PENDING");
        t.put("product_type","LONG_IP");t.put("delivery_mode","SELF_OWNED");
        t.put("expire_at",EXP);t.put("grace_until_at",EXP.plusDays(2));
        t.put("grace_days_snapshot",2);
        return t;
    }
    private static Ledger ledger(String mode){
        Ledger l=new Ledger();l.task=task();
        if("EXCLUSIVE".equals(mode))l.task.put("product_type","EXCLUSIVE_IP");
        Map<String,Object> claim=new HashMap<>();
        claim.put("id",5L);claim.put("ipv4","198.51.100.7");
        claim.put("claim_mode",mode);claim.put("claim_status","GRACE_HELD");
        claim.put("hold_until_at",EXP.plusDays(2));
        l.claims.add(claim);
        return l;
    }
    private static ProductGraceReleaseInspector inspector(Ledger ledger){
        return new ProductGraceReleaseInspector(ledger);
    }
    @Test void sharedPeersAllowOnlyServiceUnbindNeverReturnToShort(){
        Ledger l=ledger("SHARED");
        l.peers.add(Map.of("service_id",53L,"claim_mode","SHARED","claim_status","ACTIVE"));
        var answer=inspector(l).inspect("expiry:RELEASE:52:xxx");
        assertTrue(answer.ready());
        assertFalse(answer.dataPlaneVerified());
        assertEquals("UNBIND_SERVICE_ONLY",answer.claims().getFirst().releasePlan());
        assertEquals(1,answer.claims().getFirst().otherProtectedClaims());
        assertFalse(answer.claims().getFirst().requiresModeConversion());
        assertTrue(l.sqls.stream().anyMatch(s->s.contains("pg_advisory_xact_lock")));
        assertTrue(l.sqls.stream().anyMatch(s->s.contains("res_exclusive_allocation")));
        assertTrue(l.sqls.stream().noneMatch(s->s.stripLeading().toUpperCase().startsWith("UPDATE")),
                "FOR UPDATE row locks are allowed, but preflight must not modify a business row");
    }
    @Test void lastSharedClaimBecomesCandidateForVerifiedShortConversion(){
        Ledger l=ledger("SHARED");
        var answer=inspector(l).inspect("expiry:RELEASE:52:xxx");
        assertTrue(answer.ready());
        assertEquals("RETURN_TO_SHORT_AFTER_VERIFICATION",answer.claims().getFirst().releasePlan());
        assertTrue(answer.claims().getFirst().requiresModeConversion());
        assertFalse(answer.dataPlaneVerified(),"local preflight is never a product confirmation");
    }
    @Test void exclusiveOnlyCanReturnWhenNoPeerAndNoLegacyConflict(){
        Ledger l=ledger("EXCLUSIVE");
        var answer=inspector(l).inspect("expiry:RELEASE:52:xxx");
        assertEquals("EXCLUSIVE",answer.claims().getFirst().mode());
        assertEquals("RETURN_TO_SHORT_AFTER_VERIFICATION",answer.claims().getFirst().releasePlan());
        l.peers.add(Map.of("service_id",55L,"claim_mode","SHARED","claim_status","ACTIVE"));
        assertEquals("EXCLUSIVE_EGRESS_CONFLICT",assertThrows(BusinessException.class,
                ()->inspector(l).inspect("expiry:RELEASE:52:xxx")).getCode());
    }
    @Test void legacyUnknownExclusiveBlocksReleaseAndReturnToPool(){
        Ledger l=ledger("SHARED");l.legacyCount=1;
        assertEquals("RELEASE_LEGACY_RECONCILIATION_REQUIRED",
                assertThrows(BusinessException.class,()->inspector(l).inspect("r1")).getCode());
    }
    @Test void notYetDueOrRenewedCannotRelease(){
        Ledger l=ledger("SHARED");
        l.task.put("grace_until_at",OffsetDateTime.parse("2030-01-01T00:00:00Z"));
        assertEquals("RELEASE_GRACE_NOT_ENDED",
                assertThrows(BusinessException.class,()->inspector(l).inspect("r1")).getCode());
        l.task.put("grace_until_at",EXP.plusDays(2));
        l.task.put("service_status","ACTIVE");
        assertEquals("RELEASE_PRECHECK_BLOCKED",
                assertThrows(BusinessException.class,()->inspector(l).inspect("r1")).getCode());
    }
    @Test void protectedGraceHoldMustMatchSnapshotAndClaimMode(){
        Ledger l=ledger("SHARED");
        l.claims.getFirst().put("hold_until_at",EXP.plusDays(1));
        assertEquals("RELEASE_CLAIM_HOLD_MISMATCH",
                assertThrows(BusinessException.class,()->inspector(l).inspect("r1")).getCode());
        l.claims.getFirst().put("hold_until_at",EXP.plusDays(2));
        l.claims.getFirst().put("claim_status","RELEASED");
        assertEquals("RELEASE_CLAIM_INCONSISTENT",
                assertThrows(BusinessException.class,()->inspector(l).inspect("r1")).getCode());
    }
    @Test void sameExitWithOtherExclusiveNeverApprovesLongReturn(){
        Ledger l=ledger("SHARED");
        l.peers.add(Map.of("service_id",53L,"claim_mode","EXCLUSIVE","claim_status","ACTIVE"));
        assertEquals("EGRESS_MODE_CONFLICT",
                assertThrows(BusinessException.class,()->inspector(l).inspect("r1")).getCode());
    }
}
