# IP代理管理平台

当前已完成 **M1 工程骨架 + M2 系统基础能力 + M3 客户管理 + M4 资源管理 + M5 产品管理 + M6 订单与服务管理 + M7 Open API 与运行日志体系 + M8 监控、告警与数据统计**。

仓库同时保留静态原型、正式前后端工程、PostgreSQL Flyway 迁移以及各阶段实际 API 契约。

## 目录

```text
.
├── prototype/              # 已确认的静态原型，作为产品/视觉基准
├── frontend/               # Vue 3 + TypeScript + Vite + Element Plus + ECharts
├── backend/                # Java 21 + Spring Boot 4.1.1
├── database/migration/     # PostgreSQL Flyway V1-V12
├── docs/                   # ER、OpenAPI、架构设计
├── docker-compose.yml      # PostgreSQL + Redis + Backend + Frontend
└── .github/workflows/      # CI 构建与 M3-M8 真实业务生命周期验证
```

## 已实现里程碑

### M1 工程骨架
- Vue 3 / TypeScript / Vite / Element Plus / Pinia / Vue Router / Axios
- Spring Boot / Spring MVC / Security / MyBatis
- PostgreSQL + Redis
- Flyway、统一 API 响应、全局异常、Request ID
- JWT 登录、Swagger UI、Actuator

### M2 系统基础能力
- 用户、角色、权限、字典、参数
- JWT 角色与权限载荷
- 前后端路由/按钮权限控制
- 登录日志、操作审计
- SUPER_ADMIN 与当前管理员防误操作保护

### M3 客户管理
- 客户列表、筛选、新增、编辑、详情
- 客户状态、个人/企业认证、客户账号生命周期
- 服务引用、使用概览、服务认证信息脱敏

> 主线工程仍未合并“运营端反馈分支”中的余额、折扣、客户来源、专属注册链接、充值、退款、财务等功能。

### M4 资源管理
- CentOS、ROS、家宽/线路、IP资源同步与查看
- 资源层级：`CentOS → ROS → 家宽/线路 → IP`
- `ResourceAdapter` 隔离真实运维/供应商协议
- IP资源池、独享资源、域名配置、外部供应商、资源同步任务
- 自有IP与外部IP统一进入 `res_ip`，再组织到统一资源池
- 未确认的资源控制能力不伪造为新增/修改/删除/远程控制

### M5 产品管理
- 五类产品：短效IP、长效IP、独享IP、VPN、隧道
- 产品通用配置 + 五类专项配置
- 套餐管理
- 产品资源策略直接引用统一资源池
- 策略验证通过后才能启用产品

### M6 订单与服务管理
- 订单与订单项
- 服务实例关联客户、产品、套餐、资源策略和资源绑定
- 开通时固化产品/套餐/策略快照
- 共享服务绑定资源池；独享服务额外锁定具体IP
- 服务凭证只在开通/重置时返回明文一次
- 白名单、暂停、恢复、续费、终止、资源释放
- `ServiceProvisionAdapter` 隔离真实 Proxy / VPN / Tunnel 数据面

### M7 Open API + 运行日志体系

客户侧真实调用链：

```text
客户账号用户名 + 密码
        ↓
POST /api/open/v1/auth/token
        ↓
      Open Token
        ↓
服务实例有效性 / 套餐额度 / 提取频率 / 去重规则
        ↓
服务绑定资源池
        ↓
      IP提取
        ↓
IP提取日志 + API日志
```

实际接口：
- `POST /api/open/v1/auth/token`
- `GET /api/open/v1/proxy/extract`
- `GET /api/open/v1/whitelist`
- `POST /api/open/v1/whitelist`
- `DELETE /api/open/v1/whitelist/{ip}`

Token 使用 `oa.<grantId>.<tokenVersion>.<HMAC-signature>`，不明文落库。服务暂停、终止或到期后，Token 即使签名有效也无法继续调用。

运行日志：
- `log_ip_extract`：IP提取真实写入
- `log_api`：Open API 调用真实写入，失败请求使用独立事务保留审计痕迹
- `log_usage`：只用于真实代理流量，不把“提取IP”伪造成“使用IP”

## M8 监控 + 告警 + 数据统计

M8 把 M4 的资源、M6 的服务实例和 M7 的真实调用日志组织成运行闭环：

```text
资源 / 服务 / Open API运行数据
          ↓
       监控采集
          ↓
 mon_object_status
 mon_metric_sample
          ↓
       告警规则
          ↓
   告警触发 / 合并
          ↓
确认 → 处理中 → 自动恢复 / 人工关闭
          ↓
 PLATFORM / 钉钉通知记录
          ↓
      小时统计聚合
          ↓
资源 / IP / 客户 / 产品服务 / 外部资源统计
```

