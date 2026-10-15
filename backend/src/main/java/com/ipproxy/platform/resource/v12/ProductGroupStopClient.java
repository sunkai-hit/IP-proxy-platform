package com.ipproxy.platform.resource.v12;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipproxy.platform.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Opt-in candidate product write adapter: ONLY one confirmed business action
 * (service-scoped expiry pause) can be sent. The caller MUST durably persist
 * a deterministic operationId before invoking this HTTP method. A 202 never
 * means that the service is stopped or the fixed egress is released.
 */
@Service
public class ProductGroupStopClient {
    private final URI base;
    private final String token;
    private final boolean writeEnabled;
    private final ObjectMapper json;
    private final Transport transport;

    public record Response(int status,String body){}
    @FunctionalInterface interface Transport {
        Response post(URI uri,String bearer,String operationId,String jsonBody) throws Exception;
    }
    public record Acceptance(String operationId,String status) {}

    @Autowired
    public ProductGroupStopClient(
          @Value("${app.integration.product-base-url:}") String configuredBase,
          @Value("${app.integration.product-api-token:}") String token,
          @Value("${app.integration.product-writes-enabled:false}") boolean writeEnabled,
          ObjectMapper json) {
        this(configuredBase,token,writeEnabled,json,ProductGroupStopClient::httpPost);
    }
    ProductGroupStopClient(String configuredBase,String token,boolean enabled,
                           ObjectMapper json,Transport transport) {
        this.base=base(configuredBase);this.token=token==null?"":token;
        this.writeEnabled=enabled;this.json=json;this.transport=transport;
    }
    public boolean ready(){return writeEnabled&&base!=null&&token.length()>=32;}
    public Acceptance pauseForExpiry(Long serviceId,String operationId,String effectiveExpireAt) {
        if(!ready())
            throw new BusinessException("PRODUCT_WRITE_DISABLED",
                    "产品组写接口未明确启用或未配置；不能执行真实到期停服");
        if(serviceId==null||serviceId<=0||operationId==null
                ||!operationId.matches("[A-Za-z0-9_-]{1,128}"))
            throw new BusinessException("PRODUCT_TASK_ID_INVALID","远程操作编号或服务ID不合法");
        try{
            Map<String,Object> payload=new LinkedHashMap<>();
            payload.put("action","PAUSE");
            payload.put("operationId",operationId);
            payload.put("reason","服务到期停服；只撤销当前服务授权，宽限保留固定IPv4及其他共享客户的访问");
            payload.put("effectiveAt",effectiveExpireAt);
            Response response=transport.post(
                    base.resolve("product/v1/services/"+serviceId+"/actions"),
                    token,operationId,json.writeValueAsString(payload));
            if(response.status()!=202 || response.body()==null
                  ||response.body().length()>65536)
                throw new BusinessException("PRODUCT_TASK_ACK_UNCERTAIN",
                        "产品组未返回有效的202受理回执；请按同一操作编号查单，禁止盲目重发");
            JsonNode receipt=json.readTree(response.body());
            if(receipt==null||!receipt.isObject()
                    ||!receipt.path("accepted").isBoolean()||!receipt.path("accepted").asBoolean()
                    ||!operationId.equals(receipt.path("operationId").asText())
                    ||!Set.of("ACCEPTED","PENDING","RUNNING","VERIFYING","SUCCEEDED")
                        .contains(receipt.path("status").asText()))
                throw new BusinessException("PRODUCT_TASK_ACK_UNCERTAIN",
                        "产品组受理回执未匹配本次操作编号；不得将其记为成功");
            return new Acceptance(operationId,receipt.path("status").asText());
        }catch(BusinessException e){throw e;}
        catch(Exception e){throw new BusinessException("PRODUCT_TASK_ACK_UNCERTAIN",
                    "产品组停服调用超时或响应异常，可能已执行；必须按原操作编号查单");}
    }
    private static Response httpPost(URI uri,String bearer,String opId,String body) throws Exception {
        HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpRequest req=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                .header("Authorization","Bearer "+bearer)
                .header("X-Operation-Id",opId)
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();
        HttpResponse<String> reply=http.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(reply.statusCode(),reply.body());
    }
    private static URI base(String raw){
        if(raw==null||raw.isBlank())return null;
        try{
            URI uri=URI.create(raw.trim());
            boolean https="https".equalsIgnoreCase(uri.getScheme());
            boolean local="http".equalsIgnoreCase(uri.getScheme())
                    && Set.of("localhost","127.0.0.1").contains(uri.getHost());
            if((!https&&!local)||uri.getHost()==null||uri.getUserInfo()!=null
                    ||uri.getRawQuery()!=null||uri.getRawFragment()!=null
                    ||!(uri.getPath().isBlank()||"/".equals(uri.getPath())))return null;
            return URI.create(uri.toString().replaceAll("/+$","")+"/");
        }catch(Exception e){return null;}
    }
}
