package com.ipproxy.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipproxy.platform.common.api.PageResult;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import com.ipproxy.platform.service.adapter.ServiceProvisionAdapter;
import com.ipproxy.platform.service.mapper.OrderServiceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.sql.Timestamp;
import java.util.*;

@Service
public class OrderServiceService {
    private final OrderServiceMapper db; private final ObjectMapper json; private final ServiceProvisionAdapter adapter;
    public OrderServiceService(OrderServiceMapper db,ObjectMapper json,ServiceProvisionAdapter adapter){this.db=db;this.json=json;this.adapter=adapter;}
    public record OrderItemInput(Long productId,Long packageId,Integer quantity,Integer serviceCount,BigDecimal unitPrice,String remark){}
    public record OrderInput(Long customerId,String orderType,String currency,OffsetDateTime expectedEffectiveAt,OffsetDateTime expectedExpireAt,Long ownerUserId,String remark,List<OrderItemInput> items,Long renewalServiceId){}
    public record ManualProvisionInput(Long customerId,Long productId,Long packageId,Long strategyId,OffsetDateTime effectiveAt,OffsetDateTime expireAt,String remark){}

    public PageResult<Map<String,Object>> orders(int page,int size,String keyword,String status,Long customerId){page=Math.max(1,page);size=Math.min(200,Math.max(1,size));keyword=n(keyword);status=n(status);return new PageResult<>(page,size,db.countOrders(keyword,status,customerId),db.orders(keyword,status,customerId,size,(page-1)*size));}
    public Map<String,Object> order(Long id){Map<String,Object> x=mustOrder(id);x.put("items",db.orderItems(id));return x;}
    @Transactional public Long createOrder(OrderInput in,UserPrincipal actor,String ip){Map<String,Object> c=mustCustomer(in.customerId());if(!"ACTIVE".equals(s(c.get("status"))))throw err("CUSTOMER_NOT_ACTIVE","仅正常客户可创建订单");if(in.items()==null||in.items().isEmpty())throw err("ORDER_ITEMS_REQUIRED","订单至少包含一个订单项");String type=Set.of("NEW","RENEW","EXPAND","CHANGE").contains(n(in.orderType()))?in.orderType():"NEW";String currency=n(in.currency()).isBlank()?"CNY":in.currency().trim().toUpperCase();BigDecimal total=BigDecimal.ZERO;List<Map<String,Object>> prepared=new ArrayList<>();int line=1;for(OrderItemInput item:in.items()){Map<String,Object> p=mustActiveProduct(item.productId());Map<String,Object> k=item.packageId()==null?null:mustActivePackage(item.packageId(),item.productId());int qty=item.quantity()==null?1:Math.max(1,item.quantity());int sc=item.serviceCount()==null?1:Math.max(1,item.serviceCount());BigDecimal unit=item.unitPrice()!=null?item.unitPrice():(k==null?BigDecimal.ZERO:new BigDecimal(s(k.get("price"))));BigDecimal amount=unit.multiply(BigDecimal.valueOf(qty));total=total.add(amount);prepared.add(new LinkedHashMap<>(Map.of("line",line++,"product",p,"package",k==null?Map.of():k,"qty",qty,"sc",sc,"unit",unit,"amount",amount,"remark",n(item.remark()))));}
        if("RENEW".equals(type)){
            if(in.renewalServiceId()==null)throw err("RENEW_TARGET_REQUIRED","续费订单必须选择具体客户服务");
            Map<String,Object> target=mustService(in.renewalServiceId());
            if(!Objects.equals(longv(target.get("customer_id")),in.customerId()))throw err("RENEW_CUSTOMER_MISMATCH","续费目标服务不属于本客户");
            if(Set.of("TERMINATED","PROVISION_FAILED").contains(s(target.get("status"))))throw err("RENEW_SERVICE_INVALID","该服务不能续费");
            if(in.items().size()!=1)throw err("RENEW_SINGLE_ITEM_REQUIRED","每张续费订单只可对应一个既有服务");
            OrderItemInput only=in.items().getFirst();
            if(only.packageId()==null||!Objects.equals(only.productId(),longv(target.get("product_id")))||
               !Objects.equals(only.packageId(),target.get("package_id")==null?null:longv(target.get("package_id")))||
               (only.quantity()!=null&&only.quantity()!=1)||
               (only.serviceCount()!=null&&only.serviceCount()!=1))
                throw err("RENEW_PRODUCT_MISMATCH","续费订单必须只针对原服务的产品套餐，数量和实例数均为1");
        }else if(in.renewalServiceId()!=null)throw err("RENEW_TARGET_UNEXPECTED","只有续费订单可以指定原服务");
        String no=db.nextOrderNo();Long id=db.insertOrder(no,in.customerId(),type,total,currency,in.expectedEffectiveAt(),in.expectedExpireAt(),in.ownerUserId(),n(in.remark()),in.renewalServiceId(),actor.userId());for(Map<String,Object> a:prepared){Map<String,Object> p=(Map<String,Object>)a.get("product");Map<String,Object> k=(Map<String,Object>)a.get("package");db.insertItem(id,(Integer)a.get("line"),longv(p.get("id")),k.isEmpty()?null:longv(k.get("id")),(Integer)a.get("qty"),(BigDecimal)a.get("unit"),(BigDecimal)a.get("amount"),s(p.get("product_json")),k.isEmpty()?null:s(k.get("package_json")),(Integer)a.get("sc"),s(a.get("remark")));}audit(actor,"ORDER",id,"CREATE","新增订单",ip);return id;}
    @Transactional public void confirmOrder(Long id,String reason,UserPrincipal actor,String ip){Map<String,Object> o=mustOrder(id);if(!Set.of("PENDING_CONFIRM","DRAFT").contains(s(o.get("status"))))throw err("ORDER_STATUS_INVALID","当前订单状态不能确认");db.orderStatus(id,"PENDING_PROVISION",actor.userId());audit(actor,"ORDER",id,"CONFIRM",required(reason),ip);}
    @Transactional public void cancelOrder(Long id,String reason,UserPrincipal actor,String ip){Map<String,Object> o=mustOrder(id);if(Set.of("PROVISIONED","COMPLETED","CANCELLED").contains(s(o.get("status"))))throw err("ORDER_STATUS_INVALID","当前订单状态不能取消");db.cancelOrder(id,required(reason),actor.userId());audit(actor,"ORDER",id,"CANCEL",reason,ip);}

