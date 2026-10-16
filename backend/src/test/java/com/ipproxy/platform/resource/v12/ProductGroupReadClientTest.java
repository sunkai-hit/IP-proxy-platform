package com.ipproxy.platform.resource.v12;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipproxy.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProductGroupReadClientTest {
    private static final String KEY="product-read-only-secret-of-at-least-32-characters";
    private static final String TIME="2026-10-15T02:00:00Z";

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
}
