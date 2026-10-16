package com.ipproxy.platform.resource.v12;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipproxy.platform.common.exception.BusinessException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real loopback HTTP wire test for the pre-agreed two-team interface.
 * The local response fixture is NOT proxy equipment or product-team acceptance.
 */
class ProductGroupHttpWireTest {
    private static final String TOKEN="product-group-ci-wire-key-012345678901234567";
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final String TIME=java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString();

    @Test void actualHttpPauseAndTaskQueryPreserveAsyncBoundary() throws Exception {
        AtomicInteger postCount=new AtomicInteger();
        AtomicReference<String> receivedId=new AtomicReference<>();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/product/v1/",exchange->{
            try {
                assertEquals("Bearer "+TOKEN,exchange.getRequestHeaders().getFirst("Authorization"));
                String path=exchange.getRequestURI().getPath();
                if("POST".equals(exchange.getRequestMethod())
                        &&path.equals("/product/v1/services/23/actions")) {
                    JsonNode body=JSON.readTree(exchange.getRequestBody());
                    assertEquals("PAUSE",body.path("action").asText());
                    String operation=body.path("operationId").asText();
                    assertEquals(operation,exchange.getRequestHeaders().getFirst("X-Operation-Id"));
                    assertEquals(operation,exchange.getRequestHeaders().getFirst("X-Request-Id"));
                    assertEquals(operation,exchange.getRequestHeaders().getFirst("Idempotency-Key"));
                    assertEquals("2026-10-14T00:00:00Z",body.path("effectiveAt").asText());
                    receivedId.set(operation);
                    postCount.incrementAndGet();
                    respond(exchange,202,JSON.writeValueAsString(Map.of(
                            "operationId",operation,"accepted",true,"status","ACCEPTED")));
                } else if("GET".equals(exchange.getRequestMethod())
                        &&path.equals("/product/v1/tasks/expiry-stop-23")) {
                    respond(exchange,200,JSON.writeValueAsString(Map.of(
                            "operationId","expiry-stop-23","accepted",true,"status","SUCCEEDED",
                            "verifiedAt",TIME)));
                } else if("GET".equals(exchange.getRequestMethod())
                        &&path.equals("/product/v1/services/23")) {
                    respond(exchange,200,JSON.writeValueAsString(Map.of(
                            "serviceId","23","actualStatus","STOPPED",
                            "verificationStatus","VERIFIED","verifiedAt",TIME,"sampledAt",TIME)));
                } else respond(exchange,404,"{}");
            }catch(Throwable e){respond(exchange,500,"{}");}
        });
        server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            ProductGroupStopClient stop=new ProductGroupStopClient(base,TOKEN,true,JSON);
            ProductGroupReadClient read=new ProductGroupReadClient(base,TOKEN,JSON);
            assertEquals("ACCEPTED",
                stop.pauseForExpiry(23L,"expiry-stop-23","2026-10-14T00:00:00Z").status());
            assertEquals("expiry-stop-23",receivedId.get());
            assertEquals(1,postCount.get());
            assertEquals("SUCCEEDED",read.taskStatus("expiry-stop-23").get("status"));
            assertEquals("VERIFIED",read.actualService("23").get("verificationStatus"));
            // These reads only verify protocol compatibility; no business
            // service is marked EXPIRED and no IPv4 claim is freed here.
        }finally {server.stop(0);}
    }

    @Test void actualHttpReleaseVerifiesOperationIdAndReadsPerServiceProof() throws Exception {
        AtomicInteger postCount=new AtomicInteger();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/product/v1/",exchange->{
            try {
                String path=exchange.getRequestURI().getPath();
                assertEquals("Bearer "+TOKEN,exchange.getRequestHeaders().getFirst("Authorization"));
                if("POST".equals(exchange.getRequestMethod())
                        &&path.equals("/product/v1/services/54/actions")) {
                    JsonNode body=JSON.readTree(exchange.getRequestBody());
                    assertEquals("RELEASE",body.path("action").asText());
                    assertEquals("UNBIND_SERVICE_ONLY",body.path("releaseMode").asText());
                    assertEquals("198.51.100.7",body.path("egressIpv4").asText());
                    assertEquals("release-54",exchange.getRequestHeaders().getFirst("X-Operation-Id"));
                    assertEquals("release-54",exchange.getRequestHeaders().getFirst("X-Request-Id"));
                    assertEquals("release-54",exchange.getRequestHeaders().getFirst("Idempotency-Key"));
                    postCount.incrementAndGet();
                    respond(exchange,202,"{\"operationId\":\"release-54\",\"accepted\":true,\"status\":\"ACCEPTED\"}");
                } else if("GET".equals(exchange.getRequestMethod())
                        &&path.equals("/product/v1/tasks/release-54")) {
                    respond(exchange,200,JSON.writeValueAsString(Map.of(
                            "operationId","release-54","accepted",true,"status","SUCCEEDED",
                            "releaseVerification",Map.of(
                                "releaseMode","UNBIND_SERVICE_ONLY",
                                "egressIpv4","198.51.100.7",
                                "serviceAccessRevoked",true,"verifiedAt",TIME))));
                } else respond(exchange,404,"{}");
            }catch(Throwable e){respond(exchange,500,"{}");}
        });
        server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            ProductGroupStopClient stop=new ProductGroupStopClient(base,TOKEN,true,JSON);
            ProductGroupReadClient read=new ProductGroupReadClient(base,TOKEN,JSON);
            assertEquals("ACCEPTED",stop.releaseAfterGrace(
                    54L,"release-54","UNBIND_SERVICE_ONLY","198.51.100.7").status());
            assertEquals(1,postCount.get());
            Map<String,Object> task=read.taskStatus("release-54");
            assertEquals("SUCCEEDED",task.get("status"));
            @SuppressWarnings("unchecked")
            Map<String,Object> proof=(Map<String,Object>)task.get("releaseVerification");
            assertEquals(true,proof.get("serviceAccessRevoked"));
            assertEquals("UNBIND_SERVICE_ONLY",proof.get("releaseMode"));
        }finally {server.stop(0);}
    }

    @Test void actualHttpRejectionNeverBecomesAccepted() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/product/v1/services/11/actions",exchange->
                respond(exchange,409,"{\"operationId\":\"release-11\",\"accepted\":false,\"status\":\"FAILED\"}"));
        server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            ProductGroupStopClient stop=new ProductGroupStopClient(base,TOKEN,true,JSON);
            BusinessException e=assertThrows(BusinessException.class,
                    ()->stop.releaseAfterGrace(11L,"release-11","UNBIND_SERVICE_ONLY","198.51.100.8"));
            assertEquals("PRODUCT_TASK_ACK_UNCERTAIN",e.getCode());
        }finally {server.stop(0);}
    }

    private static void respond(HttpExchange exchange,int status,String content) {
        try {
            byte[] body=content.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(status,body.length);
            try(var out=exchange.getResponseBody()){out.write(body);}
        }catch(Exception ignored){exchange.close();}
    }
}
