package com.ipproxy.platform.runtime;

import com.ipproxy.platform.common.api.ApiResponse;
import com.ipproxy.platform.common.exception.BusinessException;
import com.ipproxy.platform.security.UserPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * V1.2 source-grouped resource-pool statistics. Uses LINE membership only,
 * not the legacy IP-pool catalog. Current snapshot is NOT hourly history or
 * proof of real product data-plane delivery.
 */
@RestController
@RequestMapping("/api/admin/v1/statistics/v12")
public class V12StatisticsController {
    private final JdbcTemplate jdbc;
    public V12StatisticsController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private void require(UserPrincipal actor) {
        if(actor==null||(!actor.roles().contains("SUPER_ADMIN")
            && !actor.permissions().contains("statistics:read")))
            throw new BusinessException("AUTH_FORBIDDEN","当前账号无统计查看权限");
    }
    @GetMapping("/pools/current")
    public ApiResponse<?> pools(@AuthenticationPrincipal UserPrincipal actor){
        require(actor);
        List<Map<String,Object>> rows=jdbc.queryForList("""
          SELECT p.id "poolId",p.pool_code "poolCode",p.pool_name "poolName",
                 p.province_code "provinceCode",p.city_code "cityCode",
                 p.carrier_code "carrierCode",p.status,
                 count(DISTINCT l.id) "lineCount",
                 count(DISTINCT l.id) FILTER(WHERE l.line_type='SHARED') "sharedLines",
                 count(DISTINCT l.id) FILTER(WHERE l.line_type='LONG') "longLines",
                 count(DISTINCT l.id) FILTER(WHERE l.online_status='ONLINE') "onlineLines",
                 count(DISTINCT l.id) FILTER(WHERE l.online_status IN ('OFFLINE','ABNORMAL')) "abnormalLines",
                 count(DISTINCT l.current_public_ip)
                   FILTER(WHERE l.current_public_ip IS NOT NULL) "currentDistinctIpv4",
                 count(DISTINCT l.id)
                   FILTER(WHERE l.last_sync_at IS NULL
                     OR l.last_sync_at<now()-interval '5 minutes') "staleLines",
                 max(l.last_sync_at) "lastLineSyncAt",
                 now() "snapshotAt"
          FROM res_pool p
          LEFT JOIN res_pool_line pl ON pl.pool_id=p.id AND pl.enabled=TRUE
          LEFT JOIN res_line l ON l.id=pl.line_id AND l.deleted=FALSE
          WHERE p.deleted=FALSE
          GROUP BY p.id
          ORDER BY p.pool_code,p.id LIMIT 200
        """);
        for(Map<String,Object> row:rows){
            row.put("source","LINE_MEMBERSHIP_MANAGEMENT_SNAPSHOT");
            row.put("realNetworkVerified",false);
        }
        return ApiResponse.success(rows);
    }
}
