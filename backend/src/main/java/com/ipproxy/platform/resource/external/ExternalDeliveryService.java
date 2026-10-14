package com.ipproxy.platform.resource.external;

import com.ipproxy.platform.common.api.PageResult;
import com.ipproxy.platform.common.audit.OperationAuditRepository;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.resource.security.ResourceSecretCipher;
import com.ipproxy.platform.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ExternalDeliveryService {
    private final ExternalDeliveryMapper db; private final ResourceSecretCipher cipher; private final ReverseProxyAdapter adapter; private final OperationAuditRepository audit;
    public ExternalDeliveryService(ExternalDeliveryMapper db,ResourceSecretCipher cipher,ReverseProxyAdapter adapter,OperationAuditRepository audit){this.db=db;this.cipher=cipher;this.adapter=adapter;this.audit=audit;}

    public Map<String,Object> options(Long customerId){return Map.of("suppliers",db.supplierOptions(),"customers",db.customerOptions(),"purchases",db.purchaseOptions(),"services",db.serviceOptions(customerId));}

    public List<Map<String,Object>> paidOrdersForCustomer(Long customerId){
        if(customerId==null)throw new BusinessException("CUSTOMER_REQUIRED","请先选择采购归属客户");
        return db.paidOrdersForCustomer(customerId);
    }
    public PageResult<Map<String,Object>> purchases(int page,int size,String keyword,String status,Long supplierId,Long customerId){page=page(page);size=size(size);String k=n(keyword),s=n(status);return new PageResult<>(page,size,db.countPurchases(k,s,supplierId,customerId),db.purchases(k,s,supplierId,customerId,size,(page-1)*size));}
    public Map<String,Object> purchase(Long id){Map<String,Object>x=db.purchase(id);if(x==null)throw notFound("外部套餐采购");Map<String,Object> out=new LinkedHashMap<>(x);out.remove("purchase_secret_cipher");out.put("secretConfigured",x.get("purchase_secret_cipher")!=null);return out;}
    @Transactional public Long createPurchase(PurchaseInput in,UserPrincipal actor,String ip){validatePurchase(in);requirePaidCustomerOrder(in);String no="PUR-"+OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))+"-"+Integer.toHexString(new Random().nextInt(65536)).toUpperCase(Locale.ROOT);Long id=db.insertPurchase(no,in.supplierId(),in.customerId(),in.supplierPackageName().trim(),n(in.supplierPackageNo()),n(in.purchaseAccount()),cipher.encrypt(in.purchaseSecret()),n(in.interfaceEndpoint()),blankNull(in.packageQuota()),blankNull(in.remainingQuota()),in.purchaseCost(),currency(in.currency()),in.effectiveAt(),in.expireAt(),n(in.remark()),actor.userId());if(in.customerId()!=null)db.linkPurchaseToPaidOrder(id,in.sourceCustomerOrderId());audit.success(actor,"EXTERNAL_DELIVERY","SUPPLIER_PURCHASE",id,"CREATE","新增外部供应商套餐采购",ip);return id;}
    @Transactional public void updatePurchase(Long id,PurchaseInput in,UserPrincipal actor,String ip){Map<String,Object> original=purchase(id);validatePurchase(in);if(!Objects.equals(number(original.get("customer_id")),in.customerId()))throw new BusinessException("PURCHASE_OWNER_CHANGE_BLOCKED","采购所属客户不可直接修改");db.updatePurchase(id,in.customerId(),in.supplierPackageName().trim(),n(in.supplierPackageNo()),n(in.purchaseAccount()),n(in.purchaseSecret()).isBlank()?null:cipher.encrypt(in.purchaseSecret()),n(in.interfaceEndpoint()),blankNull(in.packageQuota()),blankNull(in.remainingQuota()),in.purchaseCost(),currency(in.currency()),in.effectiveAt(),in.expireAt(),n(in.remark()),actor.userId());audit.success(actor,"EXTERNAL_DELIVERY","SUPPLIER_PURCHASE",id,"UPDATE","修改外部套餐采购",ip);}
    @Transactional public void purchaseStatus(Long id,String status,String reason,UserPrincipal actor,String ip){purchase(id);String s=n(status).toUpperCase(Locale.ROOT);if(!Set.of("ACTIVE","EXPIRED","DISABLED").contains(s))throw new BusinessException("PURCHASE_STATUS_INVALID","不支持的采购状态");db.purchaseStatus(id,s,actor.userId());audit.success(actor,"EXTERNAL_DELIVERY","SUPPLIER_PURCHASE",id,"STATUS_"+s,required(reason,"操作原因不能为空"),ip);}

    public PageResult<Map<String,Object>> proxies(int page,int size,String keyword,String status,Long customerId){page=page(page);size=size(size);String k=n(keyword),s=n(status);return new PageResult<>(page,size,db.countProxies(k,s,customerId),db.proxies(k,s,customerId,size,(page-1)*size));}
    public Map<String,Object> proxy(Long id){Map<String,Object>x=db.proxy(id);if(x==null)throw notFound("反向代理");Map<String,Object> out=new LinkedHashMap<>(x);out.remove("upstream_secret_cipher");out.remove("platform_secret_cipher");out.put("upstreamSecretConfigured",x.get("upstream_secret_cipher")!=null);out.put("platformSecretConfigured",x.get("platform_secret_cipher")!=null);out.put("operationLogs",db.proxyLogs(id,100));return out;}
    @Transactional public Long createProxy(ProxyInput in,UserPrincipal actor,String ip){validateProxy(in);Map<String,Object> purchase=purchase(in.purchaseId());Long customerId=in.customerId()!=null?in.customerId():number(purchase.get("customer_id"));if(customerId==null)throw new BusinessException("PROXY_CUSTOMER_REQUIRED","反向代理必须绑定客户");if(purchase.get("customer_id")!=null&&!Objects.equals(number(purchase.get("customer_id")),customerId))throw new BusinessException("PROXY_PURCHASE_CUSTOMER_MISMATCH","反向代理与采购套餐的客户归属不一致");if(db.hasOtherPurchaseCustomer(in.purchaseId(),customerId))throw new BusinessException("PROXY_PURCHASE_CROSS_CUSTOMER","同一采购套餐已被其他客户占用，V1.2禁止跨客户共享");String no="RP-"+OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))+"-"+Integer.toHexString(new Random().nextInt(65536)).toUpperCase(Locale.ROOT);Long id=db.insertProxy(no,in.purchaseId(),customerId,in.serviceId(),in.protocolCode().trim().toUpperCase(Locale.ROOT),in.upstreamHost().trim(),in.upstreamPort(),n(in.upstreamAccount()),cipher.encrypt(in.upstreamSecret()),in.platformHost().trim(),in.platformPort(),n(in.platformAccount()),cipher.encrypt(in.platformSecret()),in.effectiveAt(),in.expireAt(),n(in.remark()),actor.userId());db.proxyLog(id,"CREATE","SUCCESS","创建反向代理配置",actor.userId());audit.success(actor,"EXTERNAL_DELIVERY","REVERSE_PROXY",id,"CREATE","创建反向代理交付",ip);return id;}
    @Transactional public void updateProxy(Long id,ProxyInput in,UserPrincipal actor,String ip){proxy(id);validateProxy(in);db.updateProxy(id,in.serviceId(),in.protocolCode().trim().toUpperCase(Locale.ROOT),in.upstreamHost().trim(),in.upstreamPort(),n(in.upstreamAccount()),n(in.upstreamSecret()).isBlank()?null:cipher.encrypt(in.upstreamSecret()),in.platformHost().trim(),in.platformPort(),n(in.platformAccount()),n(in.platformSecret()).isBlank()?null:cipher.encrypt(in.platformSecret()),in.effectiveAt(),in.expireAt(),n(in.remark()),actor.userId());db.proxyLog(id,"UPDATE","SUCCESS","修改反向代理配置",actor.userId());audit.success(actor,"EXTERNAL_DELIVERY","REVERSE_PROXY",id,"UPDATE","修改反向代理交付",ip);}
    // Noop/PENDING / transport ACK must not be interpreted as actual proxy data-plane verification.
    private void realProxyResult(ReverseProxyAdapter.Result result,String action) {
        if(result==null || !result.success() || result.detail()==null
           || !Boolean.TRUE.equals(result.detail().get("verified"))) {
            throw new BusinessException("EXTERNAL_PROXY_DATA_PLANE_UNVERIFIED",
                action+"未取得真实反向代理数据面回执；保留原平台代理业务状态");
        }
    }
    @Transactional public Map<String,Object> applyProxy(Long id,String reason,UserPrincipal actor,String ip){
        String why=required(reason,"操作原因不能为空");
        Map<String,Object> x=proxy(id);
        ReverseProxyAdapter.Result r=adapter.apply(id,x);
        realProxyResult(r,"反向代理启用");
        db.proxyStatus(id,"ACTIVE",null,actor.userId());
        db.proxyLog(id,"APPLY","SUCCESS",r.message(),actor.userId());
        audit.success(actor,"EXTERNAL_DELIVERY","REVERSE_PROXY",id,"APPLY",why,ip);
        return Map.of("status","ACTIVE","message",r.message(),"detail",r.detail());
    }
    @Transactional public Map<String,Object> testProxy(Long id,String reason,UserPrincipal actor,String ip){
        String why=required(reason,"操作原因不能为空");
        Map<String,Object> x=proxy(id);
        ReverseProxyAdapter.Result r=adapter.test(id,x);
        realProxyResult(r,"反向代理检测");
        db.proxyLog(id,"TEST","SUCCESS",r.message(),actor.userId());
        audit.success(actor,"EXTERNAL_DELIVERY","REVERSE_PROXY",id,"TEST",why,ip);
        return Map.of("success",true,"message",r.message(),"detail",r.detail());
    }
    @Transactional public Map<String,Object> disableProxy(Long id,String reason,UserPrincipal actor,String ip){
        String why=required(reason,"操作原因不能为空");
        Map<String,Object> x=proxy(id);
        ReverseProxyAdapter.Result r=adapter.disable(id,x);
        realProxyResult(r,"反向代理停用");
        db.proxyStatus(id,"DISABLED",null,actor.userId());
        db.proxyLog(id,"DISABLE","SUCCESS",r.message(),actor.userId());
        audit.success(actor,"EXTERNAL_DELIVERY","REVERSE_PROXY",id,"DISABLE",why,ip);
        return Map.of("status","DISABLED","message",r.message(),"detail",r.detail());
    }

    private void requirePaidCustomerOrder(PurchaseInput in) {
        if(in.customerId()==null)return;
        if(in.sourceCustomerOrderId()==null)
            throw new BusinessException("PURCHASE_PAID_ORDER_REQUIRED","新增客户采购必须关联已核实收款的订单");
        Map<String,Object> order=db.paymentConfirmedOrder(in.sourceCustomerOrderId());
        if(order==null || !Objects.equals(number(order.get("customer_id")),in.customerId())
            || "CANCELLED".equals(order.get("status"))
            || !"PAID_CONFIRMED".equals(order.get("payment_status")))
            throw new BusinessException("PURCHASE_PAYMENT_NOT_VERIFIED","客户付款尚未核实，禁止进行客户专属供应商采购");
    }
    private String blankNull(String v){return n(v).isBlank()?null:v.trim();}
    private void validatePurchase(PurchaseInput in){if(in==null||in.supplierId()==null)throw new BusinessException("PURCHASE_REQUIRED","供应商不能为空");required(in.supplierPackageName(),"供应商套餐名称不能为空");if(in.expireAt()!=null&&in.effectiveAt()!=null&&in.expireAt().isBefore(in.effectiveAt()))throw new BusinessException("PURCHASE_TIME_INVALID","到期时间不能早于生效时间");}
    private void validateProxy(ProxyInput in){if(in==null||in.purchaseId()==null)throw new BusinessException("PROXY_PURCHASE_REQUIRED","外部采购不能为空");required(in.protocolCode(),"代理协议不能为空");required(in.upstreamHost(),"上游地址不能为空");required(in.platformHost(),"平台代理地址不能为空");if(in.upstreamPort()==null||in.upstreamPort()<1||in.upstreamPort()>65535||in.platformPort()==null||in.platformPort()<1||in.platformPort()>65535)throw new BusinessException("PROXY_PORT_INVALID","端口必须在1-65535之间");}
    private int page(int v){return Math.max(1,v);} private int size(int v){return Math.min(200,Math.max(1,v));}
    private String n(String v){return v==null?"":v.trim();} private String required(String v,String msg){if(n(v).isBlank())throw new BusinessException("V12_REQUIRED",msg);return v.trim();}
    private String currency(String v){return n(v).isBlank()?"CNY":v.trim().toUpperCase(Locale.ROOT);}
    private Long number(Object x){return x instanceof Number n?n.longValue():x==null?null:Long.valueOf(String.valueOf(x));}
    private BusinessException notFound(String n){return new BusinessException("EXTERNAL_DELIVERY_NOT_FOUND",n+"不存在");}

    public record PurchaseInput(Long supplierId,Long customerId,String supplierPackageName,String supplierPackageNo,String purchaseAccount,String purchaseSecret,String interfaceEndpoint,String packageQuota,String remainingQuota,BigDecimal purchaseCost,String currency,OffsetDateTime effectiveAt,OffsetDateTime expireAt,String remark,Long sourceCustomerOrderId){}
    public record ProxyInput(Long purchaseId,Long customerId,Long serviceId,String protocolCode,String upstreamHost,Integer upstreamPort,String upstreamAccount,String upstreamSecret,String platformHost,Integer platformPort,String platformAccount,String platformSecret,OffsetDateTime effectiveAt,OffsetDateTime expireAt,String remark){}
}