    @Transactional public Map<String,Object> provisionOrder(Long id,String reason,UserPrincipal actor,String ip){Map<String,Object> o=mustOrder(id);if(!"PENDING_PROVISION".equals(s(o.get("status"))))throw err("ORDER_STATUS_INVALID","订单必须处于待开通状态");if(!"PAID_CONFIRMED".equals(s(o.get("payment_status"))))throw err("ORDER_PAYMENT_NOT_CONFIRMED","订单尚未核实客户付款；旧开通路径同样禁止绕过付款门禁");List<Map<String,Object>> issued=new ArrayList<>();for(Map<String,Object> item:db.orderItems(id)){int n=((Number)item.get("service_count")).intValue();for(int i=0;i<n;i++)issued.add(provisionOne(longv(o.get("customer_id")),id,longv(item.get("id")),longv(item.get("product_id")),item.get("package_id")==null?null:longv(item.get("package_id")),null,(OffsetDateTime)o.get("expected_effective_at"),(OffsetDateTime)o.get("expected_expire_at"),actor,ip));}db.orderStatus(id,"PROVISIONED",actor.userId());audit(actor,"ORDER",id,"PROVISION",required(reason),ip);return Map.of("orderId",id,"services",issued);}
    @Transactional public Map<String,Object> manualProvision(ManualProvisionInput in,UserPrincipal actor,String ip){mustCustomer(in.customerId());return provisionOne(in.customerId(),null,null,in.productId(),in.packageId(),in.strategyId(),in.effectiveAt(),in.expireAt(),actor,ip);}