### 1. 监控中心

后台新增正式“监控中心”，前端按运行对象提供：
1. CentOS监控
2. ROS监控
3. 线路监控
4. IP资源监控
5. 外部资源监控
6. 服务运行监控

后端统一监控对象还包括 `POOL`，用于告警和统计计算。

采集指标来自两类来源：
- **平台已经真实掌握的数据**：同步后的在线状态、机器负载字段、线路延迟/可用率、IP质量、资源池IP数量、服务状态、M7 API请求/失败量等；
- **需要主动探测的数据**：通过 `MonitorProbeAdapter` 扩展。

正式环境默认使用 `NoopMonitorProbeAdapter`，没有真实运维协议时不会伪造主动探测结果；`m8-mock` 只用于 CI / 本地生命周期测试。

监控数据写入：
- `mon_object_status`：对象最新状态和最新指标快照
- `mon_metric_sample`：时序指标样本
- `mon_collect_run`：每轮采集的对象数、样本数、告警触发数、恢复数和执行结果

#### 数据过期原则

监控页面不把旧数据继续显示成绿色健康状态：

```text
最新采集时间超过 staleSeconds
          ↓
     display_status = UNKNOWN
```

默认过期阈值为 300 秒，可通过 `MONITOR_STALE_SECONDS` 调整。

注意：`SERVICE` 和 `POOL` 是平台自身维护的业务状态，不使用“上游同步时间”判定过期；但它们的监控采集结果本身仍受 `last_collected_at` 的页面过期规则约束。

### 2. 告警规则

支持监控对象：CENTOS、ROS、LINE、IP、POOL、SUPPLIER、SERVICE。

支持运算符：EQ、NE、GT、GTE、LT、LTE、IN、NOT_IN。

规则可配置：
- 指标编码
- 阈值
- 连续命中次数
- 持续时间
- 告警级别 `INFO / MINOR / MAJOR / CRITICAL`
- 通知通道
- 是否发送恢复通知
- 重复通知间隔
- 启用/停用

### 3. 告警去重与生命周期

同一 `rule_id + object_type + object_id + metric_code` 在告警尚未恢复时只允许存在一条活动告警。

重复命中不会不断创建新告警，而是更新 `last_triggered_at`、累加 `occurrence_count`，并按 `repeat_interval_seconds` 决定是否重复通知。

告警状态：

```text
OPEN
  ↓ 确认
ACKNOWLEDGED
  ↓ 开始处理
PROCESSING
  ↓ 指标恢复
RECOVERED
  ↓ 可人工归档
CLOSED
```

所有处理动作写入 `alarm_process_record`。

### 4. 自动恢复

每轮监控采集都会重新计算活动告警。指标继续违反规则时更新同一告警；指标恢复正常时自动转为 `RECOVERED`，记录 `RECOVER` 动作，并按规则发送恢复通知。

M8 CI 已实际验证“触发 → 确认 → 处理中 → 重复采集去重 → 修改阈值 → 自动恢复”。

### 5. 告警通知

当前通知通道模型：
- `PLATFORM`
- `DINGTALK`
- `WEBHOOK`

每次发送都会落 `alarm_notification`，记录告警、通道、目标、消息快照、发送结果、重试次数、发送时间和错误信息。

`PLATFORM` 当前作为站内通知记录直接成功。

`DINGTALK` 正式环境通过：

```bash
DINGTALK_WEBHOOK_URL=https://oapi.dingtalk.com/robot/send?access_token=...
```

配置机器人 Webhook 后发送；未配置时明确记录 FAILURE，不伪造成功。

`m8-mock` 下通知发送器只用于 CI，目标会标记为 `M8_MOCK_*`。

`WEBHOOK` 已保留通道模型和记录能力，但通用外部 Webhook 的认证、签名和目标配置尚未确认，因此正式发送器暂不伪造实现。

### 6. 数据统计

统计最小粒度固定为 **1小时**。

当前五类统计：
1. 基础资源统计
2. IP资源统计
3. 客户使用统计
4. 产品与服务统计
5. 外部资源统计

写入：
- `stat_resource_hourly`
- `stat_ip_hourly`
- `stat_customer_usage_hourly`
- `stat_product_service_hourly`
- `stat_supplier_hourly`
- `stat_calculation_run`

支持手工重算指定小时，也支持定时任务自动计算上一小时数据。默认统计任务在 UTC 每小时第5分钟执行，可通过 `STATISTICS_CRON` 调整。

#### 统计口径边界

M7 与 M8 对“提取”和“使用”继续严格区分：

```text
IP提取日志 log_ip_extract
    → 提取请求IP数
    → 实际返回IP数

真实代理流量 log_usage
    → 代理请求数
    → 成功/失败请求数
    → 实际出站IP去重
    → 上行/下行流量
```

