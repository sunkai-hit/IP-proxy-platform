package com.ipproxy.platform.resource.external;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Repository
public class ExternalDeliveryMapper {
    private final JdbcTemplate jdbc;
    public ExternalDeliveryMapper(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private Map<String,Object> one(String sql,Object...args){List<Map<String,Object>> rows=jdbc.queryForList(sql,args);return rows.isEmpty()?null:rows.getFirst();}

    public long countPurchases(String k,String status,Long supplierId,Long customerId){return jdbc.queryForObject("""
      SELECT count(*) FROM res_supplier_purchase p JOIN res_supplier s ON s.id=p.supplier_id LEFT JOIN customer c ON c.id=p.customer_id
      WHERE p.deleted=FALSE AND (?='' OR p.purchase_no ILIKE '%'||?||'%' OR s.supplier_name ILIKE '%'||?||'%' OR p.supplier_package_name ILIKE '%'||?||'%' OR COALESCE(c.customer_name,'') ILIKE '%'||?||'%')
      AND (?='' OR p.status=?) AND (? IS NULL OR p.supplier_id=?) AND (? IS NULL OR p.customer_id=?)
      """,Long.class,k,k,k,k,k,status,status,supplierId,supplierId,customerId,customerId);}
    public List<Map<String,Object>> purchases(String k,String status,Long supplierId,Long customerId,int size,int offset){return jdbc.queryForList("""
      SELECT p.id,p.purchase_no,p.supplier_id,s.supplier_code,s.supplier_name,p.customer_id,c.customer_name,p.supplier_package_name,p.supplier_package_no,
        p.purchase_account,p.interface_endpoint,p.package_quota,p.remaining_quota,p.purchase_cost,p.currency,p.effective_at,p.expire_at,p.status,p.remark,
        (p.purchase_secret_cipher IS NOT NULL) secret_configured,p.created_at,p.updated_at
      FROM res_supplier_purchase p JOIN res_supplier s ON s.id=p.supplier_id LEFT JOIN customer c ON c.id=p.customer_id
      WHERE p.deleted=FALSE AND (?='' OR p.purchase_no ILIKE '%'||?||'%' OR s.supplier_name ILIKE '%'||?||'%' OR p.supplier_package_name ILIKE '%'||?||'%' OR COALESCE(c.customer_name,'') ILIKE '%'||?||'%')
      AND (?='' OR p.status=?) AND (? IS NULL OR p.supplier_id=?) AND (? IS NULL OR p.customer_id=?)
      ORDER BY p.updated_at DESC,p.id DESC LIMIT ? OFFSET ?
      """,k,k,k,k,k,status,status,supplierId,supplierId,customerId,customerId,size,offset);}
    public Map<String,Object> purchase(Long id){return one("""
      SELECT p.id,p.purchase_no,p.supplier_id,s.supplier_code,s.supplier_name,p.customer_id,c.customer_name,p.supplier_package_name,p.supplier_package_no,
        p.purchase_account,p.purchase_secret_cipher,p.interface_endpoint,p.package_quota,p.remaining_quota,p.purchase_cost,p.currency,p.effective_at,p.expire_at,p.status,p.remark,p.created_at,p.updated_at
      FROM res_supplier_purchase p JOIN res_supplier s ON s.id=p.supplier_id LEFT JOIN customer c ON c.id=p.customer_id WHERE p.id=? AND p.deleted=FALSE
      """,id);}
    public Long insertPurchase(String no,Long supplierId,Long customerId,String packageName,String packageNo,String account,String secret,String endpoint,String quota,String remaining,BigDecimal cost,String currency,OffsetDateTime effective,OffsetDateTime expire,String remark,Long actor){return jdbc.queryForObject("""
      INSERT INTO res_supplier_purchase(purchase_no,supplier_id,customer_id,supplier_package_name,supplier_package_no,purchase_account,purchase_secret_cipher,interface_endpoint,package_quota,remaining_quota,purchase_cost,currency,effective_at,expire_at,status,remark,created_by,updated_by)
      VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,'ACTIVE',?,?,?) RETURNING id
      """,Long.class,no,supplierId,customerId,packageName,packageNo,account,secret,endpoint,quota,remaining,cost,currency,effective,expire,remark,actor,actor);}
    public int updatePurchase(Long id,Long customerId,String packageName,String packageNo,String account,String secret,String endpoint,String quota,String remaining,BigDecimal cost,String currency,OffsetDateTime effective,OffsetDateTime expire,String remark,Long actor){String sql=secret==null?"""
      UPDATE res_supplier_purchase SET customer_id=?,supplier_package_name=?,supplier_package_no=?,purchase_account=?,interface_endpoint=?,package_quota=?,remaining_quota=?,purchase_cost=?,currency=?,effective_at=?,expire_at=?,remark=?,updated_at=now(),updated_by=?,version=version+1 WHERE id=? AND deleted=FALSE
      """:"""
      UPDATE res_supplier_purchase SET customer_id=?,supplier_package_name=?,supplier_package_no=?,purchase_account=?,purchase_secret_cipher=?,interface_endpoint=?,package_quota=?,remaining_quota=?,purchase_cost=?,currency=?,effective_at=?,expire_at=?,remark=?,updated_at=now(),updated_by=?,version=version+1 WHERE id=? AND deleted=FALSE
      """;return secret==null?jdbc.update(sql,customerId,packageName,packageNo,account,endpoint,quota,remaining,cost,currency,effective,expire,remark,actor,id):jdbc.update(sql,customerId,packageName,packageNo,account,secret,endpoint,quota,remaining,cost,currency,effective,expire,remark,actor,id);}
    public int purchaseStatus(Long id,String status,Long actor){return jdbc.update("UPDATE res_supplier_purchase SET status=?,updated_at=now(),updated_by=?,version=version+1 WHERE id=? AND deleted=FALSE",status,actor,id);}

    public long countProxies(String k,String status,Long customerId){return jdbc.queryForObject("""
      SELECT count(*) FROM res_reverse_proxy rp JOIN res_supplier_purchase p ON p.id=rp.purchase_id JOIN res_supplier s ON s.id=p.supplier_id JOIN customer c ON c.id=rp.customer_id
      WHERE rp.deleted=FALSE AND (?='' OR rp.proxy_no ILIKE '%'||?||'%' OR c.customer_name ILIKE '%'||?||'%' OR rp.platform_host ILIKE '%'||?||'%' OR s.supplier_name ILIKE '%'||?||'%')
      AND (?='' OR rp.status=?) AND (? IS NULL OR rp.customer_id=?)
      """,Long.class,k,k,k,k,k,status,status,customerId,customerId);}
    public List<Map<String,Object>> proxies(String k,String status,Long customerId,int size,int offset){return jdbc.queryForList("""
      SELECT rp.id,rp.proxy_no,rp.purchase_id,p.purchase_no,s.supplier_name,rp.customer_id,c.customer_name,rp.service_id,si.service_no,rp.protocol_code,
        rp.upstream_host,rp.upstream_port,rp.upstream_account,(rp.upstream_secret_cipher IS NOT NULL) upstream_secret_configured,
        rp.platform_host,rp.platform_port,rp.platform_account,(rp.platform_secret_cipher IS NOT NULL) platform_secret_configured,
        rp.effective_at,rp.expire_at,rp.status,rp.last_checked_at,rp.last_error,rp.remark,rp.created_at,rp.updated_at
      FROM res_reverse_proxy rp JOIN res_supplier_purchase p ON p.id=rp.purchase_id JOIN res_supplier s ON s.id=p.supplier_id JOIN customer c ON c.id=rp.customer_id LEFT JOIN svc_instance si ON si.id=rp.service_id
      WHERE rp.deleted=FALSE AND (?='' OR rp.proxy_no ILIKE '%'||?||'%' OR c.customer_name ILIKE '%'||?||'%' OR rp.platform_host ILIKE '%'||?||'%' OR s.supplier_name ILIKE '%'||?||'%')
      AND (?='' OR rp.status=?) AND (? IS NULL OR rp.customer_id=?) ORDER BY rp.updated_at DESC,rp.id DESC LIMIT ? OFFSET ?
      """,k,k,k,k,k,status,status,customerId,customerId,size,offset);}
    public Map<String,Object> proxy(Long id){return one("""
      SELECT rp.*,p.purchase_no,p.supplier_id,s.supplier_name,c.customer_name,si.service_no FROM res_reverse_proxy rp
      JOIN res_supplier_purchase p ON p.id=rp.purchase_id JOIN res_supplier s ON s.id=p.supplier_id JOIN customer c ON c.id=rp.customer_id LEFT JOIN svc_instance si ON si.id=rp.service_id
      WHERE rp.id=? AND rp.deleted=FALSE
      """,id);}
    public Long insertProxy(String no,Long purchaseId,Long customerId,Long serviceId,String protocol,String upstreamHost,Integer upstreamPort,String upstreamAccount,String upstreamSecret,String platformHost,Integer platformPort,String platformAccount,String platformSecret,OffsetDateTime effective,OffsetDateTime expire,String remark,Long actor){return jdbc.queryForObject("""
      INSERT INTO res_reverse_proxy(proxy_no,purchase_id,customer_id,service_id,protocol_code,upstream_host,upstream_port,upstream_account,upstream_secret_cipher,platform_host,platform_port,platform_account,platform_secret_cipher,effective_at,expire_at,status,remark,created_by,updated_by)
      VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'PENDING',?,?,?) RETURNING id
      """,Long.class,no,purchaseId,customerId,serviceId,protocol,upstreamHost,upstreamPort,upstreamAccount,upstreamSecret,platformHost,platformPort,platformAccount,platformSecret,effective,expire,remark,actor,actor);}
    public int updateProxy(Long id,Long serviceId,String protocol,String upstreamHost,Integer upstreamPort,String upstreamAccount,String upstreamSecret,String platformHost,Integer platformPort,String platformAccount,String platformSecret,OffsetDateTime effective,OffsetDateTime expire,String remark,Long actor){return jdbc.update("""
      UPDATE res_reverse_proxy SET service_id=?,protocol_code=?,upstream_host=?,upstream_port=?,upstream_account=?,upstream_secret_cipher=COALESCE(?,upstream_secret_cipher),platform_host=?,platform_port=?,platform_account=?,platform_secret_cipher=COALESCE(?,platform_secret_cipher),effective_at=?,expire_at=?,remark=?,updated_at=now(),updated_by=?,version=version+1 WHERE id=? AND deleted=FALSE
      """,serviceId,protocol,upstreamHost,upstreamPort,upstreamAccount,upstreamSecret,platformHost,platformPort,platformAccount,platformSecret,effective,expire,remark,actor,id);}
    public int proxyStatus(Long id,String status,String error,Long actor){return jdbc.update("UPDATE res_reverse_proxy SET status=?,last_error=?,last_checked_at=now(),updated_at=now(),updated_by=?,version=version+1 WHERE id=? AND deleted=FALSE",status,error,actor,id);}
    public void proxyLog(Long id,String op,String status,String message,Long actor){jdbc.update("INSERT INTO res_reverse_proxy_operation_log(reverse_proxy_id,operation_type,status,message,operator_id) VALUES(?,?,?,?,?)",id,op,status,message,actor);}
    public List<Map<String,Object>> proxyLogs(Long id,int limit){return jdbc.queryForList("SELECT id,operation_type,status,message,operator_id,created_at FROM res_reverse_proxy_operation_log WHERE reverse_proxy_id=? ORDER BY created_at DESC LIMIT ?",id,limit);}

    public List<Map<String,Object>> supplierOptions(){return jdbc.queryForList("SELECT id,supplier_code code,supplier_name name FROM res_supplier WHERE deleted=FALSE AND status='ACTIVE' ORDER BY supplier_name");}
    public List<Map<String,Object>> customerOptions(){return jdbc.queryForList("SELECT id,customer_code code,customer_name name FROM customer WHERE deleted=FALSE AND status='ACTIVE' ORDER BY customer_name");}
    public List<Map<String,Object>> purchaseOptions(){return jdbc.queryForList("SELECT p.id,p.purchase_no code,p.supplier_package_name name,p.customer_id,s.supplier_name,p.expire_at FROM res_supplier_purchase p JOIN res_supplier s ON s.id=p.supplier_id WHERE p.deleted=FALSE AND p.status='ACTIVE' ORDER BY p.expire_at NULLS LAST");}
    public List<Map<String,Object>> serviceOptions(Long customerId){return jdbc.queryForList("SELECT id,service_no code,service_no||' / '||product_type name,product_type,status FROM svc_instance WHERE deleted=FALSE AND (? IS NULL OR customer_id=?) AND status IN ('PENDING','ACTIVE','SUSPENDED') ORDER BY created_at DESC",customerId,customerId);}
}
