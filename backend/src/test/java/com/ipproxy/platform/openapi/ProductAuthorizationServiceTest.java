package com.ipproxy.platform.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class ProductAuthorizationServiceTest {
    static final Clock CLOCK=Clock.fixed(Instant.parse("2026-10-15T02:00:00Z"),ZoneOffset.UTC);
    static class StubJdbc extends JdbcTemplate {
        Map<String,Object> row;
        long used=0;
        long recent=0;
        @Override public List<Map<String,Object>> queryForList(String sql,Object... args) {
            assertTrue(sql.contains("s.customer_id=c.id"),"Account and service must belong to the same customer");
            return row==null?List.of():List.of(row);
        }
        @Override public <T> T queryForObject(String sql,Class<T> type,Object... args) {
            return type.cast(Long.valueOf(sql.contains("sum(returned_count)")?used:recent));
        }
    }
    private static Map<String,Object> active(){
        Map<String,Object> row=new HashMap<>();
        row.put("account_status","ACTIVE");
        row.put("customer_status","ACTIVE");
        row.put("service_status","ACTIVE");
        row.put("product_type","SHORT_IP");
        row.put("access_mode_code","API_EXTRACT");
        row.put("delivery_stage","VERIFIED");
        row.put("grant_id",71L);
        row.put("grant_status","ACTIVE");
        row.put("token_version",2L);
        row.put("customer_id",9L);
        row.put("order_customer_id",9L);
        row.put("order_id",88L);
        row.put("order_payment_status","PAID_CONFIRMED");
        row.put("effective_at",OffsetDateTime.parse("2026-10-14T00:00:00Z"));
        row.put("expire_at",OffsetDateTime.parse("2026-10-20T00:00:00Z"));
        row.put("policy_snapshot_json","{\"singleExtractLimit\":10,\"extractQuota\":100,\"extractRateLimit\":20,\"extractRateWindowSeconds\":60}");
        return row;
    }
    private static ProductAuthorizationService.CheckRequest shortRequest(int n){
        return new ProductAuthorizationService.CheckRequest("23","54","88","SHORT_EXTRACT",n,"test-1");
    }
    private static ProductAuthorizationService service(StubJdbc db){
        return new ProductAuthorizationService(db,new ObjectMapper(),CLOCK);
    }
    @Test void verifiedPaidShortServiceAllowedForFiveSecondsOnly() {
        StubJdbc db=new StubJdbc();db.row=active();db.used=60;
        var result=service(db).check(shortRequest(2));
        assertEquals("ALLOW",result.decision());
        assertEquals("grant:71",result.authorizationId());
        assertEquals(40L,result.remainingQuota());
        assertEquals("2",result.policyVersion());
        assertEquals(OffsetDateTime.parse("2026-10-15T02:00:05Z"),result.expiresAt());
    }
    @Test void revokedGrantDenied() {
        StubJdbc db=new StubJdbc();db.row=active();db.row.put("grant_status","REVOKED");
        assertEquals("ACCESS_GRANT_INACTIVE",service(db).check(shortRequest(1)).reasonCode());
    }
    @Test void unpaidOrderDeniedEvenWithActiveService() {
        StubJdbc db=new StubJdbc();db.row=active();db.row.put("order_payment_status","UNVERIFIED");
        assertEquals("ORDER_PAYMENT_NOT_CONFIRMED",service(db).check(shortRequest(1)).reasonCode());
    }
    @Test void managementOnlyDeliveryIsNotRealProxyPermission() {
        StubJdbc db=new StubJdbc();db.row=active();db.row.put("delivery_stage","MOCK_ONLY");
        assertEquals("DELIVERY_NOT_VERIFIED",service(db).check(shortRequest(1)).reasonCode());
    }
    @Test void quotaExceededDenied() {
        StubJdbc db=new StubJdbc();db.row=active();db.used=99;
        assertEquals("EXTRACT_QUOTA_EXCEEDED",service(db).check(shortRequest(2)).reasonCode());
    }
    @Test void rateLimitDenied() {
        StubJdbc db=new StubJdbc();db.row=active();db.recent=20;
        assertEquals("EXTRACT_RATE_LIMIT",service(db).check(shortRequest(1)).reasonCode());
    }
    @Test void unsupportedConnectionActionsNeverInheritExtractPermission() {
        StubJdbc db=new StubJdbc();db.row=active();
        var req=new ProductAuthorizationService.CheckRequest("23","54","88","CONNECT",1,"test-2");
        assertEquals("ACTION_NOT_SUPPORTED",service(db).check(req).reasonCode());
    }
    @Test void missingIdentityAndCrossOrderDenied() {
        StubJdbc db=new StubJdbc();db.row=active();
        assertEquals("AUTH_REQUEST_INVALID",service(db).check(
            new ProductAuthorizationService.CheckRequest("","54","88","SHORT_EXTRACT",1,"bad")).reasonCode());
        assertEquals("ORDER_MISMATCH",service(db).check(
            new ProductAuthorizationService.CheckRequest("23","54","89","SHORT_EXTRACT",1,"bad")).reasonCode());
    }
    @Test void missingOrDisabledAccountDenied() {
        StubJdbc db=new StubJdbc();
        assertEquals("SUBJECT_SERVICE_NOT_FOUND",service(db).check(shortRequest(1)).reasonCode());
        db.row=active();db.row.put("account_status","DISABLED");
        assertEquals("ACCOUNT_DISABLED",service(db).check(shortRequest(1)).reasonCode());
    }
}
