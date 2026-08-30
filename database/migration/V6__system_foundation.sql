-- IP代理管理平台 V6 - M2 系统基础能力初始化
-- V1-V5 已冻结，本脚本只新增权限目录、超级管理员授权及基础参数。

INSERT INTO sys_role(role_code, role_name, data_scope, description, status)
SELECT 'SUPER_ADMIN','超级管理员','ALL','系统内置超级管理员','ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code='SUPER_ADMIN' AND deleted=FALSE);

INSERT INTO sys_permission(permission_code,permission_name,permission_type,parent_id,route_path,sort_order,status)
SELECT 'system:access','系统管理','MENU',NULL,'/system',900,'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='system:access' AND deleted=FALSE);

WITH p(code,name,ptype,sort_no) AS (
  VALUES
    ('system:user:read','查看用户','ACTION',10),('system:user:write','维护用户','ACTION',11),
    ('system:role:read','查看角色','ACTION',20),('system:role:write','维护角色与授权','ACTION',21),
    ('system:permission:read','查看权限目录','ACTION',30),
    ('system:dict:read','查看字典','ACTION',40),('system:dict:write','维护字典','ACTION',41),
    ('system:param:read','查看系统参数','ACTION',50),('system:param:write','维护系统参数','ACTION',51),
    ('system:login-log:read','查看登录日志','ACTION',60),
    ('system:operation-log:read','查看操作审计','ACTION',70)
)
INSERT INTO sys_permission(permission_code,permission_name,permission_type,parent_id,sort_order,status)
SELECT p.code,p.name,p.ptype,parent.id,p.sort_no,'ACTIVE'
FROM p
JOIN sys_permission parent ON parent.permission_code='system:access' AND parent.deleted=FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.permission_code=p.code AND x.deleted=FALSE);

INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code='SUPER_ADMIN' AND r.deleted=FALSE AND p.deleted=FALSE
  AND (p.permission_code='system:access' OR p.permission_code LIKE 'system:%')
ON CONFLICT DO NOTHING;

INSERT INTO sys_parameter(param_group,param_code,param_name,param_value,value_type,sensitive,description,status)
SELECT 'SYSTEM','system.name','系统名称','IP代理管理平台','STRING',FALSE,'管理端系统显示名称','ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM sys_parameter WHERE param_code='system.name' AND deleted=FALSE);

INSERT INTO sys_parameter(param_group,param_code,param_name,param_value,value_type,sensitive,description,status)
SELECT 'SECURITY','security.login.max-failures','连续登录失败阈值','5','NUMBER',FALSE,'后续登录防护策略使用','ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM sys_parameter WHERE param_code='security.login.max-failures' AND deleted=FALSE);
