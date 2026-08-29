-- IP代理管理平台 V5 - 初始字典

INSERT INTO sys_dict_type(dict_code, dict_name, description, editable) VALUES
('customer_type','客户类型','个人/企业等客户分类',TRUE),
('auth_type','认证类型','个人认证/企业认证',TRUE),
('account_type','客户账号类型','客户登录/API账号分类',TRUE),
('access_mode','接入方式','API提取/直连/VPN/隧道等',TRUE),
('proxy_protocol','代理协议','HTTP/HTTPS/SOCKS等',TRUE),
('auth_mode','认证方式','Token/白名单/账号密码/私钥等',TRUE),
('carrier','运营商','联通/移动/电信等',TRUE),
('resource_source','资源来源','自有/外部等',TRUE),
('notification_channel','通知方式','平台/钉钉/Webhook',TRUE),
('region','地区','建议后续迁移到层级区域主数据，本字典仅保留原型兼容',FALSE),
('vpn_protocol','VPN协议','实际协议待部署方案确认',TRUE)
ON CONFLICT DO NOTHING;

WITH t AS (SELECT id, dict_code FROM sys_dict_type WHERE deleted=FALSE)
INSERT INTO sys_dict_item(dict_type_id,item_code,item_name,item_value,sort_order,is_default)
SELECT t.id, v.item_code, v.item_name, v.item_value, v.sort_order, v.is_default
FROM t JOIN (VALUES
('customer_type','PERSONAL','个人','PERSONAL',10,FALSE),
('customer_type','ENTERPRISE','企业','ENTERPRISE',20,TRUE),
('auth_type','PERSONAL','个人认证','PERSONAL',10,FALSE),
('auth_type','ENTERPRISE','企业认证','ENTERPRISE',20,TRUE),
('account_type','DEFAULT','普通账号','DEFAULT',10,TRUE),
('account_type','API','API账号','API',20,FALSE),
('access_mode','API_EXTRACT','API提取','API_EXTRACT',10,FALSE),
('access_mode','DIRECT','直连','DIRECT',20,FALSE),
('access_mode','VPN','VPN连接','VPN',30,FALSE),
('access_mode','TUNNEL','固定隧道入口','TUNNEL',40,FALSE),
('proxy_protocol','HTTP','HTTP','HTTP',10,TRUE),
('proxy_protocol','HTTPS','HTTPS','HTTPS',20,FALSE),
('proxy_protocol','SOCKS5','SOCKS5','SOCKS5',30,FALSE),
('auth_mode','TOKEN','Token','TOKEN',10,FALSE),
('auth_mode','WHITELIST','IP白名单','WHITELIST',20,FALSE),
('auth_mode','USERNAME_PASSWORD','账号密码','USERNAME_PASSWORD',30,FALSE),
('auth_mode','PRIVATE_KEY','私钥','PRIVATE_KEY',40,FALSE),
('auth_mode','CERTIFICATE','证书','CERTIFICATE',50,FALSE),
('carrier','UNICOM','联通','UNICOM',10,FALSE),
('carrier','MOBILE','移动','MOBILE',20,FALSE),
('carrier','TELECOM','电信','TELECOM',30,FALSE),
('carrier','OTHER','其他','OTHER',99,FALSE),
('resource_source','SELF','自有资源','SELF',10,TRUE),
('resource_source','EXTERNAL','外部资源','EXTERNAL',20,FALSE),
('notification_channel','PLATFORM','平台通知','PLATFORM',10,TRUE),
('notification_channel','DINGTALK','钉钉机器人','DINGTALK',20,FALSE),
('notification_channel','WEBHOOK','Webhook','WEBHOOK',30,FALSE),
('vpn_protocol','TBD','待确认','TBD',99,TRUE)
) AS v(dict_code,item_code,item_name,item_value,sort_order,is_default)
ON t.dict_code=v.dict_code
ON CONFLICT DO NOTHING;
