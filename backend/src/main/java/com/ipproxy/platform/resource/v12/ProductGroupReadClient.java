package com.ipproxy.platform.resource.v12;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipproxy.platform.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Read-only product service bridge. The external product team owns live resource
 * and proxy state; this bridge never mutates a pool or treats a read as a lock.
 * Fail closed on missing configuration, transport/HTTP errors or bad payloads.
 */
@Service
public class ProductGroupReadClient {
    private static final Pattern SAFE_ID=Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final Set<String> RESOURCE_FIELDS=Set.of(
            "resourceId","lineId","deviceId","rosId","centosId","publicIpv4","mode","health",
            "region","operator","activeUserCount","activeConnectionCount","utilization",
            "maxCapacity","shareSlotsRemaining","convertible","sampledAt","resourceVersion");
    private static final Set<String> CAPABILITY_FIELDS=Set.of(
            "productTypes","supportsModeConversion","supportsShortMigration","supportsFixedEgress",
            "supportsExclusiveIpv4","supportsProxyAuth","supportsIdempotentTasks","sampledAt");
    private static final Set<String> SERVICE_FIELDS=Set.of(
            "serviceId","actualStatus","verificationStatus","egressIpv4","verifiedAt","sampledAt");
    private static final Set<String> TASK_FIELDS=Set.of(
            "operationId","taskId","accepted","status","stage","remoteId","verifiedAt","errorCode");
    private final URI base;
    private final String bearer;
    private final ObjectMapper mapper;
    private final Transport transport;

    public record Received(int status,String body){}
    @FunctionalInterface interface Transport {
        Received get(URI url,String token,String requestId) throws Exception;
    }
    public ProductGroupReadClient(
            @Value("${app.integration.product-base-url:}") String configuredBase,
            @Value("${app.integration.product-api-token:}") String token,
            ObjectMapper mapper) {
        this(configuredBase,token,mapper,ProductGroupReadClient::httpGet);
    }
    ProductGroupReadClient(String configuredBase,String token,ObjectMapper mapper,Transport transport) {
        this.base=parseBase(configuredBase);
        this.bearer=token==null?"":token;
        this.mapper=mapper;
        this.transport=transport;
    }

    public boolean configured(){return base!=null&&bearer.length()>=32;}
    public Map<String,Object> integrationStatus(){
        return Map.of("configured",configured(),"source","PRODUCT_GROUP_HTTP",
                "readOnly",true,"writesEnabled",false,
                "note",configured()?"产品组读取接口已配置；不代表真实设备在线或支持转换":
                    "产品组接口未配置，禁止把管理平台缓存当作真实资源状态");
    }

    public Map<String,Object> capabilities(){
        JsonNode root=fetch("/product/v1/capabilities");
        if(!root.path("productTypes").isArray()||!validTime(root,"sampledAt"))
            throw invalid();
        return clean(root,CAPABILITY_FIELDS);
    }
    public Map<String,Object> shortPool(){return pool("/product/v1/short-pool");}
    public Map<String,Object> longPool(){return pool("/product/v1/long-pool");}
    public Map<String,Object> actualService(String id){
        JsonNode root=fetch("/product/v1/services/"+checkedId(id));
        if(!id.equals(text(root,"serviceId"))||!validTime(root,"sampledAt")
                ||!Set.of("RUNNING","STOPPED","PENDING","ERROR","UNKNOWN")
                        .contains(text(root,"actualStatus"))
                ||!Set.of("VERIFIED","FAILED","NOT_VERIFIED","UNKNOWN")
                        .contains(text(root,"verificationStatus")))
            throw invalid();
        // Read status only. VERIFIED is the product team's statement, not a
        // business-platform acceptance or a permission to release a claim.
        return clean(root,SERVICE_FIELDS);
    }
    public Map<String,Object> taskStatus(String operationId){
        JsonNode root=fetch("/product/v1/tasks/"+checkedId(operationId));
        if(!operationId.equals(text(root,"operationId"))
                ||!Set.of("ACCEPTED","PENDING","RUNNING","VERIFYING","SUCCEEDED",
                        "FAILED","COMPENSATION_REQUIRED","UNKNOWN").contains(text(root,"status"))
                ||!root.path("accepted").isBoolean())
            throw invalid();
        return clean(root,TASK_FIELDS);
    }

