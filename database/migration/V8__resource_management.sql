-- IP代理管理平台 V8 - M4 资源管理
-- V1-V7 已冻结。本脚本仅补充资源模块权限与运行查询索引。

INSERT INTO sys_permission(permission_code,permission_name,permission_type,parent_id,route_path,sort_order,status)
SELECT 'resource:access','资源管理','MENU',NULL,'/resources',200,'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='resource:access' AND deleted=FALSE);

WITH p(code,name,ptype,sort_no) AS (
  VALUES
    ('resource:read','查看基础资源','ACTION',10),
    ('resource:sync','同步基础资源','ACTION',11),
    ('resource:pool:read','查看资源池','ACTION',20),
    ('resource:pool:write','维护资源池','ACTION',21),
    ('resource:pool:status','变更资源池状态','ACTION',22),
    ('resource:exclusive:read','查看独享资源','ACTION',30),
    ('resource:exclusive:release','释放独享资源','ACTION',31),
    ('resource:domain:read','查看域名配置','ACTION',40),
    ('resource:domain:write','维护域名配置','ACTION',41),
    ('resource:domain:refresh','刷新域名目标IP','ACTION',42),
    ('resource:supplier:read','查看外部供应商','ACTION',50),
    ('resource:supplier:write','维护外部供应商','ACTION',51),
    ('resource:supplier:status','变更供应商状态','ACTION',52),
    ('resource:supplier:test','测试供应商配置','ACTION',53),
    ('resource:sync-task:read','查看同步任务','ACTION',60),
    ('resource:sync-task:retry','重试同步任务','ACTION',61)
)
INSERT INTO sys_permission(permission_code,permission_name,permission_type,parent_id,sort_order,status)
SELECT p.code,p.name,p.ptype,parent.id,p.sort_no,'ACTIVE'
FROM p
JOIN sys_permission parent ON parent.permission_code='resource:access' AND parent.deleted=FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.permission_code=p.code AND x.deleted=FALSE);

INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code='SUPER_ADMIN' AND r.deleted=FALSE AND p.deleted=FALSE
  AND (p.permission_code='resource:access' OR p.permission_code LIKE 'resource:%')
ON CONFLICT DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_res_centos_sync_time ON res_centos(last_sync_at DESC) WHERE deleted=FALSE;
CREATE INDEX IF NOT EXISTS idx_res_ros_sync_time ON res_ros(last_sync_at DESC) WHERE deleted=FALSE;
CREATE INDEX IF NOT EXISTS idx_res_line_sync_time ON res_line(last_sync_at DESC) WHERE deleted=FALSE;
CREATE INDEX IF NOT EXISTS idx_res_ip_source_identity ON res_ip(source_type,source_system,source_id) WHERE deleted=FALSE;
CREATE INDEX IF NOT EXISTS idx_res_pool_ip_enabled_ip ON res_pool_ip(ip_id,enabled);
CREATE INDEX IF NOT EXISTS idx_res_domain_line ON res_domain(line_id) WHERE deleted=FALSE;
CREATE INDEX IF NOT EXISTS idx_res_supplier_code_status ON res_supplier(supplier_code,status) WHERE deleted=FALSE;
CREATE INDEX IF NOT EXISTS idx_res_sync_task_source_time ON res_sync_task(source_type,created_at DESC);
