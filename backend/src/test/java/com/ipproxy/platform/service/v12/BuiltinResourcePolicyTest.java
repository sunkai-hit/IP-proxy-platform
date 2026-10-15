package com.ipproxy.platform.service.v12;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BuiltinResourcePolicyTest {
    @Test
    void longCandidatesPreferExistingSharedClaimsAndBlockExclusiveConflicts() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(), any()))
                .thenReturn(List.of(Map.of("id", 7L, "pool_code", "LONG-POOL")))
                .thenReturn(List.of(Map.of("id", 9L, "resource_code", "LINE-9")));
        BuiltinResourcePolicy.Decision d =
                new BuiltinResourcePolicy(jdbc).decide("LONG_IP", 4L, 3L, 2L);
        assertEquals(7L, d.poolId());
        assertEquals(9L, d.lineId());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).queryForList(sql.capture(), any(), any());
        assertTrue(sql.getAllValues().get(0).contains("shared_claim.claim_mode='SHARED'"),
                "choose a pool containing an existing long shared export first");
        String lineSql = sql.getAllValues().get(1);
        assertTrue(lineSql.contains("ex.claim_mode='EXCLUSIVE'"),
                "long service must not take an exclusive IPv4");
        assertTrue(lineSql.contains("'PENDING_RECYCLE'"),
                "legacy occupied exit remains blocked until genuine release");
        assertTrue(lineSql.contains("shared.source_line_id=l.id"),
                "prefer a line already bound to long shared services");
        assertTrue(lineSql.contains("l.active_users<l.user_limit"),
                "reject known legacy capacity exhaustion");
    }

    @Test
    void shortPoolDoesNotPinOneLongLineForEachShortIpExtraction() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(), any()))
                .thenReturn(List.of(Map.of("id", 5L, "pool_code", "SHARED")));
        BuiltinResourcePolicy.Decision d =
                new BuiltinResourcePolicy(jdbc).decide("SHORT_IP", 4L, 3L, 2L);
        assertEquals(5L, d.poolId());
        assertNull(d.lineId());
        verify(jdbc, times(1)).queryForList(anyString(), any(), any());
    }
}
