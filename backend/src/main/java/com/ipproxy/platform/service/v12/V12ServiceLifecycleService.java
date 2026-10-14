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
    public Map<String,Object> service(Long id){Map<String,Object>x=mustService(id);x=new LinkedHashMap<>(x);x.put("resourceBindings",db.bindings(id));x.put("egressClaims",db.egressClaims(id));x.put("backupCandidates",db.backupCandidates(id));x.put("actionTasks",db.actionTasks(id));x.put("credentials",db.credentials(id));x.put("whitelist",db.whitelist(id));x.put("changes",db.changes(id));x.put("releaseRecords",db.releaseRecords(id));x.put("longMonitors",db.longMonitorRecords(id));return x;}

    public record ProvisionPlan(Long orderItemId,String deliveryMode,List<Long> reverseProxyIds){}
    public Map<String,Object> provisionPreview(Long orderId){
        Map<String,Object> order=db.order(orderId);
        if(order==null)throw err("ORDER_NOT_FOUND","订单不存在");
        if(!"PENDING_PROVISION".equals(String.valueOf(order.get("status"))))throw err("ORDER_STATUS_INVALID","订单不是待开通状态");
        if(!"NEW".equals(String.valueOf(order.get("order_type"))))throw err("ORDER_TYPE_INVALID","续费、扩容、变更订单须走服务变更流程，不能重复新开通");
        List<Map<String,Object>> items=new ArrayList<>();
        for(Map<String,Object> item:db.orderItems(orderId)){
            Map<String,Object> row=new LinkedHashMap<>(item);
            String type=String.valueOf(item.get("product_type"));
            row.put("deliveryAdvice",switch(type){case "SHORT_IP"->"共享资源池动态分配";case "LONG_IP"->"长效线路固定绑定";case "EXCLUSIVE_IP"->"独享线路占用";case "VPN"->"VPN专项数据面";case "TUNNEL"->"隧道专项数据面";default->"请核对产品";});
            if("EXCLUSIVE_IP".equals(type)){
                int needed=db.exclusiveRequiredLines(((Number)item.get("product_id")).longValue(),
                    item.get("package_id")==null?null:((Number)item.get("package_id")).longValue());
                row.put("exclusiveLinesPerService",needed);
                row.put("availableExclusiveLines",db.freeExclusiveLineCount());
                if(db.freeExclusiveLineCount()<(long)needed*((Number)item.get("service_count")).intValue())
                    row.put("precheckError","独享线路不足");
            }
            if(Set.of("SHORT_IP","LONG_IP").contains(type)){
                try{
                    BuiltinResourcePolicy.Decision d=policy.decide(type,((Number)item.get("product_id")).longValue(),
                        item.get("package_id")==null?null:((Number)item.get("package_id")).longValue(),
                        ((Number)order.get("customer_id")).longValue());
                    row.put("resourcePrecheck",Map.of("poolCode",d.poolCode(),"lineCode",d.lineCode()==null?"动态选择":d.lineCode()));
                }catch(BusinessException ex){row.put("precheckError","当前可用资源不足，请核查资源池与线路状态");}
            }
            if(Set.of("VPN","TUNNEL").contains(type)&&adapter instanceof com.ipproxy.platform.service.adapter.NoopServiceProvisionAdapter)
                row.put("warning","实际数据面适配器尚未配置，无法真正开通");
            items.add(row);
        }
        return Map.of("order",order,"items",items,"availableReverseProxies",db.availableReverseProxies(((Number)order.get("customer_id")).longValue()),"alreadyProvisioned",db.existingServicesForOrder(orderId)>0,"paymentReady","PAID_CONFIRMED".equals(String.valueOf(order.get("payment_status"))));
    }

    @Transactional public Map<String,Object> provisionOrder(Long orderId,String reason,List<ProvisionPlan> plans,UserPrincipal actor,String ip){
        required(reason,"开通原因不能为空");
        Map<String,Object> order=db.lockedOrder(orderId);
        if(order==null)throw err("ORDER_NOT_FOUND","订单不存在");
        if(!"PENDING_PROVISION".equals(String.valueOf(order.get("status"))))throw err("ORDER_STATUS_INVALID","订单必须处于待开通状态");
        if(!"NEW".equals(String.valueOf(order.get("order_type"))))throw err("ORDER_TYPE_INVALID","只有新购订单可创建全新服务实例，其他订单须走变更流程");
        if(!"PAID_CONFIRMED".equals(String.valueOf(order.get("payment_status"))))throw err("ORDER_PAYMENT_NOT_CONFIRMED","客户订单尚无经授权核实的付款凭证；禁止创建或交付新服务，更不得以此安排供应商采购");
        if(db.existingServicesForOrder(orderId)>0)throw err("ORDER_ALREADY_PROVISIONED","订单已存在开通服务，请核对后处理，不可重复开通");
        List<Map<String,Object>> items=db.orderItems(orderId);
        if(items.isEmpty())throw err("ORDER_ITEMS_REQUIRED","订单不存在可开通明细");
        if(plans==null||plans.size()!=items.size())throw err("PROVISION_PLAN_REQUIRED","请逐订单项填写开通计划");
        Map<Long,ProvisionPlan> pmap=new LinkedHashMap<>();
        for(ProvisionPlan p:plans){
            if(p==null||p.orderItemId()==null||pmap.putIfAbsent(p.orderItemId(),p)!=null)
                throw err("PROVISION_PLAN_INVALID","存在重复或无效订单项");
        }
        Set<Long> usedProxyIds=new HashSet<>();
        List<Map<String,Object>> results=new ArrayList<>();
        Long customerId=((Number)order.get("customer_id")).longValue();
        for(Map<String,Object> item:items){
            Long itemId=((Number)item.get("id")).longValue();
            ProvisionPlan p=pmap.remove(itemId);
            if(p==null)throw err("PROVISION_PLAN_MISSING","订单项缺少开通计划");
            String delivery=n(p.deliveryMode()).toUpperCase(Locale.ROOT);
            int count=((Number)item.get("service_count")).intValue();
            if(count<1||count>100)throw err("PROVISION_COUNT_INVALID","服务数量无效");
            if(!Set.of("SELF_OWNED","EXTERNAL_PROXY").contains(delivery))throw err("DELIVERY_MODE_INVALID","不支持的交付方式");
            List<Long> proxyIds=p.reverseProxyIds()==null?List.of():p.reverseProxyIds();
            if("EXTERNAL_PROXY".equals(delivery)){
                if(proxyIds.size()!=count)throw err("PROXY_COUNT_MISMATCH","每个外部服务必须选择一个反向代理");
                for(Long proxyId:proxyIds)if(proxyId==null||!usedProxyIds.add(proxyId))throw err("PROXY_DUPLICATE","反向代理不能重复绑定");
            }else if(!proxyIds.isEmpty())throw err("PROVISION_PLAN_INVALID","自有交付不应选择反向代理");
            for(int i=0;i<count;i++){
                Map<String,Object> created=provision(new ProvisionInput(customerId,((Number)item.get("product_id")).longValue(),
                  item.get("package_id")==null?null:((Number)item.get("package_id")).longValue(),
                  delivery,null,"EXTERNAL_PROXY".equals(delivery)?proxyIds.get(i):null,
                  (OffsetDateTime)order.get("expected_effective_at"),(OffsetDateTime)order.get("expected_expire_at"),reason),
                  orderId,itemId,actor,ip);
                if(!"ACTIVE".equals(String.valueOf(created.get("status"))))throw err("PROVISION_NOT_COMPLETE","开通未全部完成，订单保持待开通");
                results.add(created);
            }
        }
        if(!pmap.isEmpty())throw err("PROVISION_PLAN_INVALID","包含不属于本订单的订单项");
        db.orderStatus(orderId,"PROVISIONED",actor.userId());
        audit.success(actor,"SERVICE","ORDER",orderId,"PROVISION_V12",reason.trim(),ip);
        return Map.of("orderId",orderId,"status","PROVISIONED","services",results);
    }

    @Transactional public Map<String,Object> manualProvision(ProvisionInput in,UserPrincipal actor,String ip){required(in.remark(),"手工开通必须填写操作原因");return provision(in,null,null,actor,ip);}

    private Map<String,Object> provision(ProvisionInput in,Long orderId,Long itemId,UserPrincipal actor,String ip){Map<String,Object> customer=db.customer(in.customerId());if(customer==null||!"ACTIVE".equals(String.valueOf(customer.get("status"))))throw err("CUSTOMER_NOT_ACTIVE","客户不存在或非正常状态");Map<String,Object> product=db.product(in.productId());if(product==null||!"ACTIVE".equals(String.valueOf(product.get("status"))))throw err("PRODUCT_NOT_ACTIVE","产品不存在或未启用");Map<String,Object> pkg=in.packageId()==null?null:db.pkg(in.packageId());if(pkg!=null&&!"ACTIVE".equals(String.valueOf(pkg.get("status"))))throw err("PACKAGE_NOT_ACTIVE","套餐已停用");if(pkg!=null&&!Objects.equals(((Number)pkg.get("product_id")).longValue(),in.productId()))throw err("PACKAGE_PRODUCT_MISMATCH","套餐不属于当前产品");String productType=String.valueOf(product.get("product_type"));String delivery=n(in.deliveryMode()).isBlank()?"SELF_OWNED":in.deliveryMode().trim().toUpperCase(Locale.ROOT);if(!Set.of("SELF_OWNED","EXTERNAL_PROXY").contains(delivery))throw err("DELIVERY_MODE_INVALID","未知服务交付方式");OffsetDateTime effective=in.effectiveAt()==null?OffsetDateTime.now(ZoneOffset.UTC):in.effectiveAt();OffsetDateTime expire=in.expireAt();if(expire==null&&pkg!=null)expire=effective.plusDays(((Number)pkg.get("service_days")).longValue());if(expire==null)throw err("SERVICE_EXPIRE_REQUIRED","必须选择服务到期时间或有效套餐");if(!expire.isAfter(effective))throw err("SERVICE_DATE_INVALID","到期时间必须晚于生效时间");if(Set.of("LONG_IP","EXCLUSIVE_IP").contains(productType)&&(pkg==null||pkg.get("grace_days")==null))throw err("PACKAGE_GRACE_REQUIRED","长效/独享必须选有明确宽限设置的套餐");String serviceNo=db.nextServiceNo();String quota=pkg==null?"{}":j(Map.of("packageId",pkg.get("id"),"packageCode",pkg.get("package_code"),"serviceDays",pkg.get("service_days")));
        BuiltinResourcePolicy.Decision decision=null;Long purchaseId=null,reverseProxyId=null;Map<String,Object> externalProxy=null;String policyCode;
        if("SELF_OWNED".equals(delivery)){decision=policy.decide(productType,in.productId(),in.packageId(),in.customerId());policyCode=decision.policyCode();}
        else {reverseProxyId=in.reverseProxyId();if(reverseProxyId==null)throw err("REVERSE_PROXY_REQUIRED","外部交付必须选择反向代理配置");Map<String,Object> rp=db.lockedReverseProxy(reverseProxyId);externalProxy=rp;
            if(rp==null)throw err("REVERSE_PROXY_NOT_FOUND","反向代理不存在");
            if(!Objects.equals(((Number)rp.get("customer_id")).longValue(),in.customerId()))throw err("REVERSE_PROXY_CUSTOMER_MISMATCH","反向代理与客户不匹配");
            if(!"ACTIVE".equals(String.valueOf(rp.get("status"))))throw err("REVERSE_PROXY_NOT_READY","反向代理未启用");
            if(!"ACTIVE".equals(String.valueOf(rp.get("purchase_status"))))throw err("PURCHASE_NOT_ACTIVE","上游采购套餐已失效");
            if(rp.get("service_id")!=null)throw err("REVERSE_PROXY_OCCUPIED","反向代理已被服务占用");
            OffsetDateTime fromRp=(OffsetDateTime)rp.get("effective_at"),untilRp=(OffsetDateTime)rp.get("expire_at");
            OffsetDateTime fromPurchase=(OffsetDateTime)rp.get("purchase_effective_at"),untilPurchase=(OffsetDateTime)rp.get("purchase_expire_at");
            if((fromRp!=null&&fromRp.isAfter(effective))||(fromPurchase!=null&&fromPurchase.isAfter(effective))||
              (untilRp!=null&&untilRp.isBefore(expire))||(untilPurchase==null||untilPurchase.isBefore(expire)))
              throw err("PROXY_TERM_MISMATCH","代理或采购套餐不能覆盖服务有效期");
            purchaseId=((Number)rp.get("purchase_id")).longValue();
            if(in.supplierPurchaseId()!=null&&!in.supplierPurchaseId().equals(purchaseId))throw err("PURCHASE_PROXY_MISMATCH","采购套餐与反向代理不匹配");
            policyCode="EXTERNAL_REVERSE_PROXY";}
        Long sid=db.insertService(serviceNo,in.customerId(),orderId,itemId,in.productId(),in.packageId(),productType,String.valueOf(product.get("product_json")),pkg==null?null:String.valueOf(pkg.get("package_json")),quota,effective,expire,delivery,policyCode,purchaseId,reverseProxyId,actor.userId());if(Set.of("LONG_IP","EXCLUSIVE_IP").contains(productType))db.snapshotGrace(sid,((Number)pkg.get("grace_days")).intValue(),expire);
        Map<String,Object> ctx=new LinkedHashMap<>();ctx.put("serviceNo",serviceNo);ctx.put("customerId",in.customerId());ctx.put("deliveryMode",delivery);ctx.put("policyCode",policyCode);
        if("SELF_OWNED".equals(delivery)){
            if("EXCLUSIVE_IP".equals(productType)){
                int need=db.exclusiveRequiredLines(in.productId(),in.packageId());
                if(need>200)throw err("EXCLUSIVE_QUOTA_INVALID","独享线路数超出开通上限");
                List<Map<String,Object>> lines=db.lockFreeExclusiveLines(need);
                if(lines.size()!=need)throw err("EXCLUSIVE_RESOURCE_UNAVAILABLE","独享资源不足，需要"+need+"条可用线路");
                for(Map<String,Object> line:lines){
                    Long lid=((Number)line.get("id")).longValue();
                    db.allocateExclusiveLine(sid,lid,in.customerId(),expire,expire.plusDays(((Number)pkg.get("grace_days")).intValue()),String.valueOf(line.get("current_public_ip")),j(line),actor.userId());
                    db.bind(sid,"LINE",lid,"EXCLUSIVE",j(line),actor.userId());
                }
                ctx.put("exclusiveLines",lines.stream().map(e->e.get("id")).toList());
            }
            if(decision.poolId()!=null){db.bind(sid,"POOL",decision.poolId(),"PRIMARY",j(Map.of("poolCode",decision.poolCode(),"policyCode",decision.policyCode())),actor.userId());ctx.put("poolId",decision.poolId());}
            if(decision.lineId()!=null){Map<String,Object> line=db.line(decision.lineId());db.bind(sid,"LINE",decision.lineId(),"OUTBOUND",j(line),actor.userId());
                if("LONG_IP".equals(productType)&&line!=null&&line.get("current_public_ip")!=null)
                    db.claimLongSharedEgress(sid,in.customerId(),decision.lineId(),String.valueOf(line.get("current_public_ip")),
                        expire.plusDays(((Number)pkg.get("grace_days")).intValue()));
                ctx.put("lineId",decision.lineId());}
            if(decision.poolId()==null)ctx.put("resourceMode","PRODUCT_SPECIFIC");
            ServiceProvisionAdapter.Result result=adapter.provision(productType,sid,ctx);if(!result.success())throw err("DELIVERY_ADAPTER_FAILED",result.message());
        }else{
            db.bind(sid,"SUPPLIER_PURCHASE",purchaseId,"PRIMARY",j(Map.of("deliveryMode","EXTERNAL_PROXY")),actor.userId());db.bind(sid,"REVERSE_PROXY",reverseProxyId,"ENTRY",j(Map.of("deliveryMode","EXTERNAL_PROXY")),actor.userId());if(db.attachReverseProxy(reverseProxyId,sid,actor.userId())!=1)throw err("PROXY_ATTACH_FAILED","无法绑定反向代理");ctx.put("reverseProxyId",reverseProxyId);
        }
        Map<String,Object> credential;
        if("EXTERNAL_PROXY".equals(delivery)){
            credential=Map.of("type","EXISTING_PLATFORM_AUTH","account",Objects.toString(externalProxy.get("platform_account"),""),
                "mask","平台代理已有认证配置；不生成未下发的新密钥","secret","");
        }else credential=issueCredential(sid,serviceNo,productType,String.valueOf(product.get("auth_modes_json")),expire,actor.userId());
        String deliveryStage="EXTERNAL_PROXY".equals(delivery)?"MANAGEMENT_ONLY":
            adapter instanceof com.ipproxy.platform.service.adapter.NoopServiceProvisionAdapter?"MANAGEMENT_ONLY":
            adapter instanceof com.ipproxy.platform.service.adapter.MockServiceProvisionAdapter?"MOCK_ONLY":"ADAPTER_ACK_UNVERIFIED";
        db.serviceStatus(sid,"ACTIVE",null,actor.userId());db.deliveryStage(sid,deliveryStage);
        audit.success(actor,"SERVICE","SERVICE",sid,"PROVISION_V12",n(in.remark()).isBlank()?"V1.2管理面开通":in.remark(),ip);
        Map<String,Object> out=new LinkedHashMap<>();out.put("serviceId",sid);out.put("serviceNo",serviceNo);
        out.put("deliveryMode",delivery);out.put("builtinPolicyCode",policyCode);out.put("status","ACTIVE");
        out.put("oneTimeCredential",credential);out.put("deliveryStage",deliveryStage);
        return out;}

    private Map<String,Object> issueCredential(Long serviceId,String serviceNo,String productType,String authModesJson,OffsetDateTime expire,Long actor){
        String auth=n(authModesJson).toUpperCase(Locale.ROOT);
        if(auth.contains("WHITELIST")&&!auth.contains("TOKEN")&&!auth.contains("USERNAME_PASSWORD"))
            return Map.of("type","WHITELIST","account","","mask","请先配置客户访问IP白名单","secret","");
        String type=auth.contains("USERNAME_PASSWORD")?"USERNAME_PASSWORD":auth.contains("TOKEN")?"TOKEN":switch(productType){case "VPN"->"USERNAME_PASSWORD";case "TUNNEL"->"TUNNEL_ACCOUNT";default->"TOKEN";};String secret=UUID.randomUUID().toString().replace("-","")+Long.toString(System.nanoTime(),36);String account="TOKEN".equals(type)?null:"svc_"+serviceNo.toLowerCase(Locale.ROOT);String mask=secret.substring(0,6)+"••••••"+secret.substring(secret.length()-4);Long id=db.insertCredential(serviceId,type,account,sha(secret),mask,expire,j(Map.of("issuedBy","V1.2")),actor);return Map.of("credentialId",id,"type",type,"account",account==null?"":account,"secret",secret,"mask",mask);}
    private String sha(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private Map<String,Object> mustService(Long id){Map<String,Object>x=db.service(id);if(x==null)throw err("SERVICE_NOT_FOUND","服务不存在");return x;}
    private String n(String x){return x==null?"":x.trim();}private String required(String x,String msg){if(n(x).isBlank())throw err("V12_REQUIRED",msg);return x.trim();}private String j(Object x){try{return json.writeValueAsString(x==null?Map.of():x);}catch(Exception e){return "{}";}}private BusinessException err(String code,String msg){return new BusinessException(code,msg);}

    public record ProvisionInput(Long customerId,Long productId,Long packageId,String deliveryMode,Long supplierPurchaseId,Long reverseProxyId,OffsetDateTime effectiveAt,OffsetDateTime expireAt,String remark){}
}
