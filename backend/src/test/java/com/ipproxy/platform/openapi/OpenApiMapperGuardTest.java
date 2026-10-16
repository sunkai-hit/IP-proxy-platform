package com.ipproxy.platform.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Protects the legacy short-IP extraction query during the transition to
 * the product group's authoritative resource pool.
 *
 * This is a SQL contract regression test, not a substitute for PostgreSQL
 * integration or a real proxy data-plane test.
 */
class OpenApiMapperGuardTest {
    @Test
    void occupiedLongAndExclusiveIpv4MustNotBeSelectedFromLegacyShortPool() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new OpenApiMapper(jdbc).selectIps(1L, 2L, 3, "", "", 0);
        String sql = (String) Mockito.mockingDetails(jdbc)
                .getInvocations().iterator().next().getArgument(0);

        assertTrue(sql.contains("ec.egress_ipv4=i.ip_address"), "R3 egress claims must be checked");
        assertTrue(sql.contains("'RESERVED','ACTIVE','GRACE_HELD','RELEASE_PENDING'"),
                "reserved, active, grace and release-pending IPv4 must stay blocked");
        assertTrue(sql.contains("res_exclusive_allocation"), "legacy exclusive claims must be checked");
        assertTrue(sql.contains("ea.current_ip_snapshot=i.ip_address"), "snapshot must remain protected after redial");
        assertTrue(sql.contains("'LOCKED','ALLOCATED','PENDING_RECYCLE'"), "legacy holds and pending recycle must stay blocked");
    }
}