    private Map<String,Object> pool(String path){
        JsonNode root=fetch(path);
        if(!root.path("resources").isArray()||!validTime(root,"sampledAt"))throw invalid();
        List<Map<String,Object>> resources=new ArrayList<>();
        for(JsonNode item:root.path("resources")){
            if(!item.isObject()||text(item,"resourceId").isBlank()||text(item,"lineId").isBlank()
                    ||text(item,"mode").isBlank()||text(item,"health").isBlank()
                    ||!validTime(item,"sampledAt"))throw invalid();
            resources.add(clean(item,RESOURCE_FIELDS));
        }
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("resources",resources);
        result.put("sampledAt",root.path("sampledAt").asText());
        result.put("nextCursor",root.path("nextCursor").isTextual()?root.path("nextCursor").asText():null);
        result.put("source","PRODUCT_GROUP_HTTP");
        result.put("advisoryOnly",true); // not a resource reservation
        return result;
    }

    private JsonNode fetch(String path){
        if(!configured())throw new BusinessException("PRODUCT_INTEGRATION_NOT_CONFIGURED",
                "产品组只读接口未配置或专用密钥不足32字符，不能使用缓存替代真实资源");
        try {
            Received response=transport.get(base.resolve(path.substring(1)),bearer,UUID.randomUUID().toString());
            if(response.status()!=200)
                throw new BusinessException("PRODUCT_INTEGRATION_UNAVAILABLE",
                        "产品组接口未返回成功的真实读取结果（HTTP "+response.status()+"）");
            if(response.body()==null||response.body().length()>524288)throw invalid();
            JsonNode json=mapper.readTree(response.body());
            if(json==null||!json.isObject())throw invalid();
            return json;
        }catch(BusinessException e){throw e;}
        catch(Exception e){throw new BusinessException("PRODUCT_INTEGRATION_UNAVAILABLE",
                "产品组读取超时或通讯失败；未确认资源实际状态");}
    }

    private static Received httpGet(URI uri,String bearer,String requestId) throws Exception {
        HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpRequest request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                .header("Authorization","Bearer "+bearer)
                .header("X-Request-Id",requestId)
                .header("Accept","application/json").GET().build();
        HttpResponse<String> response=http.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Received(response.statusCode(),response.body());
    }
    private static URI parseBase(String value){
        if(value==null||value.isBlank())return null;
        try{
            URI parsed=URI.create(value.trim());
            boolean secure="https".equalsIgnoreCase(parsed.getScheme());
            boolean local="http".equalsIgnoreCase(parsed.getScheme())
                    && Set.of("localhost","127.0.0.1").contains(parsed.getHost());
            if((!secure&&!local)||parsed.getHost()==null||parsed.getUserInfo()!=null
                    ||parsed.getRawQuery()!=null||parsed.getRawFragment()!=null
                    ||!(parsed.getPath().isBlank()||"/".equals(parsed.getPath())))
                return null;
            return URI.create(parsed.toString().replaceAll("/+$","")+"/");
        }catch(Exception e){return null;}
    }
    private static String checkedId(String s) {
        if(s==null||!SAFE_ID.matcher(s).matches())
            throw new BusinessException("PRODUCT_RESOURCE_ID_INVALID","产品组对象标识不合法");
        return s;
    }
    private static String text(JsonNode root,String field){
        JsonNode value=root.path(field);
        return value.isTextual()?value.asText():"";
    }
    private static boolean validTime(JsonNode root,String field) {
        try{OffsetDateTime.parse(text(root,field));return true;}
        catch(Exception e){return false;}
    }
    private Map<String,Object> clean(JsonNode root,Set<String> allowed){
        Map<String,Object> values=new LinkedHashMap<>();
        for(String name:allowed){
            JsonNode value=root.get(name);
            if(value!=null&&!value.isNull())
                values.put(name,mapper.convertValue(value,Object.class));
        }
        return values;
    }
    private static BusinessException invalid(){
        return new BusinessException("PRODUCT_INTEGRATION_INVALID_RESPONSE",
                "产品组响应缺失必填字段或数据不符合接口契约，不允许以缓存替代");
    }
}