    private Map<String,Object> provisionOne(Long customerId,Long orderId,Long itemId,Long productId,Long packageId,Long requestedStrategy,OffsetDateTime effective,OffsetDateTime expire,UserPrincipal actor,String ip){Map<String,Object> p=mustActiveProduct(productId);
        if(Set.of("LONG_IP","EXCLUSIVE_IP").contains(String.valueOf(p.get("product_type"))))
            throw err("LEGACY_PROVISION_NOT_ALLOWED","固定IP产品必须走V1.2按公网IPv4校验的逐实例开通向导");
        Map<String,Object> k=packageId==null?null:mustActivePackage(packageId,productId);Map<String,Object> st=requestedStrategy==null?db.activeStrategy(productId):null;if(requestedStrategy!=null){Map<String,Object> candidate=db.activeStrategy(productId);if(candidate!=null&&Objects.equals(longv(candidate.get("id")),requestedStrategy))st=candidate;}if(st==null)throw err("SERVICE_STRATEGY_REQUIRED","产品没有可用的已启用资源策略");Map<String,Object> pool=db.primaryPool(longv(st.get("id")));if(pool==null||((Number)pool.get("member_count")).longValue()<=0)throw err("SERVICE_RESOURCE_UNAVAILABLE","资源策略没有可用资源池");OffsetDateTime eff=effective==null?OffsetDateTime.now(ZoneOffset.UTC):effective;OffsetDateTime exp=expire;if(exp==null&&k!=null)exp=eff.plusDays(((Number)k.get("service_days")).longValue());String serviceNo=db.nextServiceNo();String quota=k==null?"{}":j(Map.of("packageId",k.get("id"),"packageCode",k.get("package_code"),"serviceDays",k.get("service_days")));Long sid=db.insertService(serviceNo,customerId,orderId,itemId,productId,packageId,longv(st.get("id")),s(p.get("product_type")),s(p.get("product_json")),k==null?null:s(k.get("package_json")),s(st.get("strategy_json")),quota,eff,exp,actor.userId());db.bind(sid,"POOL",longv(pool.get("id")),"PRIMARY",j(Map.of("poolCode",pool.get("pool_code"))),actor.userId());if("EXCLUSIVE_IP".equals(s(p.get("product_type")))){Map<String,Object> ipr=db.freeExclusiveIp(longv(pool.get("id")));if(ipr==null)throw err("EXCLUSIVE_RESOURCE_UNAVAILABLE","当前资源池没有可分配的独享IP");db.allocateExclusive(sid,"IP",longv(ipr.get("id")),customerId,exp,j(ipr),actor.userId());db.bind(sid,"IP",longv(ipr.get("id")),"OUTBOUND",j(ipr),actor.userId());}if("TUNNEL".equals(s(p.get("product_type")))){Long domain=db.tunnelDomain(productId);if(domain!=null)db.bind(sid,"DOMAIN",domain,"ENTRY","{}",actor.userId());}
        Map<String,Object> cred=issueCredential(sid,serviceNo,s(p.get("product_type")),exp,actor.userId());Map<String,Object> ctx=new LinkedHashMap<>();ctx.put("poolId",pool.get("id"));ctx.put("serviceNo",serviceNo);ctx.put("customerId",customerId);ServiceProvisionAdapter.Result result=adapter.provision(s(p.get("product_type")),sid,ctx);if(!result.success()){db.serviceStatus(sid,"PROVISION_FAILED",result.message(),actor.userId());audit(actor,"SERVICE",sid,"PROVISION_FAILED",result.message(),ip);return Map.of("serviceId",sid,"serviceNo",serviceNo,"status","PROVISION_FAILED","message",result.message());}db.serviceStatus(sid,"ACTIVE",null,actor.userId());audit(actor,"SERVICE",sid,"PROVISION","服务开通成功",ip);Map<String,Object> out=new LinkedHashMap<>();out.put("serviceId",sid);out.put("serviceNo",serviceNo);out.put("status","ACTIVE");out.put("oneTimeCredential",cred);return out;}

