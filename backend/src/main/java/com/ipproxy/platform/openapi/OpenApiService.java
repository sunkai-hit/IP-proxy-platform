package com.ipproxy.platform.openapi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.JwtProperties;
import org.slf4j.MDC;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class OpenApiService {
    private final OpenApiMapper db;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper json;
    private final JwtProperties jwt;

    public OpenApiService(OpenApiMapper db,PasswordEncoder passwordEncoder,ObjectMapper json,JwtProperties jwt){this.db=db;this.passwordEncoder=passwordEncoder;this.json=json;this.jwt=jwt;}

    public record TokenResult(String token,OffsetDateTime expiresAt,boolean changed,String serviceNo){}
    public record Access(Long grantId,Long accountId,Long customerId,Long serviceId,Long productId,String serviceNo,String productType,OffsetDateTime expireAt,Map<String,Object> policy){}

    @Transactional
    public TokenResult issueToken(String username,String password,boolean changeToken,String clientIp){
        long start=System.nanoTime(); Long customerId=null,accountId=null,serviceId=null;
        try{
            if(blank(username)||blank(password))throw err("OPEN_AUTH_REQUIRED","用户名和密码不能为空");
            Map<String,Object> a=db.account(username.trim());
            if(a==null||!passwordEncoder.matches(password,s(a.get("password_hash"))))throw err("OPEN_AUTH_INVALID","用户名或密码错误");
            accountId=lv(a.get("id")); customerId=lv(a.get("customer_id"));
            if(!"ACTIVE".equals(s(a.get("account_status")))||!"ACTIVE".equals(s(a.get("customer_status"))))throw err("OPEN_ACCOUNT_DISABLED","客户或账号当前不可用");
            List<Map<String,Object>> services=db.eligibleServices(customerId);
            if(services.isEmpty())throw err("OPEN_SERVICE_NOT_AVAILABLE","当前账号没有可用的API提取服务");
            if(services.size()>1)throw err("OPEN_SERVICE_AMBIGUOUS","当前账号存在多个可用API提取服务，需由运营侧明确默认服务后再获取Token");
            Map<String,Object> svc=services.getFirst(); serviceId=lv(svc.get("service_id"));
            Map<String,Object> grant=db.grant(accountId,serviceId); long version; Long grantId; boolean changed=false;
            if(grant==null){
                grantId=db.insertGrant(accountId,customerId,serviceId,j(policySnapshot(svc))); version=1; changed=true;
            }else{
                grantId=lv(grant.get("id")); version=((Number)grant.get("token_version")).longValue();
                if(changeToken||!"ACTIVE".equals(s(grant.get("status")))){version=db.rotateGrant(grantId);changed=true;}
            }
            String token=token(grantId,version);
            OffsetDateTime expire=offset(svc.get("expire_at"));
            safeApi(requestId(),customerId,accountId,serviceId,"POST","/api/open/v1/auth/token","{}",clientIp,200,"SUCCESS",null,null,ms(start));
            return new TokenResult(token,expire,changed,s(svc.get("service_no")));
        }catch(BusinessException e){safeApi(requestId(),customerId,accountId,serviceId,"POST","/api/open/v1/auth/token","{}",clientIp,400,"FAILURE",e.getCode(),e.getMessage(),ms(start));throw e;}
    }

    public Access authenticate(String rawToken){
        if(blank(rawToken))throw err("OPEN_TOKEN_REQUIRED","缺少Token");
        String[] p=rawToken.trim().split("\\.");
        if(p.length!=4||!"oa".equals(p[0]))throw err("OPEN_TOKEN_INVALID","Token格式无效");
        Long grantId; long version;
        try{grantId=Long.parseLong(p[1]);version=Long.parseLong(p[2]);}catch(Exception e){throw err("OPEN_TOKEN_INVALID","Token格式无效");}
        String body=p[0]+"."+p[1]+"."+p[2]; String expected=sign(body);
        if(!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),p[3].getBytes(StandardCharsets.UTF_8)))throw err("OPEN_TOKEN_INVALID","Token签名无效");
        Map<String,Object> c=db.accessContext(grantId);
        if(c==null||!"ACTIVE".equals(s(c.get("grant_status")))||((Number)c.get("token_version")).longValue()!=version)throw err("OPEN_TOKEN_EXPIRED","Token已失效");
        if(!"ACTIVE".equals(s(c.get("account_status")))||!"ACTIVE".equals(s(c.get("customer_status"))))throw err("OPEN_ACCOUNT_DISABLED","客户或账号当前不可用");
        if(!"ACTIVE".equals(s(c.get("service_status"))))throw err("OPEN_SERVICE_NOT_ACTIVE","服务当前不可用");
        OffsetDateTime eff=offset(c.get("effective_at")),exp=offset(c.get("expire_at")),now=OffsetDateTime.now(ZoneOffset.UTC);
        if(eff!=null&&eff.isAfter(now))throw err("OPEN_SERVICE_NOT_EFFECTIVE","服务尚未生效");
        if(exp!=null&&!exp.isAfter(now))throw err("OPEN_SERVICE_EXPIRED","服务已到期");
        if(!"API_EXTRACT".equals(s(c.get("access_mode_code"))))throw err("OPEN_SERVICE_MODE_INVALID","当前服务不是API提取模式");
        db.touchGrant(grantId);
        return new Access(grantId,lv(c.get("account_id")),lv(c.get("customer_id")),lv(c.get("service_id")),lv(c.get("product_id")),s(c.get("service_no")),s(c.get("product_type")),exp,parse(s(c.get("policy_snapshot_json"))));
    }

    @Transactional
    public Map<String,Object> extract(String rawToken,int amount,String protocol,String region,String carrier,String clientIp){
        long start=System.nanoTime(); Access a=null; int returned=0; String result="FAILURE",errorCode=null,errorMessage=null; List<Map<String,Object>> items=new ArrayList<>(); Map<String,Object> resourceSummary=new LinkedHashMap<>();
        protocol=blank(protocol)?"HTTP":protocol.trim().toUpperCase(); region=n(region); carrier=n(carrier);
        try{
            a=authenticate(rawToken);
            int single=intv(a.policy().get("singleExtractLimit"),100);
            if(amount<1||amount>single)throw err("OPEN_EXTRACT_AMOUNT_INVALID","提取数量必须在1到"+single+"之间");
            long quota=longv(a.policy().get("extractQuota"),0); long used=db.extractedCount(a.serviceId());
            if(quota>0&&used+amount>quota)throw err("OPEN_EXTRACT_QUOTA_EXCEEDED","服务提取额度不足");
            int rate=intv(a.policy().get("extractRateLimit"),0),window=intv(a.policy().get("extractRateWindowSeconds"),60);
            if(rate>0&&db.recentExtractRequests(a.serviceId(),window)>=rate)throw err("OPEN_EXTRACT_RATE_LIMIT","提取频率超过服务限制");
            Map<String,Object> pool=db.primaryPool(a.serviceId()); if(pool==null)throw err("OPEN_RESOURCE_POOL_UNAVAILABLE","服务没有可用资源池");
            int dedup=intv(a.policy().get("dedupPeriodSeconds"),0),valid=intv(a.policy().get("ipValidSeconds"),0);
            List<Map<String,Object>> ips=db.selectIps(lv(pool.get("pool_id")),a.serviceId(),amount,region,carrier,dedup);
            OffsetDateTime expires=valid>0?OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(valid):null;
            for(Map<String,Object> x:ips){Map<String,Object> it=new LinkedHashMap<>();it.put("ip",s(x.get("ip")));it.put("port",null);it.put("region",x.get("region_code"));it.put("carrier",x.get("carrier_code"));it.put("expiresAt",expires);items.add(it);}
            returned=items.size(); if(returned==0)throw err("OPEN_RESOURCE_UNAVAILABLE","当前筛选条件下没有可提取IP");
            result=returned==amount?"SUCCESS":"PARTIAL";resourceSummary.put("poolId",pool.get("pool_id"));resourceSummary.put("poolCode",pool.get("pool_code"));resourceSummary.put("dedupPeriodSeconds",dedup);
            Map<String,Object> out=new LinkedHashMap<>();out.put("count",returned);out.put("protocol",protocol);out.put("items",items);out.put("quotaUsed",used+returned);out.put("quotaTotal",quota==0?null:quota);
            return out;
        }catch(BusinessException e){errorCode=e.getCode();errorMessage=e.getMessage();throw e;}
        finally{
            if(a!=null){try{db.insertExtractLog(requestId(),a.customerId(),a.accountId(),a.serviceId(),a.productId(),clientIp,protocol,region,carrier,amount,returned,j(items),j(resourceSummary),result,errorCode,errorMessage,ms(start));}catch(Exception ignored){}
                safeApi(requestId(),a.customerId(),a.accountId(),a.serviceId(),"GET","/api/open/v1/proxy/extract",j(Map.of("amount",amount,"protocol",protocol,"regionCode",region,"carrierCode",carrier)),clientIp,errorCode==null?200:400,errorCode==null?"SUCCESS":"FAILURE",errorCode,errorMessage,ms(start));}
            else safeApi(requestId(),null,null,null,"GET","/api/open/v1/proxy/extract",j(Map.of("amount",amount,"protocol",protocol,"regionCode",region,"carrierCode",carrier)),clientIp,400,"FAILURE",errorCode,errorMessage,ms(start));
        }
    }

    public List<Map<String,Object>> whitelist(String rawToken,String clientIp){long start=System.nanoTime();Access a=null;try{a=authenticate(rawToken);List<Map<String,Object>> out=db.whitelist(a.serviceId());safeApi(requestId(),a.customerId(),a.accountId(),a.serviceId(),"GET","/api/open/v1/whitelist","{}",clientIp,200,"SUCCESS",null,null,ms(start));return out;}catch(BusinessException e){safeApi(requestId(),a==null?null:a.customerId(),a==null?null:a.accountId(),a==null?null:a.serviceId(),"GET","/api/open/v1/whitelist","{}",clientIp,400,"FAILURE",e.getCode(),e.getMessage(),ms(start));throw e;}}

    @Transactional public Map<String,Object> addWhitelist(String rawToken,String ip,String clientIp){long start=System.nanoTime();Access a=null;try{a=authenticate(rawToken);if(blank(ip))throw err("OPEN_WHITELIST_IP_REQUIRED","白名单IP不能为空");long limit=longv(a.policy().get("whitelistLimit"),0);if(limit>0&&db.whitelistCount(a.serviceId())>=limit)throw err("OPEN_WHITELIST_LIMIT","白名单数量已达到套餐上限");Long id;try{id=db.addWhitelist(a.serviceId(),ip.trim());}catch(Exception e){throw err("OPEN_WHITELIST_IP_INVALID","白名单IP格式无效");}Map<String,Object> out=new LinkedHashMap<>();out.put("id",id);out.put("ip",ip.trim());safeApi(requestId(),a.customerId(),a.accountId(),a.serviceId(),"POST","/api/open/v1/whitelist",j(Map.of("ip",ip.trim())),clientIp,200,"SUCCESS",null,null,ms(start));return out;}catch(BusinessException e){safeApi(requestId(),a==null?null:a.customerId(),a==null?null:a.accountId(),a==null?null:a.serviceId(),"POST","/api/open/v1/whitelist","{}",clientIp,400,"FAILURE",e.getCode(),e.getMessage(),ms(start));throw e;}}

    @Transactional public void deleteWhitelist(String rawToken,String ip,String clientIp){long start=System.nanoTime();Access a=null;try{a=authenticate(rawToken);try{if(db.deleteWhitelist(a.serviceId(),ip)==0)throw err("OPEN_WHITELIST_NOT_FOUND","白名单记录不存在");}catch(BusinessException e){throw e;}catch(Exception e){throw err("OPEN_WHITELIST_IP_INVALID","白名单IP格式无效");}safeApi(requestId(),a.customerId(),a.accountId(),a.serviceId(),"DELETE","/api/open/v1/whitelist/{ip}",j(Map.of("ip",ip)),clientIp,200,"SUCCESS",null,null,ms(start));}catch(BusinessException e){safeApi(requestId(),a==null?null:a.customerId(),a==null?null:a.accountId(),a==null?null:a.serviceId(),"DELETE","/api/open/v1/whitelist/{ip}","{}",clientIp,400,"FAILURE",e.getCode(),e.getMessage(),ms(start));throw e;}}

    private Map<String,Object> policySnapshot(Map<String,Object> svc){Map<String,Object> p=new LinkedHashMap<>();p.put("singleExtractLimit",intv(svc.get("single_extract_limit"),100));p.put("extractRateLimit",intv(svc.get("extract_rate_limit"),0));p.put("extractRateWindowSeconds",intv(svc.get("extract_rate_window_seconds"),60));p.put("dedupPeriodSeconds",intv(svc.get("dedup_period_seconds"),0));p.put("ipValidSeconds",intv(svc.get("ip_valid_seconds"),0));p.put("whitelistRequired",Boolean.TRUE.equals(svc.get("whitelist_required")));Map<String,Object> pkg=parse(n(svc.get("package_snapshot_json")));p.put("extractQuota",longv(pkg.get("extract_quota"),0));p.put("whitelistLimit",longv(pkg.get("whitelist_limit"),0));p.put("concurrencyLimit",longv(pkg.get("concurrency_limit"),0));p.put("serviceNo",svc.get("service_no"));p.put("productType",svc.get("product_type"));return p;}
    private String token(Long grantId,long version){String body="oa."+grantId+"."+version;return body+"."+sign(body);}
    private String sign(String body){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(jwt.secret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private void safeApi(String rid,Long cid,Long aid,Long sid,String method,String path,String query,String ip,int status,String result,String ec,String em,long elapsed){try{db.insertApiLog(rid,cid,aid,sid,method,path,query,ip,status,result,ec,em,elapsed);}catch(Exception ignored){}}
    private Map<String,Object> parse(String v){if(blank(v))return new LinkedHashMap<>();try{return json.readValue(v,new TypeReference<Map<String,Object>>(){});}catch(Exception e){return new LinkedHashMap<>();}}
    private String j(Object x){try{return json.writeValueAsString(x);}catch(Exception e){throw new IllegalStateException(e);}}
    private BusinessException err(String c,String m){return new BusinessException(c,m);} private boolean blank(String x){return x==null||x.isBlank();} private String n(Object x){return x==null?"":String.valueOf(x).trim();} private String s(Object x){return x==null?"":String.valueOf(x);} private Long lv(Object x){return x instanceof Number n?n.longValue():Long.valueOf(String.valueOf(x));} private int intv(Object x,int d){if(x==null||s(x).isBlank())return d;return x instanceof Number n?n.intValue():Integer.parseInt(s(x));} private long longv(Object x,long d){if(x==null||s(x).isBlank())return d;return x instanceof Number n?n.longValue():Long.parseLong(s(x));} private OffsetDateTime offset(Object x){if(x==null)return null;if(x instanceof OffsetDateTime o)return o;if(x instanceof java.sql.Timestamp t)return t.toInstant().atOffset(ZoneOffset.UTC);return OffsetDateTime.parse(s(x));} private long ms(long start){return Math.max(0,(System.nanoTime()-start)/1_000_000);} private String requestId(){String x=MDC.get("requestId");return x==null?"":x;}
}
