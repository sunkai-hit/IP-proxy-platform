-- V21: 订单驱动开通权限与检索索引，不重写已有历史迁移。
WITH parent AS (
  SELECT id FROM sys_permission WHERE permission_code='order-service:access' AND deleted=FALSE LIMIT 1
)
INSERT INTO sys_permission(permission_code,permission_name,permission_type,parent_id,sort_order,status)
SELECT 'service:provision:manual','管理员特殊手工开通','ACTION',parent.id,74,'ACTIVE'
FROM parent WHERE NOT EXISTS (
  SELECT 1 FROM sys_permission WHERE permission_code='service:provision:manual' AND deleted=FALSE
);
INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code='SUPER_ADMIN' AND r.deleted=FALSE AND p.deleted=FALSE
  AND p.permission_code='service:provision:manual'
ON CONFLICT DO NOTHING;
CREATE INDEX IF NOT EXISTS idx_svc_instance_order_provision_guard
  ON svc_instance(order_id,order_item_id,status) WHERE deleted=FALSE AND order_id IS NOT NULL;
COMMENT ON INDEX idx_svc_instance_order_provision_guard IS '订单驱动开通幂等和异常追踪';
