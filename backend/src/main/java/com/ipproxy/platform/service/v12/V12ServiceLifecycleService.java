package com.ipproxy.platform.service.v12;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipproxy.platform.common.api.PageResult;
import com.ipproxy.platform.common.audit.OperationAuditRepository;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import com.ipproxy.platform.service.adapter.ServiceProvisionAdapter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class V12ServiceLifecycleService {
    private final V12ServiceMapper db; private final BuiltinResourcePolicy policy; private final ServiceProvisionAdapter adapter; private final ObjectMapper json; private final OperationAuditRepository audit;
    public V12ServiceLifecycleService(V12ServiceMapper db,BuiltinResourcePolicy policy,ServiceProvisionAdapter adapter,ObjectMapper json,OperationAuditRepository audit){this.db=db;this.policy=policy;this.adapter=adapter;this.json=json;this.audit=audit;}

    public PageResult<Map<String,Object>> services(int page,int size,String keyword,String status,Long customerId,String productType,String deliveryMode){page=Math.max(1,page);size=Math.min(200,Math.max(1,size));String k=n(keyword),s=n(status),t=n(productType),d=n(deliveryMode);return new PageResult<>(page,size,db.countServices(k,s,customerId,t,d),db.services(k,s,customerId,t,d,size,(page-1)*size));}
    public Map<String,Object> service(Long id){Map<String,Object>x=mustService(id);x=new LinkedHashMap<>(x);x.put("resourceBindings",db.bindings(id));x.put("credentials",db.credentials(id));x.put("whitelist",db.whitelist(id));x.put("changes",db.changes(id));x.put("releaseRecords",db.releaseRecords(id));x.put("longMonitors",db.longMonitorRecords(id));return x;}

    @Transactional public Map<String,Object> provisionOrder(Long orderId,String reason,UserPrincipal actor,String ip){Map<String,Object> order=db.order(orderId);if(order==null)throw err("ORDER_NOT_FOUND","订单不存在");if(!"PENDING_PROVISION".equals(String.valueOf(order.get("status"))))throw err("ORDER_STATUS_INVALID","订单必须处于待开通状态");List<Map<String,Object>> out=new ArrayList<>();for(Map<String,Object> item:db.orderItems(orderId)){int count=((Number)item.get("service_count")).intValue();for(int i=0;i<count;i++)out.add(provision(new ProvisionInput(((Number)order.get("customer_id")).longValue(),((Number)item.get("product_id")).longValue(),item.get("package_id")==null?null:((Number)item.get("package_id")).longValue(),"SELF_OWNED",null,null,(OffsetDateTime)order.get("expected_effective_at"),(OffsetDateTime)order.get("expected_expire_at"),"订单自动开通"),orderId,((Number)item.get("id")).longValue(),actor,ip));}db.orderStatus(orderId,"PROVISIONED",actor.userId());audit.success(actor,"SERVICE","ORDER",orderId,"PROVISION_V12",required(reason,"开通原因不能为空"),ip);return Map.of("orderId",orderId,"services",out);}

    @Transactional public Map<String,Object> manualProvision(ProvisionInput in,UserPrincipal actor,String ip){return provision(in,null,null,actor,ip);}

    private Map<String,Object> provision(ProvisionInput in,Long orderId,Long itemId,UserPrincipal actor,String ip){Map<String,Object> customer=db.customer(in.customerId());if(customer==null||!"ACTIVE".equals(String.valueOf(customer.get("status"))))throw err("CUSTOMER_NOT_ACTIVE","客户不存在或非正常状态");Map<String,Object> product=db.product(in.productId());if(product==null||!"ACTIVE".equals(String.valueOf(product.get("status"))))throw err("PRODUCT_NOT_ACTIVE","产品不存在或未启用");Map<String,Object> pkg=in.packageId()==null?null:db.pkg(in.packageId());if(pkg!=null&&!Objects.equals(((Number)pkg.get("product_id")).longValue(),in.productId()))throw err("PACKAGE_PRODUCT_MISMATCH","套餐不属于当前产品");String productType=String.valueOf(product.get("product_type"));String delivery=n(in.deliveryMode()).isBlank()?"SELF_OWNED":in.deliveryMode().trim().toUpperCase(Locale.ROOT);if(!Set.of("SELF_OWNED","EXTERNAL_PROXY").contains(delivery))throw err("DELIVERY_MODE_INVALID","未知服务交付方式");OffsetDateTime effective=in.effectiveAt()==null?OffsetDateTime.now(ZoneOffset.UTC):in.effectiveAt();OffsetDateTime expire=in.expireAt();if(expire==null&&pkg!=null)expire=effective.plusDays(((Number)pkg.get("service_days")).longValue());String serviceNo=db.nextServiceNo();String quota=pkg==null?"{}":j(Map.of("packageId",pkg.get("id"),"packageCode",pkg.get("package_code"),"serviceDays",pkg.get("service_days")));
        BuiltinResourcePolicy.Decision decision=null;Long purchaseId=null,reverseProxyId=null;String policyCode;
        if("SELF_OWNED".equals(delivery)){decision=policy.decide(productType,in.productId(),in.packageId(),in.customerId());policyCode=decision.policyCode();}
        else {reverseProxyId=in.reverseProxyId();if(reverseProxyId==null)throw err("REVERSE_PROXY_REQUIRED","外部交付必须选择反向代理配置");Map<String,Object> rp=db.reverseProxy(reverseProxyId);if(rp==null)throw err("REVERSE_PROXY_NOT_FOUND","反向代理配置不存在");Long rpCustomer=((Number)rp.get("customer_id")).longValue();if(!Objects.equals(rpCustomer,in.customerId()))throw err("REVERSE_PROXY_CUSTOMER_MISMATCH","反向代理与客户不匹配");purchaseId=((Number)rp.get("purchase_id")).longValue();policyCode="EXTERNAL_REVERSE_PROXY";}
        Long sid=db.insertService(serviceNo,in.customerId(),orderId,itemId,in.productId(),in.packageId(),productType,String.valueOf(product.get("product_json")),pkg==null?null:String.valueOf(pkg.get("package_json")),quota,effective,expire,delivery,policyCode,purchaseId,reverseProxyId,actor.userId());
        Map<String,Object> ctx=new LinkedHashMap<>();ctx.put("serviceNo",serviceNo);ctx.put("customerId",in.customerId());ctx.put("deliveryMode",delivery);
        if("SELF_OWNED".equals(delivery)){
            db.bind(sid,"POOL",decision.poolId(),"PRIMARY",j(Map.of("poolCode",decision.poolCode(),"policyCode",decision.policyCode())),actor.userId());ctx.put("poolId",decision.poolId());
            if(decision.lineId()!=null){Map<String,Object> line=db.line(decision.lineId());db.bind(sid,"LINE",decision.lineId(),"OUTBOUND",j(line),actor.userId());ctx.put("lineId",decision.lineId());if("EXCLUSIVE_IP".equals(productType)){db.allocateExclusiveLine(sid,decision.lineId(),in.customerId(),expire,String.valueOf(line.get("current_public_ip")),j(Map.of("serviceNo",serviceNo,"lineCode",decision.lineCode())),actor.userId());}}
            ServiceProvisionAdapter.Result result=adapter.provision(productType,sid,ctx);if(!result.success()){db.serviceStatus(sid,"PROVISION_FAILED",result.message(),actor.userId());audit.success(actor,"SERVICE","SERVICE",sid,"PROVISION_FAILED",result.message(),ip);return Map.of("serviceId",sid,"serviceNo",serviceNo,"status","PROVISION_FAILED","message",result.message());}
        }else{
            db.bind(sid,"SUPPLIER_PURCHASE",purchaseId,"PRIMARY",j(Map.of("deliveryMode","EXTERNAL_PROXY")),actor.userId());db.bind(sid,"REVERSE_PROXY",reverseProxyId,"ENTRY",j(Map.of("deliveryMode","EXTERNAL_PROXY")),actor.userId());db.attachReverseProxy(reverseProxyId,sid,actor.userId());ctx.put("reverseProxyId",reverseProxyId);
        }
        Map<String,Object> credential=issueCredential(sid,serviceNo,productType,expire,actor.userId());db.serviceStatus(sid,"ACTIVE",null,actor.userId());audit.success(actor,"SERVICE","SERVICE",sid,"PROVISION_V12",n(in.remark()).isBlank()?"V1.2服务开通":in.remark(),ip);Map<String,Object> out=new LinkedHashMap<>();out.put("serviceId",sid);out.put("serviceNo",serviceNo);out.put("deliveryMode",delivery);out.put("builtinPolicyCode",policyCode);out.put("status","ACTIVE");out.put("oneTimeCredential",credential);return out;}

    private Map<String,Object> issueCredential(Long serviceId,String serviceNo,String productType,OffsetDateTime expire,Long actor){String type=switch(productType){case "VPN"->"USERNAME_PASSWORD";case "TUNNEL"->"TUNNEL_ACCOUNT";default->"TOKEN";};String secret=UUID.randomUUID().toString().replace("-","")+Long.toString(System.nanoTime(),36);String account="TOKEN".equals(type)?null:"svc_"+serviceNo.toLowerCase(Locale.ROOT);String mask=secret.substring(0,6)+"••••••"+secret.substring(secret.length()-4);Long id=db.insertCredential(serviceId,type,account,sha(secret),mask,expire,j(Map.of("issuedBy","V1.2")),actor);return Map.of("credentialId",id,"type",type,"account",account==null?"":account,"secret",secret,"mask",mask);}
    private String sha(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private Map<String,Object> mustService(Long id){Map<String,Object>x=db.service(id);if(x==null)throw err("SERVICE_NOT_FOUND","服务不存在");return x;}
    private String n(String x){return x==null?"":x.trim();}private String required(String x,String msg){if(n(x).isBlank())throw err("V12_REQUIRED",msg);return x.trim();}private String j(Object x){try{return json.writeValueAsString(x==null?Map.of():x);}catch(Exception e){return "{}";}}private BusinessException err(String code,String msg){return new BusinessException(code,msg);}

    public record ProvisionInput(Long customerId,Long productId,Long packageId,String deliveryMode,Long supplierPurchaseId,Long reverseProxyId,OffsetDateTime effectiveAt,OffsetDateTime expireAt,String remark){}
}
