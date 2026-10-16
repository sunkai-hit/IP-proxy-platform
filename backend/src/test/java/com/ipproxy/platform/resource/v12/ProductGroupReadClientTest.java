package com.ipproxy.platform.resource.v12;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipproxy.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProductGroupReadClientTest {
    private static final String KEY="product-read-only-secret-of-at-least-32-characters";
    private static final String TIME=java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString();

    @Test void gatewayDisabledWithoutUrlOrCredentialsAndCannotIssueRequests() {
        AtomicInteger requests=new AtomicInteger();
        ProductGroupReadClient gateway=new ProductGroupReadClient(
                "",KEY,new ObjectMapper(),(uri,key,request)->{requests.incrementAndGet();return null;});
        assertFalse(gateway.configured());
        assertEquals(false,gateway.integrationStatus().get("writesEnabled"));
        BusinessException err=assertThrows(BusinessException.class,gateway::shortPool);
        assertEquals("PRODUCT_INTEGRATION_NOT_CONFIGURED",err.getCode());
        assertEquals(0,requests.get());

        ProductGroupReadClient insecure=new ProductGroupReadClient(
                "http://public.example.org","tiny",new ObjectMapper(),(uri,key,id)->null);
        assertFalse(insecure.configured());
        ProductGroupReadClient localNoKey=new ProductGroupReadClient(
                "http://localhost:9876","",new ObjectMapper(),(uri,key,id)->null);
        assertFalse(localNoKey.configured());
    }

    @Test void liveShortPoolShowsUseCountButNeverLeaksUnexpectedCredentials() {
        AtomicInteger count=new AtomicInteger();
        ProductGroupReadClient gateway=new ProductGroupReadClient(
                "https://product.test",KEY,new ObjectMapper(),(uri,key,request)->{
                    assertEquals("https://product.test/product/v1/short-pool",uri.toString());
                    assertEquals(KEY,key);
                    assertFalse(request.isBlank());
                    count.incrementAndGet();
                    return new ProductGroupReadClient.Received(200,
                        "{\"resources\":[{\"resourceId\":\"r-7\",\"lineId\":\"line-5\",\"mode\":\"SHORT\",\"health\":\"ONLINE\",\"activeUserCount\":4,\"activeConnectionCount\":12,\"sampledAt\":\""+TIME+"\",\"password\":\"do-not-leak\"}],\"sampledAt\":\""+TIME+"\"}");
                });
        assertTrue(gateway.configured());
        Map<String,Object> result=gateway.shortPool();
        assertEquals("PRODUCT_GROUP_HTTP",result.get("source"));
        assertEquals(true,result.get("advisoryOnly"));
        assertEquals(1,count.get());
        @SuppressWarnings("unchecked")
        Map<String,Object> r=((java.util.List<Map<String,Object>>)result.get("resources")).getFirst();
        assertEquals(4,r.get("activeUserCount"));
        assertEquals(12,r.get("activeConnectionCount"));
        assertFalse(r.containsKey("password"));
    }

    @Test void longPoolReadsSharedCapacityWithoutTreatingSnapshotAsReservation() {
        ProductGroupReadClient gateway=new ProductGroupReadClient(
                "https://product.test",KEY,new ObjectMapper(),(uri,key,id)->
                    new ProductGroupReadClient.Received(200,
                        "{\"resources\":[{\"resourceId\":\"r-7\",\"lineId\":\"line-5\",\"mode\":\"LONG\",\"health\":\"ONLINE\",\"shareSlotsRemaining\":3,\"sampledAt\":\""+TIME+"\"}],\"sampledAt\":\""+TIME+"\"}"));
        Map<String,Object> r=gateway.longPool();
        assertEquals(true,r.get("advisoryOnly"));
        @SuppressWarnings("unchecked")
        Map<String,Object> resource=((java.util.List<Map<String,Object>>)r.get("resources")).getFirst();
        assertEquals(3,resource.get("shareSlotsRemaining"));
    }

    @Test void asynchronousAcceptanceOrTimeoutIsNotReadableSuccess() {
        ProductGroupReadClient accepted=new ProductGroupReadClient(
                "https://product.test",KEY,new ObjectMapper(),
                (url,key,id)->new ProductGroupReadClient.Received(202,"{\"status\":\"ACCEPTED\"}"));
        assertEquals("PRODUCT_INTEGRATION_UNAVAILABLE",
                assertThrows(BusinessException.class,accepted::capabilities).getCode());
        ProductGroupReadClient timeout=new ProductGroupReadClient(
                "https://product.test",KEY,new ObjectMapper(),
                (url,key,id)->{throw new java.net.http.HttpTimeoutException("timeout");});
        assertEquals("PRODUCT_INTEGRATION_UNAVAILABLE",
                assertThrows(BusinessException.class,timeout::shortPool).getCode());
    }

    @Test void invalidPayloadFailsClosedInsteadOfInventingUnknownPool() {
        ProductGroupReadClient bad=new ProductGroupReadClient(
                "https://product.test",KEY,new ObjectMapper(),
                (uri,key,id)->new ProductGroupReadClient.Received(200,
                        "{\"resources\":[{\"resourceId\":\"r-7\",\"mode\":\"SHORT\"}],\"sampledAt\":\""+TIME+"\"}"));
        assertEquals("PRODUCT_INTEGRATION_INVALID_RESPONSE",
                assertThrows(BusinessException.class,bad::shortPool).getCode());
    }

    @Test void actualServiceMustMatchRequestedIdAndRequiredVerificationFields() {
        ProductGroupReadClient mismatch=new ProductGroupReadClient(
                "https://product.test",KEY,new ObjectMapper(),
                (uri,key,id)->new ProductGroupReadClient.Received(200,
                  "{\"serviceId\":\"other\",\"actualStatus\":\"RUNNING\",\"verificationStatus\":\"VERIFIED\",\"sampledAt\":\""+TIME+"\"}"));
        assertEquals("PRODUCT_INTEGRATION_INVALID_RESPONSE",
                assertThrows(BusinessException.class,()->mismatch.actualService("service-7")).getCode());
        assertEquals("PRODUCT_RESOURCE_ID_INVALID",
                assertThrows(BusinessException.class,()->mismatch.actualService("../secrets")).getCode());
        ProductGroupReadClient ok=new ProductGroupReadClient(
                "https://product.test",KEY,new ObjectMapper(),
                (uri,key,id)->new ProductGroupReadClient.Received(200,
                  "{\"serviceId\":\"service-7\",\"actualStatus\":\"RUNNING\",\"verificationStatus\":\"NOT_VERIFIED\",\"sampledAt\":\""+TIME+"\"}"));
        assertEquals("NOT_VERIFIED",ok.actualService("service-7").get("verificationStatus"));
    }

    @Test void pendingTaskRemainsPendingAndUnknownIsNotRewrittenAsFailed() {
        ProductGroupReadClient gateway=new ProductGroupReadClient(
                "https://product.test",KEY,new ObjectMapper(),(uri,key,id)->{
                    assertEquals("https://product.test/product/v1/tasks/op-9",uri.toString());
                    return new ProductGroupReadClient.Received(200,
                            "{\"operationId\":\"op-9\",\"accepted\":true,\"status\":\"UNKNOWN\"}");
                });
        assertEquals("UNKNOWN",gateway.taskStatus("op-9").get("status"));
    }
    private ProductGroupReadClient bridge(String payload){
        return new ProductGroupReadClient("https://product.test",KEY,
                new ObjectMapper(),(uri,key,id)->new ProductGroupReadClient.Received(200,payload));
    }

    @Test void expiredPoolSamplesCannotBeUsedAsCurrentCapacity(){
        String old=java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).minusHours(2).toString();
        String payload="""
            {"resources":[{"resourceId":"r-1","lineId":"line-1","mode":"LONG",
            "health":"ONLINE","shareSlotsRemaining":5,"sampledAt":"%s"}],
            "sampledAt":"%s"}
            """.formatted(old,old);
        assertEquals("PRODUCT_INTEGRATION_STALE_SAMPLE",
                assertThrows(BusinessException.class,()->bridge(payload).longPool()).getCode());
    }

    @Test void recentHeaderWithStaleNestedDeviceSampleIsRejected(){
        String old=java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).minusHours(1).toString();
        String payload="""
            {"resources":[{"resourceId":"r-1","lineId":"line-1","mode":"SHORT",
            "health":"ONLINE","sampledAt":"%s"}],"sampledAt":"%s"}
            """.formatted(old,TIME);
        assertEquals("PRODUCT_INTEGRATION_STALE_SAMPLE",
                assertThrows(BusinessException.class,()->bridge(payload).shortPool()).getCode());
    }

    @Test void futureDatedSamplesCannotClaimCurrentVerification(){
        String future=java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(10).toString();
        String service="""
            {"serviceId":"service-7","actualStatus":"RUNNING","verificationStatus":"VERIFIED",
            "sampledAt":"%s"}
            """.formatted(future);
        assertEquals("PRODUCT_INTEGRATION_STALE_SAMPLE",
                assertThrows(BusinessException.class,()->bridge(service).actualService("service-7")).getCode());
        String capabilities="""
            {"productTypes":["SHORT_IP","LONG_IP"],"supportsFixedEgress":true,"sampledAt":"%s"}
            """.formatted(future);
        assertEquals("PRODUCT_INTEGRATION_STALE_SAMPLE",
                assertThrows(BusinessException.class,()->bridge(capabilities).capabilities()).getCode());
    }

    @Test void malformedCapacityCountsAndDuplicateResourceIdsFailClosed(){
        for(String badCount:java.util.List.of("-1","1.5","\"many\"","true")){
            String sample="""
                {"resources":[{"resourceId":"r-1","lineId":"line-1","mode":"LONG",
                "health":"ONLINE","activeUserCount":%s,"sampledAt":"%s"}],"sampledAt":"%s"}
                """.formatted(badCount,TIME,TIME);
            assertEquals("PRODUCT_INTEGRATION_INVALID_RESPONSE",
                    assertThrows(BusinessException.class,()->bridge(sample).longPool()).getCode());
        }
        String duplicate="""
            {"resources":[{"resourceId":"r-1","lineId":"l-1","mode":"SHORT",
              "health":"ONLINE","sampledAt":"%s"},
             {"resourceId":"r-1","lineId":"l-2","mode":"SHORT",
              "health":"ONLINE","sampledAt":"%s"}],"sampledAt":"%s"}
            """.formatted(TIME,TIME,TIME);
        assertEquals("PRODUCT_INTEGRATION_INVALID_RESPONSE",
                assertThrows(BusinessException.class,()->bridge(duplicate).shortPool()).getCode());
    }

    @Test void booleanCapabilityAndTerminalTaskAcceptanceMustMatchContract(){
        String badCapabilities="""
            {"productTypes":["SHORT_IP"],"supportsExclusiveIpv4":"true","sampledAt":"%s"}
            """.formatted(TIME);
        assertEquals("PRODUCT_INTEGRATION_INVALID_RESPONSE",
                assertThrows(BusinessException.class,()->bridge(badCapabilities).capabilities()).getCode());
        String contradiction="""
            {"operationId":"op-7","accepted":false,"status":"SUCCEEDED"}
            """;
        assertEquals("PRODUCT_INTEGRATION_INVALID_RESPONSE",
                assertThrows(BusinessException.class,()->bridge(contradiction).taskStatus("op-7")).getCode());
    }

}
