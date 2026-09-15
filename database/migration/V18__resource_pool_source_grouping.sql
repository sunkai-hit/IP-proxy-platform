-- IP代理管理平台 V18 - 资源池模型按资源来源属性收敛
-- 资源池只组织自有家宽/线路；线路业务类型仅允许 SHARED / LONG，且单条线路单选。
-- 独享IP、VPN、隧道不作为资源池线路类型，由各自产品/业务模块维护。

ALTER TABLE res_pool ADD COLUMN IF NOT EXISTS province_code VARCHAR(64);
ALTER TABLE res_pool ADD COLUMN IF NOT EXISTS city_code VARCHAR(64);
ALTER TABLE res_pool ADD COLUMN IF NOT EXISTS carrier_code VARCHAR(64);

-- 兼容既有V13数据：若旧region/carrier数组只有单值，尽可能迁移到新的来源属性字段。
UPDATE res_pool
SET province_code = COALESCE(NULLIF(province_code,''), NULLIF(region_codes->>0,'')),
    carrier_code = COALESCE(NULLIF(carrier_code,''), NULLIF(carrier_codes->>0,'')),
    source_scope = 'SELF',
    member_mode = 'LINE',
    pool_type = 'SOURCE'
WHERE deleted = FALSE;

-- 线路类型统一为共享/长效。无法明确映射的历史值保留为空，后续由运营明确设置。
UPDATE res_line
SET line_type = CASE
    WHEN upper(COALESCE(line_type,'')) IN ('SHARED','SHARE','共享') THEN 'SHARED'
    WHEN upper(COALESCE(line_type,'')) IN ('LONG','LONG_IP','长效') THEN 'LONG'
    ELSE NULL
END
WHERE deleted = FALSE;

ALTER TABLE res_line DROP CONSTRAINT IF EXISTS ck_res_line_business_type;
ALTER TABLE res_line ADD CONSTRAINT ck_res_line_business_type
    CHECK (line_type IS NULL OR line_type IN ('SHARED','LONG'));

CREATE INDEX IF NOT EXISTS idx_res_pool_source_attr
    ON res_pool(province_code,city_code,carrier_code,status)
    WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_res_line_pool_attr
    ON res_line(province_code,city_code,carrier_code,line_type,online_status)
    WHERE deleted = FALSE;

COMMENT ON COLUMN res_line.line_type IS '资源池线路业务类型，单选：SHARED=共享，LONG=长效；其他产品类型不进入资源池';
COMMENT ON COLUMN res_pool.province_code IS '资源池来源省份；用于约束成员线路来源属性';
COMMENT ON COLUMN res_pool.city_code IS '资源池来源城市，可为空表示省级池';
COMMENT ON COLUMN res_pool.carrier_code IS '资源池来源运营商；用于约束成员线路来源属性';
COMMENT ON COLUMN res_pool.pool_type IS 'V18起废弃业务含义，仅保留历史兼容；资源池不再按短效/长效/独享/VPN/隧道划分';
COMMENT ON TABLE res_pool_line IS '资源池与家宽/线路N:M成员关系；同一线路可加入多个符合来源属性的资源池';
