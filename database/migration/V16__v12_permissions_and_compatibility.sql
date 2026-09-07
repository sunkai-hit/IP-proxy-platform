-- IP代理管理平台 V16 - V1.2 权限、兼容与业务语义调整

-- 新增V1.2资源与外部交付权限。
WITH parent AS (
    SELECT id FROM sys_permission WHERE permission_code='resource:access' AND deleted=FALSE LIMIT 1
), p(code,name,sort_no) AS (
    VALUES
      ('resource:purchase:read','查看外部套餐采购',70),
      ('resource:purchase:write','维护外部套餐采购',71),
      ('resource:purchase:status','变更外部套餐采购状态',72),
      ('resource:reverse-proxy:read','查看反向代理交付',80),
      ('resource:reverse-proxy:write','维护反向代理交付',81),
      ('resource:reverse-proxy:status','变更反向代理状态',82),
      ('resource:reverse-proxy:test','测试反向代理',83),
      ('resource:line:operate','家宽线路运维操作',90),
      ('resource:ros:operate','ROS主备与自动切换操作',91)
)
INSERT INTO sys_permission(permission_code,permission_name,permission_type,parent_id,sort_order,status)
SELECT p.code,p.name,'ACTION',parent.id,p.sort_no,'ACTIVE' FROM p CROSS JOIN parent
WHERE NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.permission_code=p.code AND x.deleted=FALSE);

-- 长效代理专项监控菜单与权限。
INSERT INTO sys_permission(permission_code,permission_name,permission_type,parent_id,route_path,sort_order,status)
SELECT 'long-monitor:access','长效代理监控','MENU',NULL,'/long-monitor',700,'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='long-monitor:access' AND deleted=FALSE);

WITH parent AS (
    SELECT id FROM sys_permission WHERE permission_code='long-monitor:access' AND deleted=FALSE LIMIT 1
), p(code,name,sort_no) AS (
    VALUES
      ('long-monitor:read','查看长效代理监控',10),
      ('long-monitor:write','维护长效代理监控',11),
      ('long-monitor:status','启停长效代理监控',12),
      ('long-monitor:check','立即检测长效代理',13),
      ('long-monitor:bot:read','查看客户机器人',20),
      ('long-monitor:bot:write','维护客户机器人',21),
      ('long-monitor:bot:test','测试客户机器人',22),
      ('long-monitor:alarm:read','查看长效代理告警',30),
      ('long-monitor:alarm:close','关闭长效代理告警',31),
      ('long-monitor:notification:read','查看通知记录',40)
)
INSERT INTO sys_permission(permission_code,permission_name,permission_type,parent_id,sort_order,status)
SELECT p.code,p.name,'ACTION',parent.id,p.sort_no,'ACTIVE' FROM p CROSS JOIN parent
WHERE NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.permission_code=p.code AND x.deleted=FALSE);

-- 新的服务开通方式仍挂在订单与服务域。
WITH parent AS (
    SELECT id FROM sys_permission WHERE permission_code='order-service:access' AND deleted=FALSE LIMIT 1
), p(code,name,sort_no) AS (
    VALUES
      ('service:provision:external','使用外部反向代理开通服务',71),
      ('service:change:read','查看服务变更',72),
      ('service:change:write','发起服务变更',73)
)
INSERT INTO sys_permission(permission_code,permission_name,permission_type,parent_id,sort_order,status)
SELECT p.code,p.name,'ACTION',parent.id,p.sort_no,'ACTIVE' FROM p CROSS JOIN parent
WHERE NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.permission_code=p.code AND x.deleted=FALSE);

-- 超级管理员自动拥有所有新权限。
INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code='SUPER_ADMIN' AND r.deleted=FALSE AND p.deleted=FALSE
  AND (p.permission_code LIKE 'resource:%' OR p.permission_code LIKE 'long-monitor:%' OR p.permission_code LIKE 'service:%')
ON CONFLICT DO NOTHING;

-- 产品资源策略进入“代码内置规则”模式。保留历史表/权限供兼容和数据回溯，业务端不再展示或要求配置。
ALTER TABLE prd_product ADD COLUMN IF NOT EXISTS builtin_policy_code VARCHAR(64);
UPDATE prd_product SET builtin_policy_code=CASE product_type
  WHEN 'SHORT_IP' THEN 'BUILTIN_SHORT_IP'
  WHEN 'LONG_IP' THEN 'BUILTIN_LONG_IP'
  WHEN 'EXCLUSIVE_IP' THEN 'BUILTIN_EXCLUSIVE_IP'
  WHEN 'VPN' THEN 'BUILTIN_VPN'
  WHEN 'TUNNEL' THEN 'BUILTIN_TUNNEL'
  ELSE 'BUILTIN_DEFAULT' END
WHERE builtin_policy_code IS NULL;

COMMENT ON COLUMN prd_product.builtin_policy_code IS 'V1.2产品资源策略由代码层BuiltinResourcePolicy实现，业务端不提供策略配置';