    public PageResult<Map<String,Object>> services(int page,int size,String keyword,String status,Long customerId,String productType){page=Math.max(1,page);size=Math.min(200,Math.max(1,size));keyword=n(keyword);status=n(status);productType=n(productType);return new PageResult<>(page,size,db.countServices(keyword,status,customerId,productType),db.services(keyword,status,customerId,productType,size,(page-1)*size));}
    public Map<String,Object> service(Long id){Map<String,Object>x=mustService(id);x.put("resourceBindings",db.bindings(id));x.put("whitelist",db.whitelist(id));x.put("changes",db.changes(id));return x;}
    public List<Map<String,Object>> credentials(Long id){mustService(id);return db.credentials(id);}
    private void requireRealLifecycle(Map<String,Object> svc,String operation){
        if(adapter instanceof com.ipproxy.platform.service.adapter.MockServiceProvisionAdapter
            || adapter instanceof com.ipproxy.platform.service.adapter.NoopServiceProvisionAdapter)
            throw err("DATA_PLANE_ADAPTER_UNAVAILABLE",operation+"需要真实代理产品适配器；Mock与管理面成功均不能作为真实完成证明");
        if("EXTERNAL_PROXY".equals(String.valueOf(svc.get("delivery_mode"))))
            throw err("EXTERNAL_LIFECYCLE_NOT_INTEGRATED",operation+"需要上游采购期限、真实反向代理入口的执行和回执校验");
    }
    private void requireVerified(ServiceProvisionAdapter.Result result,String operation){
        if(result==null||!result.success()||result.metadata()==null
           || !Boolean.TRUE.equals(result.metadata().get("verified")))
            throw err("DATA_PLANE_NOT_VERIFIED",operation+"没有取得真实代理执行及验证回执，保持原服务与出口占用");
    }
    @Transactional public void suspend(Long id,String reason,UserPrincipal actor,String ip){Map<String,Object>s=mustService(id);if(!"ACTIVE".equals(s(s.get("status"))))throw err("SERVICE_STATUS_INVALID","仅正常服务可暂停");required(reason);requireRealLifecycle(s,"停服");
        requireVerified(adapter.suspend(s(s.get("product_type")),id,Map.of("serviceNo",s.get("service_no"))),"停服");db.freezeCredentials(id);db.serviceStatus(id,"SUSPENDED",null,actor.userId());db.insertChange(db.nextChangeNo(),id,"SUSPEND",j(s),j(Map.of("status","SUSPENDED")),required(reason),"SUCCESS",actor.userId());audit(actor,"SERVICE",id,"SUSPEND",reason,ip);}
    @Transactional public void resume(Long id,String reason,UserPrincipal actor,String ip){Map<String,Object>s=mustService(id);if(!"SUSPENDED".equals(s(s.get("status"))))throw err("SERVICE_STATUS_INVALID","仅暂停服务可恢复");required(reason);requireRealLifecycle(s,"恢复");
        requireVerified(adapter.resume(s(s.get("product_type")),id,Map.of("serviceNo",s.get("service_no"))),"恢复");db.activateCredentials(id);db.serviceStatus(id,"ACTIVE",null,actor.userId());db.insertChange(db.nextChangeNo(),id,"RESUME",j(s),j(Map.of("status","ACTIVE")),required(reason),"SUCCESS",actor.userId());audit(actor,"SERVICE",id,"RESUME",reason,ip);}
    private OffsetDateTime dateOf(Object value){
        if(value==null)return null;
        if(value instanceof OffsetDateTime o)return o;
        if(value instanceof Timestamp t)return t.toInstant().atOffset(ZoneOffset.UTC);
        throw err("SERVICE_DATE_MAPPING_INVALID","服务期限字段格式不可识别，请联系运维核查");
    }
    @Transactional public OffsetDateTime renew(Long id,int days,Long renewalOrderId,String reason,UserPrincipal actor,String ip){
        if(days<=0)throw err("RENEW_DAYS_INVALID","续费天数必须大于0");
        if(renewalOrderId==null)throw err("RENEW_PAID_ORDER_REQUIRED","续费必须关联已核实付款的续费订单");
        required(reason);
        Map<String,Object> target=mustService(id);
        if(Set.of("TERMINATED","PROVISION_FAILED").contains(s(target.get("status"))))
            throw err("SERVICE_STATUS_INVALID","该服务不能续费");
        Map<String,Object> o=db.lockedRenewalOrder(renewalOrderId);
        if(o==null || !"RENEW".equals(s(o.get("order_type"))) ||
           !"PENDING_PROVISION".equals(s(o.get("status"))) ||
           !"PAID_CONFIRMED".equals(s(o.get("payment_status"))) ||
           !Objects.equals(o.get("renewal_service_id"),id) ||
           !Objects.equals(o.get("customer_id"),target.get("customer_id")))
            throw err("RENEW_PAYMENT_OR_TARGET_INVALID","续费订单未核实付款、已经处理或关联客户/服务不正确");
        List<Map<String,Object>> items=db.renewalItems(renewalOrderId);
        if(items.size()!=1 || !Objects.equals(items.getFirst().get("product_id"),target.get("product_id")) ||
           !Objects.equals(items.getFirst().get("package_id"),target.get("package_id")) ||
           ((Number)items.getFirst().get("quantity")).intValue()!=1 ||
           ((Number)items.getFirst().get("service_count")).intValue()!=1 ||
           items.getFirst().get("term_days")==null ||
           ((Number)items.getFirst().get("term_days")).intValue()!=days)
            throw err("RENEW_TERM_MISMATCH","续费天数必须与已付款订单的套餐期限快照一致");
        OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC),old=dateOf(target.get("expire_at"));
        boolean fixed=Set.of("LONG_IP","EXCLUSIVE_IP").contains(s(target.get("product_type")));
        OffsetDateTime base;
        Integer graceDays=null;
        if(fixed){
            if(old==null || target.get("grace_days_snapshot")==null)
                throw err("RENEW_GRACE_SNAPSHOT_MISSING","固定IP服务缺少原合同宽限信息，必须先核对存量权益");
            graceDays=((Number)target.get("grace_days_snapshot")).intValue();
            OffsetDateTime graceEnd=dateOf(target.get("grace_until_at"));
            if(graceEnd==null)graceEnd=old.plusDays(graceDays);
            if(now.isAfter(graceEnd))
                throw err("RENEW_OUTSIDE_GRACE","已超过固定IP宽限截止，必须重新开通并按实际交付时间起算");
            base=old;
        }else base=old==null||old.isBefore(now)?now:old;
        OffsetDateTime expiry=base.plusDays(days);
        if(!expiry.isAfter(now))
            throw err("RENEW_TERM_EXPIRED","从原到期时刻顺延后，新期限仍早于当前时间；请购买足够续费天数");
        OffsetDateTime newGrace=fixed?expiry.plusDays(graceDays):null;
        requireRealLifecycle(target,"续费");
        Map<String,Object> context=new LinkedHashMap<>();
        context.put("serviceNo",s(target.get("service_no")));
        context.put("renewalOrderId",renewalOrderId);
        context.put("operationKey","renew:order:"+renewalOrderId);
        context.put("newExpireAt",expiry.toString());
        requireVerified(adapter.renew(s(target.get("product_type")),id,context),"续费");
        boolean restore="EXPIRED".equals(s(target.get("status")));
        db.completeRenewedService(id,expiry,newGrace,restore,actor.userId());
        db.orderStatus(renewalOrderId,"COMPLETED",actor.userId());
        db.insertChange(db.nextChangeNo(),id,"RENEW",
            j(Map.of("expireAt",old==null?"":old.toString())),
            j(Map.of("expireAt",expiry.toString(),"days",days,"renewalOrderId",renewalOrderId)),
            required(reason),"SUCCESS",actor.userId());
        audit(actor,"SERVICE",id,"RENEW","已验证续费并绑定已付款订单:"+renewalOrderId+"；"+reason,ip);
        return expiry;
    }
    @Transactional public Map<String,Object> terminate(Long id,String reason,UserPrincipal actor,String ip){Map<String,Object>s=mustService(id);if("TERMINATED".equals(s(s.get("status"))))throw err("SERVICE_STATUS_INVALID","服务已终止");required(reason);requireRealLifecycle(s,"终止");
        ServiceProvisionAdapter.Result ar=adapter.release(s(s.get("product_type")),id,Map.of("serviceNo",s.get("service_no")));if(!ar.success()||ar.metadata()==null||!Boolean.TRUE.equals(ar.metadata().get("verified"))){Long rid=db.insertRelease(db.nextReleaseNo(),id,"TERMINATE","FAILED","{}","{}",actor.userId(),ar.message());audit(actor,"SERVICE",id,"RELEASE_FAILED",ar.message(),ip);return Map.of("releaseId",rid,"status","FAILED","message",ar.message());}int creds=db.revokeCredentials(id),bindings=db.releaseBindings(id),exclusive=db.recycleExclusive(id);
        db.releaseEgressClaims(id);db.serviceStatus(id,"TERMINATED",null,actor.userId());Long rid=db.insertRelease(db.nextReleaseNo(),id,"TERMINATE","SUCCESS",j(Map.of("revoked",creds)),j(Map.of("bindings",bindings,"exclusivePendingRecycle",exclusive)),actor.userId(),null);db.insertChange(db.nextChangeNo(),id,"TERMINATE",j(s),j(Map.of("status","TERMINATED")),required(reason),"SUCCESS",actor.userId());audit(actor,"SERVICE",id,"TERMINATE",reason,ip);return Map.of("releaseId",rid,"status","SUCCESS");}
    @Transactional public Map<String,Object> resetCredential(Long id,String reason,UserPrincipal actor,String ip){Map<String,Object>s=mustService(id);db.revokeCurrentCredential(id);Map<String,Object> c=issueCredential(id,s(s.get("service_no")),s(s.get("product_type")),(OffsetDateTime)s.get("expire_at"),actor.userId());db.insertChange(db.nextChangeNo(),id,"CREDENTIAL_RESET","{}",j(Map.of("credentialReset",true)),required(reason),"SUCCESS",actor.userId());audit(actor,"SERVICE",id,"CREDENTIAL_RESET",reason,ip);return c;}
    @Transactional public Long addWhitelist(Long id,String address,String remark,UserPrincipal actor,String ip){Map<String,Object>s=mustService(id);if(!Set.of("ACTIVE","SUSPENDED").contains(s(s.get("status"))))throw err("SERVICE_STATUS_INVALID","当前服务不能维护白名单");try{Long wid=db.addWhitelist(id,address,n(remark),actor.userId());audit(actor,"SERVICE",id,"WHITELIST_ADD",address,ip);return wid;}catch(Exception e){throw err("WHITELIST_INVALID","IP格式无效或已存在");}}
    @Transactional public void disableWhitelist(Long id,Long wid,String reason,UserPrincipal actor,String ip){mustService(id);if(db.disableWhitelist(id,wid,actor.userId())==0)throw err("WHITELIST_NOT_FOUND","白名单记录不存在");audit(actor,"SERVICE",id,"WHITELIST_DISABLE",required(reason),ip);}
    public PageResult<Map<String,Object>> releases(int page,int size,String status){page=Math.max(1,page);size=Math.min(200,Math.max(1,size));status=n(status);return new PageResult<>(page,size,db.countReleases(status),db.releases(status,size,(page-1)*size));}

    public Map<String,Object> options(){return Map.of("customers",List.of(),"orderTypes",List.of("NEW","RENEW","EXPAND","CHANGE"),"serviceStatuses",List.of("PENDING","ACTIVE","SUSPENDED","EXPIRED","TERMINATED","PROVISION_FAILED"),"productTypes",List.of("SHORT_IP","LONG_IP","EXCLUSIVE_IP","VPN","TUNNEL"));}
    private Map<String,Object> issueCredential(Long sid,String serviceNo,String productType,OffsetDateTime expire,Long actor){String type=switch(productType){case "VPN"->"USERNAME_PASSWORD";case "TUNNEL"->"TUNNEL_ACCOUNT";default->"TOKEN";};String secret=UUID.randomUUID().toString().replace("-","")+Long.toString(System.nanoTime(),36);String account=("TOKEN".equals(type)?null:"svc_"+serviceNo.toLowerCase());String hash=sha(secret);String mask=secret.substring(0,Math.min(6,secret.length()))+"••••••"+secret.substring(Math.max(0,secret.length()-4));Long cid=db.insertCredential(sid,type,account,hash,mask,expire,j(Map.of("issuedBy","M6")),actor);Map<String,Object> out=new LinkedHashMap<>();out.put("credentialId",cid);out.put("type",type);out.put("account",account);out.put("secret",secret);out.put("mask",mask);return out;}
    private Map<String,Object> mustCustomer(Long id){Map<String,Object>x=db.customer(id);if(x==null)throw err("CUSTOMER_NOT_FOUND","客户不存在");return x;}
    private Map<String,Object> mustActiveProduct(Long id){Map<String,Object>x=db.product(id);if(x==null)throw err("PRODUCT_NOT_FOUND","产品不存在");if(!"ACTIVE".equals(s(x.get("status"))))throw err("PRODUCT_NOT_ACTIVE","仅已启用产品可下单/开通");return x;}
    private Map<String,Object> mustActivePackage(Long id,Long productId){Map<String,Object>x=db.pkg(id);if(x==null)throw err("PACKAGE_NOT_FOUND","套餐不存在");if(!Objects.equals(longv(x.get("product_id")),productId))throw err("PACKAGE_PRODUCT_MISMATCH","套餐不属于当前产品");if(!"ACTIVE".equals(s(x.get("status"))))throw err("PACKAGE_NOT_ACTIVE","仅已启用套餐可下单/开通");return x;}
    private Map<String,Object> mustOrder(Long id){Map<String,Object>x=db.order(id);if(x==null)throw err("ORDER_NOT_FOUND","订单不存在");return x;}
    private Map<String,Object> mustService(Long id){Map<String,Object>x=db.service(id);if(x==null)throw err("SERVICE_NOT_FOUND","服务不存在");return x;}
    private void audit(UserPrincipal a,String ot,Object id,String op,String reason,String ip){db.audit(a.userId(),n(a.displayName()).isBlank()?a.username():a.displayName(),ot,id,op,n(reason),n(ip));}
    private String required(String x){if(n(x).isBlank())throw err("REASON_REQUIRED","必须填写操作原因");return x.trim();}
    private String sha(String x){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(x.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private String j(Object o){try{return json.writeValueAsString(o==null?Map.of():o);}catch(Exception e){throw err("JSON_INVALID","数据无法序列化");}}
    private long longv(Object o){return o instanceof Number n?n.longValue():Long.parseLong(String.valueOf(o));}
    private String s(Object o){return o==null?"":String.valueOf(o);}
    private String n(String x){return x==null?"":x.trim();}
    private BusinessException err(String c,String m){return new BusinessException(c,m);}
}
