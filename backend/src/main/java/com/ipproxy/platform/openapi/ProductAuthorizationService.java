package com.ipproxy.platform.openapi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Business-platform authority check for the product group. This is a
 * non-consuming preflight decision, NOT a quota debit, proxy credential or
 * verified network-delivery receipt. Unknown actions fail closed.
 */
@Service
public class ProductAuthorizationService {
    private static final String ELIGIBILITY_SQL = """
        SELECT a.id account_id,a.status account_status,c.id customer_id,c.status customer_status,
               s.id service_id,s.status service_status,s.effective_at,s.expire_at,s.order_id,
               s.product_type,s.delivery_stage,p.access_mode_code,
               g.id grant_id,g.status grant_status,g.token_version,
               g.policy_snapshot::text policy_snapshot_json,
               o.payment_status order_payment_status,o.customer_id order_customer_id
        FROM customer_account a
        JOIN customer c ON c.id=a.customer_id AND c.deleted=FALSE
        JOIN svc_instance s ON s.id=? AND s.customer_id=c.id AND s.deleted=FALSE
        JOIN prd_product p ON p.id=s.product_id AND p.deleted=FALSE
        LEFT JOIN open_access_grant g ON g.account_id=a.id AND g.service_id=s.id
        LEFT JOIN biz_order o ON o.id=s.order_id AND o.deleted=FALSE
        WHERE a.id=? AND a.deleted=FALSE
        """;
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final Clock clock;

    public ProductAuthorizationService(JdbcTemplate db,ObjectMapper json) {
        this(db,json,Clock.systemUTC());
    }
    ProductAuthorizationService(JdbcTemplate db,ObjectMapper json,Clock clock) {
        this.db=db;this.json=json;this.clock=clock;
    }

    public record CheckRequest(String subjectId,String serviceId,String orderId,
                               String action,int requestedUnits,String requestId) {}
    public record Decision(String decision,String authorizationId,OffsetDateTime expiresAt,
                           Long remainingQuota,String policyVersion,String reasonCode) {}

    public Decision check(CheckRequest req) {
        OffsetDateTime now=OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        // Only short extraction has a verified account/grant policy in the current
        // business platform. CONNECT and privileged lifecycle actions need
        // separately signed identities and must not inherit extraction rights.
        if(req==null||!Objects.equals(req.action(),"SHORT_EXTRACT"))
            return deny(now,"ACTION_NOT_SUPPORTED");
        Long accountId=parseId(req.subjectId()),serviceId=parseId(req.serviceId());
        if(accountId==null||serviceId==null||req.requestedUnits()<1)
            return deny(now,"AUTH_REQUEST_INVALID");
        List<Map<String,Object>> candidates=db.queryForList(ELIGIBILITY_SQL,serviceId,accountId);
        if(candidates.size()!=1)return deny(now,"SUBJECT_SERVICE_NOT_FOUND");
        Map<String,Object> s=candidates.getFirst();
        if(!"ACTIVE".equals(str(s.get("account_status")))||!"ACTIVE".equals(str(s.get("customer_status"))))
            return deny(now,"ACCOUNT_DISABLED");
        if(!"ACTIVE".equals(str(s.get("service_status"))))
            return deny(now,"SERVICE_DISABLED");
        if(!"SHORT_IP".equals(str(s.get("product_type")))||!"API_EXTRACT".equals(str(s.get("access_mode_code"))))
            return deny(now,"SERVICE_ACTION_MISMATCH");
        if(!"VERIFIED".equals(str(s.get("delivery_stage"))))
            return deny(now,"DELIVERY_NOT_VERIFIED");
        if(s.get("grant_id")==null||!"ACTIVE".equals(str(s.get("grant_status"))))
            return deny(now,"ACCESS_GRANT_INACTIVE");
        if(req.orderId()!=null&&!req.orderId().isBlank()) {
            Long orderId=parseId(req.orderId());
            if(orderId==null||!orderId.equals(num(s.get("order_id"))))
                return deny(now,"ORDER_MISMATCH");
        }
        if(s.get("order_id")!=null&&(!"PAID_CONFIRMED".equals(str(s.get("order_payment_status")))
               ||!Objects.equals(num(s.get("customer_id")),num(s.get("order_customer_id")))))
            return deny(now,"ORDER_PAYMENT_NOT_CONFIRMED");
        OffsetDateTime from=(OffsetDateTime)s.get("effective_at");
        OffsetDateTime expires=(OffsetDateTime)s.get("expire_at");
        if(from!=null&&from.isAfter(now))return deny(now,"SERVICE_NOT_EFFECTIVE");
        if(expires==null||!expires.isAfter(now))return deny(now,"SERVICE_EXPIRED");
        Map<String,Object> policy=policy(str(s.get("policy_snapshot_json")));
        int max=positiveLimit(policy.get("singleExtractLimit"),100);
        if(req.requestedUnits()>max)return deny(now,"EXTRACT_AMOUNT_EXCEEDED");
        long quota=nonnegative(policy.get("extractQuota"),0);
        long used=db.queryForObject(
             "SELECT COALESCE(sum(returned_count),0) FROM log_ip_extract WHERE service_id=? AND result IN ('SUCCESS','PARTIAL')",
             Long.class,serviceId);
        Long remaining=quota>0?Math.max(0,quota-used):null;
        if(quota>0&&(used>=quota||req.requestedUnits()>quota-used))
            return deny(now,"EXTRACT_QUOTA_EXCEEDED");
        long rate=nonnegative(policy.get("extractRateLimit"),0);
        int window=positiveLimit(policy.get("extractRateWindowSeconds"),60);
        if(rate>0) {
            Long recent=db.queryForObject(
                "SELECT count(*) FROM log_ip_extract WHERE service_id=? AND occurred_at>=now()-make_interval(secs=>?)",
                Long.class,serviceId,window);
            if(recent!=null&&recent>=rate)return deny(now,"EXTRACT_RATE_LIMIT");
        }
        // Strictly short-lived decision. The product side must recheck for each
        // actual extraction; quotas are NOT reserved by this preflight.
        OffsetDateTime validUntil=now.plusSeconds(5);
        if(expires.isBefore(validUntil))validUntil=expires;
        return new Decision("ALLOW","grant:"+s.get("grant_id"),validUntil,remaining,
                str(s.get("token_version")),"AUTHORIZED_PREFLIGHT");
    }

    private Decision deny(OffsetDateTime now,String code){
        return new Decision("DENY","",now,null,"",code);
    }
    private Long parseId(String value){
        try{long v=Long.parseLong(value);return v>0?v:null;}catch(Exception e){return null;}
    }
    private Long num(Object x){return x instanceof Number n?n.longValue():null;}
    private String str(Object x){return x==null?"":x.toString();}
    private int positiveLimit(Object x,int fallback){
        long n=nonnegative(x,fallback);return n<1||n>Integer.MAX_VALUE?fallback:(int)n;
    }
    private long nonnegative(Object x,long fallback) {
        if(x==null)return fallback;
        try{long n=Long.parseLong(x.toString());return n>=0?n:fallback;}catch(Exception e){return fallback;}
    }
    private Map<String,Object> policy(String value){
        try {return json.readValue(value,new TypeReference<Map<String,Object>>(){});}
        catch(Exception e){return Map.of();}
    }
}
