package com.ipproxy.platform.service.mapper;

import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * M6 对独享资源分配的正式实现。
 * V2 已确定独享资源通过 ip_id / line_id 表达，因此这里严格使用既有模型，
 * 不引入 resource_id 等抽象兼容字段。
 */
@Repository
@Primary
public class OrderServiceMapperPatch extends OrderServiceMapper {
    private final JdbcTemplate jdbc;

    public OrderServiceMapperPatch(JdbcTemplate jdbc) {
        super(jdbc);
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, Object> freeExclusiveIp(Long poolId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT i.id,host(i.ip_address) ip_address " +
            "FROM res_pool_ip pi JOIN res_ip i ON i.id=pi.ip_id " +
            "WHERE pi.pool_id=? AND pi.enabled=TRUE AND i.deleted=FALSE " +
            "AND i.available_status='AVAILABLE' AND i.duplicate_flag=FALSE " +
            "AND NOT EXISTS (SELECT 1 FROM res_exclusive_allocation a " +
            "  WHERE a.resource_type='IP' AND a.ip_id=i.id AND a.deleted=FALSE " +
            "  AND a.status IN ('LOCKED','ALLOCATED')) " +
            "ORDER BY i.quality_score DESC NULLS LAST,i.id LIMIT 1 FOR UPDATE OF i SKIP LOCKED",
            poolId
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Override
    public Long allocateExclusive(Long serviceId, String type, Long resourceId, Long customerId,
                                  OffsetDateTime expire, String snapshot, Long actor) {
        if ("LINE".equals(type)) {
            return jdbc.queryForObject(
                "INSERT INTO res_exclusive_allocation(allocation_no,resource_type,line_id,customer_id,service_id,status,locked_at,effective_at,expire_at,created_by,updated_by) " +
                "VALUES('EA-'||replace(gen_random_uuid()::text,'-',''),'LINE',?,?,?,'ALLOCATED',now(),now(),?,?,?) RETURNING id",
                Long.class, resourceId, customerId, serviceId, expire, actor, actor
            );
        }
        return jdbc.queryForObject(
            "INSERT INTO res_exclusive_allocation(allocation_no,resource_type,ip_id,customer_id,service_id,status,locked_at,effective_at,expire_at,created_by,updated_by) " +
            "VALUES('EA-'||replace(gen_random_uuid()::text,'-',''),'IP',?,?,?,'ALLOCATED',now(),now(),?,?,?) RETURNING id",
            Long.class, resourceId, customerId, serviceId, expire, actor, actor
        );
    }
}
