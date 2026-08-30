-- IP代理管理平台 V10 - M6 订单与服务管理
-- V1-V9 已冻结。本脚本仅补充订单/服务权限、编号序列与查询索引。

INSERT INTO sys_permission(permission_code,permission_name,permission_type,parent_id,route_path,sort_order,status)
SELECT 'order-service:access','订单与服务','MENU',NULL,'/orders',300,'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='order-service:access' AND deleted=FALSE);

WITH p(code,name,sort_no) AS (
  VALUES
    ('order:read','查看订单',10),('order:write','维护订单',11),('order:status','确认/取消订单',12),('order:provision','订单开通服务',13),
    ('service:read','查看服务',20),('service:provision','直接开通服务',21),('service:status','暂停/恢复/终止服务',22),('service:renew','续费服务',23),
    ('service:change','服务变更',24),('service:credential:read','查看服务凭证',25),('service:credential:reset','重置服务凭证',26),
    ('service:whitelist','维护白名单',27),('service:release','释放/重试资源',28)
)
INSERT INTO sys_permission(permission_code,permission_name,permission_type,parent_id,sort_order,status)
SELECT p.code,p.name,'ACTION',parent.id,p.sort_no,'ACTIVE'
FROM p JOIN sys_permission parent ON parent.permission_code='order-service:access' AND parent.deleted=FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.permission_code=p.code AND x.deleted=FALSE);

INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code='SUPER_ADMIN' AND r.deleted=FALSE AND p.deleted=FALSE
  AND (p.permission_code='order-service:access' OR p.permission_code LIKE 'order:%' OR p.permission_code LIKE 'service:%')
ON CONFLICT DO NOTHING;

CREATE SEQUENCE IF NOT EXISTS seq_biz_order_no START WITH 100001;
CREATE SEQUENCE IF NOT EXISTS seq_service_no START WITH 100001;
CREATE SEQUENCE IF NOT EXISTS seq_service_change_no START WITH 100001;
CREATE SEQUENCE IF NOT EXISTS seq_service_release_no START WITH 100001;

CREATE INDEX IF NOT EXISTS idx_biz_order_owner_time ON biz_order(owner_user_id,created_at DESC) WHERE deleted=FALSE;
CREATE INDEX IF NOT EXISTS idx_biz_order_item_order_product ON biz_order_item(order_id,product_id,package_id);
CREATE INDEX IF NOT EXISTS idx_svc_instance_status_created ON svc_instance(status,created_at DESC) WHERE deleted=FALSE;
CREATE INDEX IF NOT EXISTS idx_svc_instance_customer_created ON svc_instance(customer_id,created_at DESC) WHERE deleted=FALSE;
CREATE INDEX IF NOT EXISTS idx_svc_credential_status_expire ON svc_credential(status,expire_at) WHERE deleted=FALSE;
CREATE INDEX IF NOT EXISTS idx_svc_whitelist_service_status ON svc_whitelist(service_id,status) WHERE deleted=FALSE;
CREATE INDEX IF NOT EXISTS idx_res_exclusive_service_status ON res_exclusive_allocation(service_id,status) WHERE deleted=FALSE;
