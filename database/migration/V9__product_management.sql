-- IP代理管理平台 V9 - M5 产品管理
-- V1-V8 已冻结。本脚本仅补充产品域权限与查询索引。

INSERT INTO sys_permission(permission_code,permission_name,permission_type,parent_id,route_path,sort_order,status)
SELECT 'product:access','产品管理','MENU',NULL,'/products',200,'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='product:access' AND deleted=FALSE);

WITH p(code,name,sort_no) AS (
  VALUES
    ('product:read','查看产品',10),
    ('product:write','维护产品',11),
    ('product:status','启停产品',12),
    ('product:config','维护产品专项配置',13),
    ('product:package:read','查看套餐',20),
    ('product:package:write','维护套餐',21),
    ('product:strategy:read','查看资源策略',30),
    ('product:strategy:write','维护资源策略',31),
    ('product:strategy:validate','验证/启停资源策略',32)
)
INSERT INTO sys_permission(permission_code,permission_name,permission_type,parent_id,sort_order,status)
SELECT p.code,p.name,'ACTION',parent.id,p.sort_no,'ACTIVE'
FROM p JOIN sys_permission parent ON parent.permission_code='product:access' AND parent.deleted=FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.permission_code=p.code AND x.deleted=FALSE);

INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code='SUPER_ADMIN' AND r.deleted=FALSE AND p.deleted=FALSE
  AND (p.permission_code='product:access' OR p.permission_code LIKE 'product:%')
ON CONFLICT DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_prd_product_status_updated ON prd_product(status,updated_at DESC) WHERE deleted=FALSE;
CREATE INDEX IF NOT EXISTS idx_prd_package_status_created ON prd_package(status,created_at DESC) WHERE deleted=FALSE;
CREATE INDEX IF NOT EXISTS idx_prd_strategy_status_created ON prd_resource_strategy(status,created_at DESC) WHERE deleted=FALSE;
CREATE INDEX IF NOT EXISTS idx_prd_strategy_pool_enabled ON prd_strategy_pool(strategy_id,enabled,priority);
