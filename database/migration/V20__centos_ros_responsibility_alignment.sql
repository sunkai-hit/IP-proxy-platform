-- IP代理管理平台 V20 - CentOS / ROS 基础设施职责调整
-- 当前阶段 CentOS 与 ROS 固定按 1:1 部署；理论模型可扩展为 N:N，但本版本不开放。
-- CentOS 负责管理动作及 B/C 段聚合；ROS 仅负责家宽线路拨号与拨号结果上报。

-- 在建立当前版本 1:1 约束前先检查历史数据，避免静默覆盖既有关系。
DO $$
BEGIN
    IF EXISTS (
        SELECT centos_id
        FROM res_ros
        WHERE deleted = FALSE AND centos_id IS NOT NULL
        GROUP BY centos_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V20: 存在一个 CentOS 关联多个有效 ROS 的历史数据，请先清理后再执行 1:1 约束';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_res_ros_active_centos_1to1
    ON res_ros(centos_id)
    WHERE deleted = FALSE AND centos_id IS NOT NULL;

-- V13 曾预留 ROS 主备/自动切换字段。V20 起不再作为业务功能使用；字段保留仅用于历史兼容。
UPDATE res_ros
SET ha_role = 'STANDALONE',
    replacement_ros_id = NULL,
    switch_status = 'NORMAL',
    auto_switch = FALSE
WHERE deleted = FALSE;

COMMENT ON COLUMN res_ros.ha_role IS 'V20起废弃业务含义，仅保留历史兼容；当前版本不提供ROS主备功能';
COMMENT ON COLUMN res_ros.replacement_ros_id IS 'V20起废弃业务含义，仅保留历史兼容；当前版本不提供ROS替换关系';
COMMENT ON COLUMN res_ros.switch_status IS 'V20起废弃业务含义，仅保留历史兼容；当前版本不提供ROS切换功能';
COMMENT ON COLUMN res_ros.auto_switch IS 'V20起废弃业务含义，仅保留历史兼容；当前版本不提供ROS自动切换功能';
COMMENT ON COLUMN res_ros.b_prefix_count IS 'V20起不再作为ROS维度业务统计；B段按CentOS下当前拨号IPv4动态聚合';
COMMENT ON COLUMN res_ros.c_prefix_count IS 'V20起不再作为ROS维度业务统计；C段按CentOS下当前拨号IPv4动态聚合';
COMMENT ON COLUMN res_ros.centos_id IS '当前版本CentOS与ROS为1:1；理论模型可扩展为N:N，本版本不开放';

-- 基础资源小时统计增加 CentOS 维度的 B/C 段汇总字段。
ALTER TABLE stat_resource_hourly ADD COLUMN IF NOT EXISTS b_prefix_count INT NOT NULL DEFAULT 0;
ALTER TABLE stat_resource_hourly ADD COLUMN IF NOT EXISTS c_prefix_count INT NOT NULL DEFAULT 0;
COMMENT ON COLUMN stat_resource_hourly.b_prefix_count IS 'CentOS范围内当前IPv4按前2个八位组去重数量的汇总';
COMMENT ON COLUMN stat_resource_hourly.c_prefix_count IS 'CentOS范围内当前IPv4按前3个八位组去重数量的汇总';
