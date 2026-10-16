package com.ipproxy.platform.service.v12;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Defends nullable tenant filtering. Bare '? IS NULL' fails on PostgreSQL
 * with "could not determine data type of parameter" for an omitted customerId. */
class V12ServiceQueryFilterTest {
    static final class CapturingJdbc extends JdbcTemplate {
        String countSql;
        String listSql;
        Object[] countArgs;
        Object[] listArgs;

        @Override public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            countSql = sql;
            countArgs = args;
            return requiredType.cast(0L);
        }

        @Override public List<Map<String,Object>> queryForList(String sql, Object... args) {
            listSql = sql;
            listArgs = args;
            return List.of();
        }
    }

    @Test void omittedCustomerFilterIsTypedForBothCountAndList() {
        var db = new CapturingJdbc();
        var mapper = new V12ServiceMapper(db);
        assertEquals(0L, mapper.countServices("", "", null, "", ""));
        assertTrue(mapper.services("", "", null, "", "", 20, 0).isEmpty());
        assertNotNull(db.countSql);
        assertNotNull(db.listSql);
        assertTrue(db.countSql.contains("CAST(? AS BIGINT) IS NULL OR s.customer_id=CAST(? AS BIGINT)"));
        assertTrue(db.listSql.contains("CAST(? AS BIGINT) IS NULL OR s.customer_id=CAST(? AS BIGINT)"));
        assertNull(db.countArgs[5]);
        assertNull(db.listArgs[5]);
    }

    @Test void specifiedCustomerFilterRemainsBoundTwice() {
        var db = new CapturingJdbc();
        var mapper = new V12ServiceMapper(db);
        mapper.countServices("", "ACTIVE", 42L, "LONG_IP", "SELF_OWNED");
        mapper.services("", "ACTIVE", 42L, "LONG_IP", "SELF_OWNED", 20, 0);
        assertEquals(42L, db.countArgs[5]);
        assertEquals(42L, db.countArgs[6]);
        assertEquals(42L, db.listArgs[5]);
        assertEquals(42L, db.listArgs[6]);
    }
}
