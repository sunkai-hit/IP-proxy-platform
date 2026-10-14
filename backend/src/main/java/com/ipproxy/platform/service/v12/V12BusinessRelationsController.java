package com.ipproxy.platform.service.v12;

import com.ipproxy.platform.common.api.ApiResponse;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Read-only business-subtable drill-downs. Relationship sources are explicit:
 * purchase references, service bindings and pool membership are not inferred
 * from dynamic IP or deprecated res_line.customer_id.
 */
@RestController
@RequestMapping("/api/admin/v1/relations")
public class V12BusinessRelationsController {
    private final JdbcTemplate jdbc;
    public V12BusinessRelationsController(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    private void require(UserPrincipal p,String permission) {
        if(p==null || (!p.roles().contains("SUPER_ADMIN")&&!p.permissions().contains(permission)))
            throw new BusinessException("AUTH_FORBIDDEN","当前账号没有查看关联业务的权限");
    }
    private void existing(String table,Long id) {
        String sql=switch(table) {
            case "customer" -> "SELECT EXISTS(SELECT 1 FROM customer WHERE id=? AND deleted=FALSE)";
            case "line" -> "SELECT EXISTS(SELECT 1 FROM res_line WHERE id=? AND deleted=FALSE)";
            case "pool" -> "SELECT EXISTS(SELECT 1 FROM res_pool WHERE id=? AND deleted=FALSE)";
            case "purchase" -> "SELECT EXISTS(SELECT 1 FROM res_supplier_purchase WHERE id=? AND deleted=FALSE)";
            default -> throw new BusinessException("RELATION_TYPE_INVALID","不支持该关联业务");
        };
        if(id==null||id<=0||!Boolean.TRUE.equals(jdbc.queryForObject(sql,Boolean.class,id)))
            throw new BusinessException("RELATION_PARENT_NOT_FOUND","关联业务的主记录不存在");
    }
    private Map<String,Object> result(List<Map<String,Object>> rows,String source) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("items",rows);
        result.put("source",source);
        result.put("truncated",rows.size()==201);
        if(rows.size()>200)rows.remove(200);
        result.put("returned",rows.size());
        return result;
    }

    @GetMapping("/customers/{id}/orders")
    public ApiResponse<?> customerOrders(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p) {
        require(p,"customer:read");require(p,"order:read");existing("customer",id);
        return ApiResponse.success(result(jdbc.queryForList("""
          SELECT o.id,o.order_no,o.order_type,o.status,o.payment_status,o.total_amount,o.currency,
                 o.renewal_service_id,s.service_no renewal_service_no,o.created_at,o.confirmed_at,
                 (SELECT count(*) FROM biz_order_item i WHERE i.order_id=o.id) order_item_count,
                 (SELECT count(*) FROM svc_instance x WHERE x.order_id=o.id AND x.deleted=FALSE) service_count
          FROM biz_order o
          LEFT JOIN svc_instance s ON s.id=o.renewal_service_id AND s.deleted=FALSE
          WHERE o.deleted=FALSE AND o.customer_id=?
          ORDER BY o.created_at DESC,o.id DESC LIMIT 201
          """,id),"CUSTOMER_TO_ORDER"));
    }

    @GetMapping("/lines/{id}/pools")
    public ApiResponse<?> linePools(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p) {
        require(p,"resource:read");require(p,"resource:pool:read");existing("line",id);
        return ApiResponse.success(result(jdbc.queryForList("""
          SELECT p.id,p.pool_code,p.pool_name,p.province_code,p.city_code,p.carrier_code,
                 p.status,pl.joined_at,pl.source_reason
          FROM res_pool_line pl JOIN res_pool p ON p.id=pl.pool_id AND p.deleted=FALSE
          WHERE pl.line_id=? AND pl.enabled=TRUE
          ORDER BY p.pool_code,p.id LIMIT 201
          """,id),"LINE_POOL_MEMBERSHIP"));
    }

