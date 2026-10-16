package com.ipproxy.platform.openapi;

import com.ipproxy.platform.common.api.ApiResponse;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.*;

/**
 * Eight source-backed log views complement IP extraction, usage and API logs.
 * Some sources are only management audit or monitoring samples; do not claim
 * complete network/dial logs or real adapter success without product evidence.
 * Never expose raw_data, before/after JSON, upstream secrets or free-text stack traces.
 */
@RestController
@RequestMapping("/api/admin/v1/runtime-logs")
public class V12ExtendedLogController {
    private final JdbcTemplate jdbc;
    public V12ExtendedLogController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private void require(UserPrincipal actor,String category) {
        if(actor==null)throw new BusinessException("AUTH_FORBIDDEN","需要登录");
        boolean root=actor.roles().contains("SUPER_ADMIN");
        String permission="operation".equals(category)?"system:operation-log:read":"runtime-log:access";
        if(!root&&!actor.permissions().contains(permission))
            throw new BusinessException("AUTH_FORBIDDEN","没有该日志分类的查看权限");
    }

    @GetMapping("/v12/{category}")
    public ApiResponse<?> list(@PathVariable String category,
           @RequestParam(defaultValue="1") int page,
           @RequestParam(defaultValue="20") int size,
           @RequestParam(defaultValue="") String keyword,
           @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
           @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
           @AuthenticationPrincipal UserPrincipal actor) {
        String sql=sqlFor(category);
        require(actor,category);
        int pg=Math.max(1,page),sz=Math.max(1,Math.min(100,size));
        OffsetDateTime end=to==null?OffsetDateTime.now(ZoneOffset.UTC):to.withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime start=from==null?end.minusDays(7):from.withOffsetSameInstant(ZoneOffset.UTC);
        if(!start.isBefore(end)||Duration.between(start,end).compareTo(Duration.ofDays(31))>0)
            throw new BusinessException("LOG_RANGE_INVALID","日志查询区间必须在31天以内且起始早于结束");
        String k="%"+(keyword==null?"":keyword.trim())+"%";
        Object[] args=new Object[]{start,end,k,k};
        Long count=jdbc.queryForObject("SELECT count(*) FROM ("+sql+") q",Long.class,args);
        List<Map<String,Object>> items=jdbc.queryForList(
            sql+" ORDER BY \"eventTime\" DESC LIMIT ? OFFSET ?",
            start,end,k,k,sz,(pg-1)*sz);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("page",pg);result.put("size",sz);result.put("total",count==null?0:count);
        result.put("items",items);
        result.put("sourceScope",sourceScope(category));
        result.put("verifiedNetworkDelivery",false);
        result.put("from",start);result.put("to",end);
        return ApiResponse.success(result);
    }
    private String sourceScope(String category) {
        return switch(category){
            case "centos","ros"->"PARTIAL_MONITOR_SAMPLES_NOT_COMPLETE_RUNTIME_LOG";
            case "dial"->"ONLY_OBSERVED_DIAL_IP_EVENTS_NOT_DIAL_ERROR_AUDIT";
            case "ip-change"->"HISTORIC_OBSERVED_IP_DIFF_NOT_CONTRACTED_EXIT";
            case "remote","domain","external"->"MANAGEMENT_LOG_NOT_PROOF_OF_REMOTE_EXECUTION";
            case "operation"->"REDACTED_OPERATION_AUDIT_NO_RAW_JSON";
            default->throw new BusinessException("LOG_CATEGORY_UNSUPPORTED","不支持的日志分类");
        };
    }
    private String sqlFor(String category) {
        return switch(category){
            case "centos"->"""
              SELECT m.collected_at "eventTime",'CENTOS' "objectType",c.resource_code "objectCode",m.metric_code "eventType",NULL::text "result",
                COALESCE(m.metric_value::text,'无数值，仅有采集事件') "summary",NULL::text "operationKey",
                'MONITOR_METRIC_ONLY' "source",('/resources/centos/'||c.id) "relatedPath"
                FROM mon_metric_sample m JOIN res_centos c ON c.id=m.object_id AND c.deleted=FALSE
                WHERE m.object_type='CENTOS' AND m.collected_at>=? AND m.collected_at<?
                  AND (c.resource_code ILIKE ? OR m.metric_code ILIKE ?)
              """;
            case "ros"->"""
              SELECT m.collected_at "eventTime",'ROS' "objectType",r.resource_code "objectCode",m.metric_code "eventType",NULL::text "result",
                COALESCE(m.metric_value::text,'无数值，仅有采集事件') "summary",NULL::text "operationKey",
                'MONITOR_METRIC_ONLY' "source",('/resources/ros/'||r.id) "relatedPath"
                FROM mon_metric_sample m JOIN res_ros r ON r.id=m.object_id AND r.deleted=FALSE
                WHERE m.object_type='ROS' AND m.collected_at>=? AND m.collected_at<?
                  AND (r.resource_code ILIKE ? OR m.metric_code ILIKE ?)
              """;
            case "dial"->"""
              SELECT h.last_seen_at "eventTime",'LINE' "objectType",l.resource_code "objectCode",
                h.change_type "eventType",NULL::text "result",('观测IPv4 '||host(h.ip_address)) "summary",
                h.source_record_id "operationKey",'DIAL_IP_OBSERVATION_ONLY' "source",
                ('/resources/lines/'||l.id) "relatedPath"
                FROM res_line_ip_history h JOIN res_line l ON l.id=h.line_id AND l.deleted=FALSE
                WHERE h.change_type='DIAL' AND h.last_seen_at>=? AND h.last_seen_at<?
                  AND (l.resource_code ILIKE ? OR host(h.ip_address) ILIKE ?)
              """;
            case "ip-change"->"""
              SELECT h.last_seen_at "eventTime",'LINE' "objectType",l.resource_code "objectCode",
                h.change_type "eventType",NULL::text "result",
                ('前一观测 '||host(h.previous_ip)||' → 新观测 '||host(h.ip_address)) "summary",
                h.source_record_id "operationKey",'OBSERVED_IP_CHANGE_NOT_DELIVERY' "source",
                ('/resources/lines/'||l.id) "relatedPath"
                FROM (SELECT id,line_id,ip_address,change_type,source_record_id,last_seen_at,
                  lag(ip_address) OVER (PARTITION BY line_id ORDER BY last_seen_at,id) previous_ip
                  FROM res_line_ip_history) h JOIN res_line l ON l.id=h.line_id AND l.deleted=FALSE
                WHERE h.previous_ip IS NOT NULL AND h.previous_ip IS DISTINCT FROM h.ip_address
                  AND h.last_seen_at>=? AND h.last_seen_at<?
                  AND (l.resource_code ILIKE ? OR host(h.ip_address) ILIKE ?)
              """;
            case "remote"->"""
              SELECT o.created_at "eventTime",'LINE' "objectType",l.resource_code "objectCode",
                o.operation_type "eventType",o.result_status "result",
                ('执行来源 '||o.operation_source) "summary",NULL::text "operationKey",
                'MANAGEMENT_ACTION_NOT_VERIFIED_NETWORK' "source",
                ('/resources/lines/'||l.id) "relatedPath"
                FROM res_line_operation_log o JOIN res_line l ON l.id=o.line_id AND l.deleted=FALSE
                WHERE o.created_at>=? AND o.created_at<?
                  AND (l.resource_code ILIKE ? OR o.operation_type ILIKE ?)
              """;
            case "domain"->"""
              SELECT o.created_at "eventTime",'DOMAIN' "objectType",d.domain_name "objectCode",
                o.action_type "eventType",o.status "result",
                ('解析变更 '||COALESCE(host(o.before_ip),'未知')||' → '||COALESCE(host(o.after_ip),'未知')) "summary",
                NULL::text "operationKey",'MANAGEMENT_DNS_STATUS_UNVERIFIED' "source",
                ('/domains/'||d.id) "relatedPath"
                FROM res_domain_sync_log o JOIN res_domain d ON d.id=o.domain_id
                WHERE o.created_at>=? AND o.created_at<?
                  AND (d.domain_name ILIKE ? OR o.action_type ILIKE ?)
              """;
            case "external"->"""
              SELECT o.created_at "eventTime",'REVERSE_PROXY' "objectType",rp.proxy_no "objectCode",
                o.operation_type "eventType",o.status "result",
                ('平台配置操作 '||o.operation_type) "summary",NULL::text "operationKey",
                'PROXY_CONFIGURATION_AUDIT_UNVERIFIED' "source",
                ('/reverse-proxies/'||rp.id) "relatedPath"
                FROM res_reverse_proxy_operation_log o JOIN res_reverse_proxy rp ON rp.id=o.reverse_proxy_id
                WHERE o.created_at>=? AND o.created_at<?
                  AND (rp.proxy_no ILIKE ? OR o.operation_type ILIKE ?)
              """;
            case "operation"->"""
              SELECT o.occurred_at "eventTime",o.object_type "objectType",
                COALESCE(o.object_id,'') "objectCode",o.operation "eventType",o.result "result",
                ('模块 '||o.module_code||' / 执行人 '||COALESCE(o.operator_name,'系统')) "summary",
                o.request_id "operationKey",'REDACTED_OPERATION_AUDIT' "source",
                CASE WHEN o.object_id ~ '^[0-9]+$' AND o.object_type='SERVICE'
                  THEN '/services/'||o.object_id
                  WHEN o.object_id ~ '^[0-9]+$' AND o.object_type IN ('ORDER','BIZ_ORDER')
                  THEN '/orders/'||o.object_id
                  ELSE NULL END "relatedPath"
                FROM sys_operation_log o
                WHERE o.occurred_at>=? AND o.occurred_at<?
                  AND (COALESCE(o.object_id,'') ILIKE ? OR o.module_code ILIKE ?)
              """;
            default->throw new BusinessException("LOG_CATEGORY_UNSUPPORTED","未知日志类型");
        };
    }
}
