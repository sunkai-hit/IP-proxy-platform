package com.ipproxy.platform.service.v12;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.*;

@Repository
public class V12ServiceMapper {
    private final JdbcTemplate jdbc;
    public V12ServiceMapper(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private Map<String,Object> one(String sql,Object...args){List<Map<String,Object>> rows=jdbc.queryForList(sql,args);return rows.isEmpty()?null:rows.getFirst();}

    public String nextServiceNo(){return jdbc.queryForObject("SELECT 'S'||to_char(current_date,'YYYYMMDD')||lpad(nextval('seq_service_no')::text,6,'0')",String.class);}
    public Map<String,Object> customer(Long id){return one("SELECT id,customer_code,customer_name,status FROM customer WHERE id=? AND deleted=FALSE",id);}
    public Map<String,Object> product(Long id){return one("SELECT id,product_code,product_name,product_type,status,builtin_policy_code,to_jsonb(prd_product)::text product_json FROM prd_product WHERE id=? AND deleted=FALSE",id);}
    public Map<String,Object> pkg(Long id){return one("SELECT id,package_code,package_name,product_id,service_days,price,currency,status,to_jsonb(prd_package)::text package_json FROM prd_package WHERE id=? AND deleted=FALSE",id);}
    public Map<String,Object> order(Long id){return one("SELECT o.*,c.customer_name FROM biz_order o JOIN customer c ON c.id=o.customer_id WHERE o.id=? AND o.deleted=FALSE",id);}
    public List<Map<String,Object>> orderItems(Long id){return jdbc.queryForList("SELECT i.*,p.product_code,p.product_name,p.product_type,k.package_code,k.package_name FROM biz_order_item i JOIN prd_product p ON p.id=i.product_id LEFT JOIN prd_package k ON k.id=i.package_id WHERE i.order_id=? ORDER BY i.line_no",id);}
    public int orderStatus(Long id,String status,Long actor){return jdbc.update("UPDATE biz_order SET status=?,updated_at=now(),updated_by=?,completed_at=CASE WHEN ?='COMPLETED' THEN now() ELSE completed_at END WHERE id=? AND deleted=FALSE",status,actor,status,id);}

    public Long insertService(String no,Long customerId,Long orderId,Long itemId,Long productId,Long packageId,String productType,String productJson,String packageJson,String quotaJson,OffsetDateTime effective,OffsetDateTime expire,String deliveryMode,String policyCode,Long purchaseId,Long reverseProxyId,Long actor){return jdbc.queryForObject("""
      INSERT INTO svc_instance(service_no,customer_id,order_id,order_item_id,product_id,package_id,strategy_id,product_type,product_snapshot,package_snapshot,strategy_snapshot,quota_snapshot,effective_at,expire_at,status,delivery_mode,builtin_policy_code,supplier_purchase_id,reverse_proxy_id,created_by,updated_by)
      VALUES(?,?,?,?,?,?,NULL,?,CAST(? AS jsonb),CAST(NULLIF(?,'') AS jsonb),NULL,CAST(? AS jsonb),?,?,'PENDING',?,?,?,?,?,?) RETURNING id
      """,Long.class,no,customerId,orderId,itemId,productId,packageId,productType,productJson,packageJson==null?"":packageJson,quotaJson,effective,expire,deliveryMode,policyCode,purchaseId,reverseProxyId,actor,actor);}
    public int serviceStatus(Long id,String status,String failure,Long actor){return jdbc.update("UPDATE svc_instance SET status=?,failure_reason=?,updated_at=now(),updated_by=?,version=version+1 WHERE id=? AND deleted=FALSE",status,failure,actor,id);}
    public Long bind(Long serviceId,String type,Long resourceId,String role,String metadata,Long actor){return jdbc.queryForObject("INSERT INTO svc_resource_binding(service_id,resource_type,resource_id,binding_role,metadata,created_by) VALUES(?,?,?,?,CAST(? AS jsonb),?) RETURNING id",Long.class,serviceId,type,resourceId,role,metadata,actor);}
    public Long allocateExclusiveLine(Long serviceId,Long lineId,Long customerId,OffsetDateTime expire,String currentIp,String snapshot,Long actor){return jdbc.queryForObject("""
      INSERT INTO res_exclusive_allocation(allocation_no,resource_type,ip_id,line_id,customer_id,service_id,status,locked_at,effective_at,expire_at,current_ip_snapshot,service_snapshot,created_by,updated_by)
      VALUES('EA-'||replace(gen_random_uuid()::text,'-',''),'LINE',NULL,?,?,?,'ALLOCATED',now(),now(),?,CAST(NULLIF(?,'') AS inet),CAST(? AS jsonb),?,?) RETURNING id
      """,Long.class,lineId,customerId,serviceId,expire,currentIp,snapshot,actor,actor);}
    public int attachReverseProxy(Long proxyId,Long serviceId,Long actor){return jdbc.update("UPDATE res_reverse_proxy SET service_id=?,updated_at=now(),updated_by=?,version=version+1 WHERE id=? AND deleted=FALSE",serviceId,actor,proxyId);}
    public Long insertCredential(Long serviceId,String type,String account,String hash,String mask,OffsetDateTime expire,String metadata,Long actor){return jdbc.queryForObject("INSERT INTO svc_credential(service_id,credential_type,account_name,secret_hash,secret_mask,expire_at,metadata,created_by,updated_by) VALUES(?,?,?,?,?,?,CAST(? AS jsonb),?,?) RETURNING id",Long.class,serviceId,type,account,hash,mask,expire,metadata,actor,actor);}

    public long countServices(String keyword,String status,Long customerId,String productType,String deliveryMode){return jdbc.queryForObject("""
      SELECT count(*) FROM svc_instance s JOIN customer c ON c.id=s.customer_id WHERE s.deleted=FALSE
      AND (?='' OR s.service_no ILIKE '%'||?||'%' OR c.customer_name ILIKE '%'||?||'%') AND (?='' OR s.status=?)
      AND (? IS NULL OR s.customer_id=?) AND (?='' OR s.product_type=?) AND (?='' OR s.delivery_mode=?)
      """,Long.class,keyword,keyword,keyword,status,status,customerId,customerId,productType,productType,deliveryMode,deliveryMode);}
    public List<Map<String,Object>> services(String keyword,String status,Long customerId,String productType,String deliveryMode,int size,int offset){return jdbc.queryForList("""
      SELECT s.id,s.service_no,s.customer_id,c.customer_code,c.customer_name,s.order_id,o.order_no,s.product_id,p.product_name,s.package_id,k.package_name,
        s.product_type,s.delivery_mode,s.builtin_policy_code,s.supplier_purchase_id,sp.purchase_no,s.reverse_proxy_id,rp.proxy_no,rp.platform_host,rp.platform_port,
        s.effective_at,s.expire_at,s.status,s.failure_reason,s.created_at,
        (SELECT string_agg(b.resource_type||':'||b.resource_id::text,', ' ORDER BY b.id) FROM svc_resource_binding b WHERE b.service_id=s.id AND b.status='ACTIVE') resource_summary
      FROM svc_instance s JOIN customer c ON c.id=s.customer_id LEFT JOIN biz_order o ON o.id=s.order_id JOIN prd_product p ON p.id=s.product_id LEFT JOIN prd_package k ON k.id=s.package_id
      LEFT JOIN res_supplier_purchase sp ON sp.id=s.supplier_purchase_id LEFT JOIN res_reverse_proxy rp ON rp.id=s.reverse_proxy_id
      WHERE s.deleted=FALSE AND (?='' OR s.service_no ILIKE '%'||?||'%' OR c.customer_name ILIKE '%'||?||'%') AND (?='' OR s.status=?)
      AND (? IS NULL OR s.customer_id=?) AND (?='' OR s.product_type=?) AND (?='' OR s.delivery_mode=?) ORDER BY s.created_at DESC,s.id DESC LIMIT ? OFFSET ?
      """,keyword,keyword,keyword,status,status,customerId,customerId,productType,productType,deliveryMode,deliveryMode,size,offset);}
    public Map<String,Object> service(Long id){return one("""
      SELECT s.*,c.customer_code,c.customer_name,o.order_no,p.product_code,p.product_name,k.package_code,k.package_name,sp.purchase_no,sp.supplier_package_name,rp.proxy_no,rp.platform_host,rp.platform_port,rp.status reverse_proxy_status
      FROM svc_instance s JOIN customer c ON c.id=s.customer_id LEFT JOIN biz_order o ON o.id=s.order_id JOIN prd_product p ON p.id=s.product_id LEFT JOIN prd_package k ON k.id=s.package_id
      LEFT JOIN res_supplier_purchase sp ON sp.id=s.supplier_purchase_id LEFT JOIN res_reverse_proxy rp ON rp.id=s.reverse_proxy_id WHERE s.id=? AND s.deleted=FALSE
      """,id);}
    public List<Map<String,Object>> bindings(Long id){return jdbc.queryForList("SELECT id,resource_type,resource_id,binding_role,effective_at,released_at,status,metadata,created_at FROM svc_resource_binding WHERE service_id=? ORDER BY created_at,id",id);}
    public List<Map<String,Object>> credentials(Long id){return jdbc.queryForList("SELECT id,credential_type,account_name,secret_mask,status,issued_at,expire_at,last_used_at,rotated_at,metadata FROM svc_credential WHERE service_id=? AND deleted=FALSE ORDER BY issued_at DESC",id);}
    public List<Map<String,Object>> whitelist(Long id){return jdbc.queryForList("SELECT id,host(ip_address) ip_address,status,remark,created_at FROM svc_whitelist WHERE service_id=? AND deleted=FALSE ORDER BY created_at DESC",id);}
    public List<Map<String,Object>> changes(Long id){return jdbc.queryForList("SELECT id,change_no,change_type,before_snapshot,after_snapshot,reason,status,operator_id,started_at,finished_at,error_message,created_at FROM svc_change WHERE service_id=? AND deleted=FALSE ORDER BY created_at DESC",id);}
    public List<Map<String,Object>> releaseRecords(Long id){return jdbc.queryForList("SELECT * FROM svc_release_record WHERE service_id=? ORDER BY created_at DESC",id);}
    public List<Map<String,Object>> longMonitorRecords(Long id){return jdbc.queryForList("SELECT id,monitor_no,target_host,target_port,current_status,enabled,last_checked_at,last_error FROM lm_monitor WHERE service_id=? AND deleted=FALSE ORDER BY created_at DESC",id);}

    public Map<String,Object> reverseProxy(Long id){return one("SELECT rp.*,p.status purchase_status,p.customer_id purchase_customer_id FROM res_reverse_proxy rp JOIN res_supplier_purchase p ON p.id=rp.purchase_id WHERE rp.id=? AND rp.deleted=FALSE",id);}
    public Map<String,Object> line(Long id){return one("SELECT l.id,l.resource_code,host(l.current_public_ip) current_public_ip,l.http_port,l.socks5_port,l.domain_name,l.vpn_supported,l.online_status,l.line_type,l.customer_id FROM res_line l WHERE l.id=? AND l.deleted=FALSE",id);}
}
