-- IP代理管理平台 V19 - 家宽线路类型同步兼容
-- 上游若明确提供 lineType，则同步到标准业务类型；未提供时保持为空，由运营在详情页设置。

CREATE OR REPLACE FUNCTION trg_res_line_normalize_type()
RETURNS trigger AS $$
DECLARE
    source_type TEXT;
BEGIN
    source_type := upper(COALESCE(NEW.raw_data->>'lineType',''));
    IF source_type IN ('SHARED','SHARE','共享') THEN
        NEW.line_type := 'SHARED';
    ELSIF source_type IN ('LONG','LONG_IP','长效') THEN
        NEW.line_type := 'LONG';
    ELSIF NEW.line_type IS NOT NULL AND NEW.line_type NOT IN ('SHARED','LONG') THEN
        NEW.line_type := NULL;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_res_line_normalize_type ON res_line;
CREATE TRIGGER trg_res_line_normalize_type
BEFORE INSERT OR UPDATE OF raw_data ON res_line
FOR EACH ROW EXECUTE FUNCTION trg_res_line_normalize_type();

UPDATE res_line
SET line_type = CASE
    WHEN upper(COALESCE(raw_data->>'lineType','')) IN ('SHARED','SHARE','共享') THEN 'SHARED'
    WHEN upper(COALESCE(raw_data->>'lineType','')) IN ('LONG','LONG_IP','长效') THEN 'LONG'
    ELSE line_type
END
WHERE deleted=FALSE;