    @GetMapping("/lines/{id}/services")
    public ApiResponse<?> lineServices(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p) {
        require(p,"resource:read");require(p,"service:read");existing("line",id);
        return ApiResponse.success(result(jdbc.queryForList("""
          SELECT s.id,s.service_no,c.id customer_id,c.customer_name,p.product_name,
                 s.product_type,s.status,s.delivery_stage,s.expire_at,
                 EXISTS(SELECT 1 FROM svc_resource_binding b WHERE b.service_id=s.id
                   AND b.resource_type='LINE' AND b.resource_id=? AND b.status='ACTIVE')
                   direct_line_binding,
                 EXISTS(SELECT 1 FROM svc_egress_claim e WHERE e.service_id=s.id
                   AND e.source_line_id=? AND e.claim_status<>'RELEASED') active_egress_claim
          FROM svc_instance s JOIN customer c ON c.id=s.customer_id
          JOIN prd_product p ON p.id=s.product_id
          WHERE s.deleted=FALSE AND (
            EXISTS(SELECT 1 FROM svc_resource_binding b WHERE b.service_id=s.id
              AND b.resource_type='LINE' AND b.resource_id=? AND b.status='ACTIVE')
            OR EXISTS(SELECT 1 FROM svc_egress_claim e WHERE e.service_id=s.id
              AND e.source_line_id=? AND e.claim_status<>'RELEASED'))
          ORDER BY s.created_at DESC,s.id DESC LIMIT 201
          """,id,id,id,id),"DIRECT_SERVICE_LINE_BINDING_OR_EGRESS_CLAIM"));
    }

    @GetMapping("/pools/{id}/services")
    public ApiResponse<?> poolServices(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p) {
        require(p,"resource:pool:read");require(p,"service:read");existing("pool",id);
        return ApiResponse.success(result(jdbc.queryForList("""
          SELECT DISTINCT s.id,s.service_no,c.customer_name,p.product_name,
                 s.product_type,s.status,s.delivery_stage,s.expire_at,
                 b.binding_role,b.effective_at binding_at
          FROM svc_resource_binding b JOIN svc_instance s ON s.id=b.service_id AND s.deleted=FALSE
          JOIN customer c ON c.id=s.customer_id JOIN prd_product p ON p.id=s.product_id
          WHERE b.resource_type='POOL' AND b.resource_id=? AND b.status='ACTIVE'
          ORDER BY b.effective_at DESC,s.id DESC LIMIT 201
          """,id),"DIRECT_ACTIVE_POOL_BINDING_ONLY"));
    }

    @GetMapping("/supplier-purchases/{id}/proxies")
    public ApiResponse<?> purchaseProxies(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p) {
        require(p,"resource:purchase:read");require(p,"resource:reverse-proxy:read");
        existing("purchase",id);
        return ApiResponse.success(result(jdbc.queryForList("""
          SELECT rp.id,rp.proxy_no,rp.customer_id,c.customer_name,rp.service_id,s.service_no,
                 rp.protocol_code,rp.platform_host,rp.platform_port,rp.status,rp.expire_at,
                 rp.last_checked_at,rp.last_error
          FROM res_reverse_proxy rp JOIN customer c ON c.id=rp.customer_id
          LEFT JOIN svc_instance s ON s.id=rp.service_id AND s.deleted=FALSE
          WHERE rp.purchase_id=? AND rp.deleted=FALSE
          ORDER BY rp.created_at DESC,rp.id DESC LIMIT 201
          """,id),"SUPPLIER_PURCHASE_TO_REVERSE_PROXY"));
    }

    @GetMapping("/supplier-purchases/{id}/services")
    public ApiResponse<?> purchaseServices(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal p) {
        require(p,"resource:purchase:read");require(p,"service:read");
        existing("purchase",id);
        return ApiResponse.success(result(jdbc.queryForList("""
          SELECT s.id,s.service_no,c.id customer_id,c.customer_name,p.product_name,
                 s.product_type,s.status,s.delivery_stage,s.expire_at,
                 rp.id reverse_proxy_id,rp.proxy_no
          FROM svc_instance s JOIN customer c ON c.id=s.customer_id
          JOIN prd_product p ON p.id=s.product_id
          LEFT JOIN res_reverse_proxy rp ON rp.id=s.reverse_proxy_id AND rp.deleted=FALSE
          WHERE s.deleted=FALSE AND (s.supplier_purchase_id=?
             OR EXISTS(SELECT 1 FROM res_reverse_proxy x
               WHERE x.id=s.reverse_proxy_id AND x.purchase_id=? AND x.deleted=FALSE))
          ORDER BY s.created_at DESC,s.id DESC LIMIT 201
          """,id,id),"SERVICE_PURCHASE_REFERENCE_NOT_RUNTIME_PROOF"));
    }
}
