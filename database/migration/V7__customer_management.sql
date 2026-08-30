-- IP代理管理平台 V7 - M3 客户管理
-- V1-V6 已冻结。本脚本仅补充客户模块权限、编号序列与查询索引。

CREATE SEQUENCE IF NOT EXISTS customer_code_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS customer_auth_no_seq START WITH 1 INCREMENT BY 1;

INSERT INTO sys_permission(permission_code,permission_name,permission_type,parent_id,route_path,sort_order,status)
SELECT 'customer:access','客户管理','MENU',NULL,'/customers',100,'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='customer:access' AND deleted=FALSE);

WITH p(code,name,ptype,sort_no) AS (
  VALUES
    ('customer:read','查看客户','ACTION',10),
    ('customer:write','维护客户档案','ACTION',11),
    ('customer:status','变更客户状态','ACTION',12),
    ('customer:auth:read','查看客户认证','ACTION',20),
    ('customer:auth:review','审核客户认证','ACTION',21),
    ('customer:account:read','查看客户账号','ACTION',30),
    ('customer:account:write','维护客户账号','ACTION',31),
    ('customer:credential:read','查看服务认证信息','ACTION',40)
)
INSERT INTO sys_permission(permission_code,permission_name,permission_type,parent_id,sort_order,status)
SELECT p.code,p.name,p.ptype,parent.id,p.sort_no,'ACTIVE'
FROM p
JOIN sys_permission parent ON parent.permission_code='customer:access' AND parent.deleted=FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.permission_code=p.code AND x.deleted=FALSE);

INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code='SUPER_ADMIN' AND r.deleted=FALSE AND p.deleted=FALSE
  AND (p.permission_code='customer:access' OR p.permission_code LIKE 'customer:%')
ON CONFLICT DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_customer_created_at ON customer(created_at DESC) WHERE deleted=FALSE;
CREATE INDEX IF NOT EXISTS idx_customer_type_status ON customer(customer_type_code,status) WHERE deleted=FALSE;
CREATE INDEX IF NOT EXISTS idx_customer_auth_customer_status_time ON customer_auth(customer_id,status,submitted_at DESC) WHERE deleted=FALSE;
CREATE INDEX IF NOT EXISTS idx_customer_account_customer_status2 ON customer_account(customer_id,status,created_at DESC) WHERE deleted=FALSE;
