package com.ipproxy.platform.service.v12;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;

/** Contract-only test of the existing management-side fallback selector.
 * Real capacity, proxy switching and network verification belong to product-group integration tests.
 */
class BuiltinResourcePolicyTest {
    static class RecordingJdbc extends JdbcTemplate {
        final List<String> sqls = new ArrayList<>();
        final Queue<List<Map<String,Object>>> responses = new ArrayDeque<>();

        @Override
        public List<Map<String,Object>> queryForList(String sql, Object... args) {
            sqls.add(sql);
            return responses.remove();
        }
    }

    @Test
    void longCandidatesPreferExistingSharedClaimsAndBlockExclusiveConflicts() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.responses.add(List.of(Map.of("id", 7L, "pool_code", "LONG-POOL")));
        jdbc.responses.add(List.of(Map.of("id", 9L, "resource_code", "LINE-9")));

        BuiltinResourcePolicy.Decision d =
                new BuiltinResourcePolicy(jdbc).decide("LONG_IP", 4L, 3L, 2L);
        assertEquals(7L, d.poolId());
        assertEquals(9L, d.lineId());
        assertEquals(2, jdbc.sqls.size());
        assertTrue(jdbc.sqls.get(0).contains("shared_claim.claim_mode='SHARED'"),
                "prioritize existing long shared export in candidate pool");
        String lineSql = jdbc.sqls.get(1);
        assertTrue(lineSql.contains("ex.claim_mode='EXCLUSIVE'"),
                "long cannot use exclusive IPv4");
        assertTrue(lineSql.contains("'PENDING_RECYCLE'"),
                "legacy reserved or recycling exit must stay blocked");
        assertTrue(lineSql.contains("shared.source_line_id=l.id"),
                "prefer an existing long shared line");
        assertTrue(lineSql.contains("l.active_users<l.user_limit"),
                "legacy capacity check must remain in place");
    }

    @Test
    void shortExtractionPolicyDoesNotPinOneLineForEveryCustomer() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.responses.add(List.of(Map.of("id", 5L, "pool_code", "SHARED")));

        BuiltinResourcePolicy.Decision d =
                new BuiltinResourcePolicy(jdbc).decide("SHORT_IP", 4L, 3L, 2L);
        assertEquals(5L, d.poolId());
        assertNull(d.lineId());
        assertEquals(1, jdbc.sqls.size());
    }
}