如果真实 Proxy/VPN/Tunnel/ROS 数据面尚未回传 `log_usage`，相关流量指标保持 0，不使用 IP 提取量替代。

### 7. 首页工作台

首页已升级为实际运行概览：
- 有效客户
- 有效服务
- 3天内到期服务
- 今日返回IP数
- 当前可用去重IP数
- 当前活动告警
- 在线 CentOS / ROS
- 在线线路

## M8 权限

```text
monitor:access
monitor:read
monitor:collect

alarm:access
alarm:read
alarm:rule:read
alarm:rule:write
alarm:handle
alarm:notification:read

statistics:access
statistics:read
statistics:recalculate
```

SUPER_ADMIN 默认拥有以上权限。

## 当前完整业务运行链

```text
客户
 ↓
客户账号
 ↓
订单 → 产品 / 套餐 / 产品资源策略
 ↓
服务实例
 ↓
Open Token
 ↓
IP提取
 ↓
统一资源池 → IP
 ↓
IP提取日志 + API日志
 ↓
服务运行指标
        ↘
基础资源状态 → 监控采集 → 告警判定 → 告警处理/通知
                         ↓
                     小时统计
                         ↓
              首页 / 统计分析 / 运营排障
```

## 管理面与数据面边界

Spring Boot 管理平台负责客户、资源、产品、订单服务、Open API、运行日志、监控、告警和统计，但**不承担真实代理、VPN、隧道流量转发**。

仍需真实外部系统或数据面协议的能力包括：
- Proxy / VPN / Tunnel Gateway 实际流量处理
- ROS/线路主动探测和远程控制
- 真实 `log_usage` 流量日志回传
- 更完整的通用 Webhook 通知配置

## 数据库迁移

```text
V1   系统 + 客户基础
V2   资源基础模型
V3   产品 + 订单 + 服务基础模型
V4   监控 + 日志 + 告警 + 统计基础模型
V5   基础字典
V6   系统基础能力
V7   客户管理
V8   资源管理
V9   产品管理
V10  订单与服务管理
V11  Open API + 运行日志体系
V12  监控 + 告警 + 数据统计
```

M8 验收通过后，**V1-V12 作为已执行历史迁移冻结**。后续数据库结构变化新增 `V13__...sql`，不回改 V1-V12。

## 一键启动

```bash
cp .env.example .env
docker compose up --build
```

访问：
- 前端：http://localhost:8081
- 后端：http://localhost:8080
- Swagger：http://localhost:8080/swagger-ui.html
- Health：http://localhost:8080/actuator/health

开发环境默认后台账号：`admin / admin123`。

> 默认账号仅用于本地开发验证。非开发环境必须修改 `M1_ADMIN_PASSWORD`、`JWT_SECRET`，并单独配置生产资源、数据面、供应商和告警机器人密钥。

## 分开开发

```bash
docker compose up -d postgres redis
cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev
```

## 设计与接口基线

- 数据库：`docs/database/` + `database/migration/`
- Admin API 总体基线：`docs/api/openapi-admin-v1.json`
- M3 客户管理：`docs/api/openapi-customer-m3.yaml`
- M4 资源管理：`docs/api/openapi-resource-m4.yaml`
- M5 产品管理：`docs/api/openapi-product-m5.yaml`
- M6 订单与服务：`docs/api/openapi-order-service-m6.yaml`
- M7 客户 Open API：`docs/api/openapi-open-v1.yaml`
- M7 运行日志 Admin API：`docs/api/openapi-runtime-log-m7.yaml`
- M8 监控/告警/统计 Admin API：`docs/api/openapi-runtime-m8.yaml`
- 静态原型：`prototype/`

## M8 CI 验收范围

M8 CI 在全新 PostgreSQL + Redis 环境中执行：

```text
V1 → V12 全量迁移
        ↓
M3 客户/账号
        ↓
M4 资源同步/资源池
        ↓
M5 产品/套餐/资源策略
        ↓
M6 订单/服务实例
        ↓
M7 Token / IP提取 / 白名单 / 运行日志
        ↓
M8 创建告警规则
        ↓
监控采集 → 触发线路告警
        ↓
确认 → 处理中
        ↓
重复采集 → 同一告警累加，不重复创建
        ↓
规则阈值调整 → 自动恢复
        ↓
PLATFORM + DINGTALK 通知/恢复通知记录
        ↓
当前小时统计重算
        ↓
验证资源 / IP / 客户 / 产品服务统计
        ↓
验证真实 log_usage 未产生时代理请求统计仍为 0
        ↓
验证首页运行指标
```

同时执行完整 Vue / TypeScript 前端构建。
