package com.ipproxy.platform.resource.v12;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipproxy.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ProductGroupStopClientTest {
    static final String TOKEN="this-is-a-private-product-server-token-123456789";
    static final String OP="op-21-20261015";

    @Test void productionWriteFlagIsOffByDefault(){
        AtomicInteger calls=new AtomicInteger();
        ProductGroupStopClient client=new ProductGroupStopClient(
            "https://proxy.example.test",TOKEN,false,new ObjectMapper(),
            (uri,key,id,body)->{calls.incrementAndGet();return null;});
        assertFalse(client.ready());
        BusinessException e=assertThrows(BusinessException.class,
            ()->client.pauseForExpiry(21L,OP,"2026-10-15T00:00:00Z"));
        assertEquals("PRODUCT_WRITE_DISABLED",e.getCode());
        assertEquals(0,calls.get());
    }

    @Test void onlyExplicit202MatchingOperationIdIsAccepted(){
        ProductGroupStopClient client=new ProductGroupStopClient(
            "https://proxy.example.test",TOKEN,true,new ObjectMapper(),
            (uri,key,id,body)->{
                assertEquals("https://proxy.example.test/product/v1/services/21/actions",uri.toString());
                assertEquals(TOKEN,key);
                assertEquals(OP,id);
                assertTrue(body.contains("\"action\":\"PAUSE\""));
                assertTrue(body.contains("\"operationId\":\""+OP+"\""));
                assertTrue(body.contains("只撤销当前服务授权"));
                return new ProductGroupStopClient.Response(202,
                    "{\"operationId\":\""+OP+"\",\"accepted\":true,\"status\":\"ACCEPTED\"}");
            });
        assertEquals("ACCEPTED",client.pauseForExpiry(21L,OP,"2026-10-15T00:00:00Z").status());
    }

    @Test void mismatchedReceiptOr200CannotBeMistakenForDelivery(){
        ProductGroupStopClient mismatch=new ProductGroupStopClient(
            "https://proxy.example.test",TOKEN,true,new ObjectMapper(),
            (uri,key,id,body)->new ProductGroupStopClient.Response(202,
                "{\"operationId\":\"wrong\",\"accepted\":true,\"status\":\"SUCCEEDED\"}"));
        assertEquals("PRODUCT_TASK_ACK_UNCERTAIN",assertThrows(BusinessException.class,
            ()->mismatch.pauseForExpiry(21L,OP,"2026-10-15T00:00:00Z")).getCode());
        ProductGroupStopClient status200=new ProductGroupStopClient(
            "https://proxy.example.test",TOKEN,true,new ObjectMapper(),
            (uri,key,id,body)->new ProductGroupStopClient.Response(200,
                "{\"operationId\":\""+OP+"\",\"accepted\":true,\"status\":\"SUCCEEDED\"}"));
        assertEquals("PRODUCT_TASK_ACK_UNCERTAIN",assertThrows(BusinessException.class,
            ()->status200.pauseForExpiry(21L,OP,"2026-10-15T00:00:00Z")).getCode());
    }

    @Test void releaseUsesSameOptInKeyAndDoesNotTreat202AsCompletion(){
        ProductGroupStopClient disabled=new ProductGroupStopClient(
            "https://proxy.example.test",TOKEN,false,new ObjectMapper(),
            (uri,key,id,body)->{fail("feature disabled");return null;});
        assertEquals("PRODUCT_WRITE_DISABLED",assertThrows(BusinessException.class,
            ()->disabled.releaseAfterGrace(21L,OP,"UNBIND_SERVICE_ONLY","198.51.100.7")).getCode());
        ProductGroupStopClient enabled=new ProductGroupStopClient(
            "https://proxy.example.test",TOKEN,true,new ObjectMapper(),
            (uri,key,id,body)->{
                assertEquals("https://proxy.example.test/product/v1/services/21/actions",uri.toString());
                assertTrue(body.contains("\"action\":\"RELEASE\""));
                assertTrue(body.contains("\"releaseMode\":\"UNBIND_SERVICE_ONLY\""));
                assertTrue(body.contains("\"egressIpv4\":\"198.51.100.7\""));
                return new ProductGroupStopClient.Response(202,
                    "{\"operationId\":\""+id+"\",\"accepted\":true,\"status\":\"ACCEPTED\"}");
            });
        assertEquals("ACCEPTED",enabled.releaseAfterGrace(
                21L,OP,"UNBIND_SERVICE_ONLY","198.51.100.7").status());
    }
    @Test void releaseMismatchedRemoteReceiptCannotUnlockIpv4(){
        ProductGroupStopClient client=new ProductGroupStopClient(
            "https://proxy.example.test",TOKEN,true,new ObjectMapper(),
            (uri,key,id,body)->new ProductGroupStopClient.Response(202,
                "{\"operationId\":\"another-operation\",\"accepted\":true,\"status\":\"SUCCEEDED\"}"));
        assertEquals("PRODUCT_TASK_ACK_UNCERTAIN",assertThrows(BusinessException.class,
            ()->client.releaseAfterGrace(21L,OP,"RETURN_TO_SHORT_AFTER_VERIFICATION","198.51.100.7")).getCode());
    }
    @Test void timeoutAndPublicHttpEndpointFailClosed(){
        ProductGroupStopClient timeout=new ProductGroupStopClient(
            "https://proxy.example.test",TOKEN,true,new ObjectMapper(),
            (uri,key,id,body)->{throw new java.net.http.HttpTimeoutException("ambiguous");});
        assertEquals("PRODUCT_TASK_ACK_UNCERTAIN",assertThrows(BusinessException.class,
            ()->timeout.pauseForExpiry(21L,OP,"2026-10-15T00:00:00Z")).getCode());
        ProductGroupStopClient unsafe=new ProductGroupStopClient(
            "http://public.example.org",TOKEN,true,new ObjectMapper(),
            (uri,key,id,body)->{fail("Must not call unsafe URL");return null;});
        assertFalse(unsafe.ready());
    }
    @Test void releaseInvalidIpv4NeverReachesTheProductGateway(){
        AtomicInteger calls=new AtomicInteger();
        ProductGroupStopClient client=new ProductGroupStopClient(
                "https://proxy.example.test",TOKEN,true,new ObjectMapper(),
                (uri,key,id,body)->{calls.incrementAndGet();return new ProductGroupStopClient.Response(202,"{}");});
        for(String invalid:java.util.List.of("999.999.999.999","192.168.001.1",
                "198.51.100","198.51.100.7.8","198..100.7","abc.51.100.7")){
            assertEquals("PRODUCT_RELEASE_INVALID",assertThrows(BusinessException.class,
                    ()->client.releaseAfterGrace(21L,OP,"UNBIND_SERVICE_ONLY",invalid)).getCode(),invalid);
        }
        assertEquals(0,calls.get());
    }

    @Test void emptyOrMalformedStopEffectiveTimeNeverReachesRemoteNetwork(){
        AtomicInteger calls=new AtomicInteger();
        ProductGroupStopClient client=new ProductGroupStopClient(
                "https://proxy.example.test",TOKEN,true,new ObjectMapper(),
                (uri,key,id,body)->{calls.incrementAndGet();return new ProductGroupStopClient.Response(202,"{}");});
        for(String invalid:java.util.List.of("","2026-10-15","2026-13-45T00:00:00Z","not-a-time")){
            assertEquals("PRODUCT_TASK_EFFECTIVE_AT_INVALID",assertThrows(BusinessException.class,
                    ()->client.pauseForExpiry(21L,OP,invalid)).getCode());
        }
        assertEquals("PRODUCT_TASK_EFFECTIVE_AT_INVALID",assertThrows(BusinessException.class,
                ()->client.pauseForExpiry(21L,OP,null)).getCode());
        assertEquals(0,calls.get());
    }

}
