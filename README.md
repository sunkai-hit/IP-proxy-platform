# IP代理管理平台

当前已完成 **M1 工程骨架 + M2 系统基础能力 + M3 客户管理 + M4 资源管理 + M5 产品管理 + M6 订单与服务管理 + M7 Open API 与运行日志体系**。仓库同时保留需求原型、数据库/API设计以及正式前后端工程。

## 目录

```text
.
├── prototype/              # 已确认的静态原型，作为产品/视觉基准
├── frontend/               # Vue 3 + TypeScript + Vite
├── backend/                # Java 21 + Spring Boot 4.1.1
├── database/migration/     # PostgreSQL Flyway V1-V11
├── docs/                   # ER、OpenAPI、架构设计
├── docker-compose.yml      # PostgreSQL + Redis + Backend + Frontend
└── .github/workflows/      # CI 构建与真实业务生命周期验证
```

## 已实现里程碑

### M1 工程骨架
- Vue 3 / TypeScript / Vite / Element Plus / Pinia / Vue Router / Axios
- Spring Boot 4.1.1 / Spring MVC / Security / MyBatis
- PostgreSQL + Redis Docker Compose
- Flyway、统一 API 响应、全局异常、Request ID
- JWT 登录、Swagger UI、Actuator

### M2 系统基础能力
- 用户、角色、权限、字典、参数
- JWT 角色与权限载荷
- 前后端路由/按钮权限控制
- 登录日志、操作审计
- SUPER_ADMIN 与当前管理员防误操作保护

### M3 客户管理
- 客户列表、分页筛选、新增、编辑、详情
- 客户状态、个人/企业认证、客户账号生命周期
- 服务引用、使用概览
- 服务认证信息独立权限 `customer:credential:read`，仅展示 `secret_mask`
- Flyway `V7__customer_management.sql`

> M3 仍按主线需求实现。运营端反馈分支中的余额、折扣、客户来源、专属注册链接、充值/退款/财务等内容尚未并入正式工程。

### M4 资源管理
- CentOS、ROS、家宽/线路、IP资源：同步、分页列表、详情
- 明确资源层级：`CentOS → ROS → 家宽/线路 → IP`
- `ResourceAdapter` 隔离真实运维/供应商接口协议
- IP资源池、独享资源、域名配置、外部供应商、资源同步任务
- 自有IP与外部IP统一进入 `res_ip` 后再进入统一资源池
- 基础资源不提供未经确认的新增/修改/删除/远程控制能力
- Flyway `V8__resource_management.sql`

### M5 产品管理
- 五类产品：短效IP、长效IP、独享IP、VPN、隧道
- 产品通用配置 + 五类专项配置
- 套餐管理与权益/价格配置
- 产品资源策略直接引用 M4 `res_pool`
- 策略验证通过后才能启用；产品启用要求专项配置完整且存在有效 ACTIVE 策略
- 产品复制只复制产品+专项配置；套餐/策略不随产品复制
- 套餐复制默认停用
- Flyway `V9__product_management.sql`

### M6 订单与服务管理
- `biz_order + biz_order_item` 订单模型，一个订单可包含多个产品项
- 订单状态：待确认 → 待开通 → 已开通；未开通订单支持取消
- 订单本身不占资源，只有服务开通阶段才执行资源选择与绑定
- 服务开通时固化产品、套餐、资源策略快照，历史服务不受后续配置修改影响
- 服务实例统一关联客户、订单/订单项、产品、套餐、资源策略
- 共享服务绑定资源池；独享IP服务额外锁定具体IP并生成 `res_exclusive_allocation`
- 服务凭证：Token / 用户名密码 / 隧道账号等按产品类型生成
- 新密钥仅在开通或重置响应中返回一次；历史查询只返回 `secret_mask`
- 服务白名单支持新增和停用
- 生命周期支持暂停、恢复、续费、终止
- 终止服务撤销凭证、释放资源绑定并产生 `svc_release_record`
- 独享资源终止后进入 `PENDING_RECYCLE`
- `ServiceProvisionAdapter` 隔离真实 Proxy / VPN / Tunnel 数据面
- Flyway `V10__order_service_management.sql`

### M7 Open API + 运行日志体系

#### 客户 Open API
沿用已确认的客户调用习惯：

```text
客户账号用户名 + 密码
        ↓
POST /api/open/v1/auth/token
        ↓
      Open Token
        ↓
┌───────────────────────────────┐
│ GET  /proxy/extract           │
│ GET  /whitelist               │
│ POST /whitelist               │
│ DELETE /whitelist/{ip}        │
└───────────────────────────────┘
```

当前实际接口：
- `POST /api/open/v1/auth/token`：获取/刷新 Token
- `GET /api/open/v1/proxy/extract`：提取IP
- `GET /api/open/v1/whitelist`：查询白名单
- `POST /api/open/v1/whitelist`：新增白名单
- `DELETE /api/open/v1/whitelist/{ip}`：停用白名单

Open Token 推荐通过 `Authorization: Bearer <token>` 传递，同时兼容 `?token=` 调用方式。

#### Token 与服务实例绑定
Open Token 不是独立的“代理账号”，而是客户账号访问具体服务实例的认证凭证：

```text
customer_account
      ↓
open_access_grant
      ↓
svc_instance
      ↓
产品 / 套餐 / 资源策略 / 资源池
```

每次调用都实时校验：
- 客户状态必须为 ACTIVE
- 客户账号必须为 ACTIVE
- 服务必须为 ACTIVE
- 服务已到生效时间
- 服务未过期
- 产品接入方式必须为 `API_EXTRACT`

因此服务暂停、终止或到期后，Open API 会立即失效，而不是只依赖 Token 自身状态。

#### Token 不明文落库
M7 使用：

```text
oa.<grantId>.<tokenVersion>.<HMAC-signature>
```

签名使用服务端密钥生成，不保存完整 Token 明文。

- `changeToken=false`：同一版本返回同一个 Token
- `changeToken=true`：递增 `token_version`，生成新 Token，旧 Token 立即失效
- 完整 Token 不写入数据库和运行日志

#### 当前多服务边界
当前客户账号若同时存在多个处于 ACTIVE、已生效且 `access_mode_code=API_EXTRACT` 的服务，获取 Token 返回：

`OPEN_SERVICE_AMBIGUOUS`

在尚未确认“客户如何选择具体服务”的 Open API 协议前，不自行猜测 serviceId/productId 参数规则。

#### IP 提取规则
提取接口实际校验：
- 单次提取数量上限
- 套餐提取总额度
- 提取频率/时间窗口
- 服务绑定资源池是否可用
- IP可用状态
- 重复IP标记
- 地区筛选
- 运营商筛选
- 去重周期

资源路径：

```text
Open Token
   ↓
服务实例
   ↓
固化的服务策略
   ↓
服务资源绑定
   ↓
统一资源池
   ↓
可用 res_ip
   ↓
返回客户
```

M7 不绕过 M4/M5/M6 已建立的资源池、产品策略和服务实例模型直接取IP。

#### 运行日志
后台新增“运行日志”页面及权限：
- `runtime-log:access`
- `runtime-log:extract:read`
- `runtime-log:api:read`
- `runtime-log:usage:read`

页面包含：
1. IP提取日志
2. API日志
3. 使用日志

IP提取接口会真实写入：
- `log_ip_extract`
- `log_api`

两类日志通过同一个 `requestId` 关联，可从一次客户调用追溯到具体服务、客户、账号、产品、请求条件、返回数量、错误码和耗时。

失败请求日志使用独立 `REQUIRES_NEW` 事务写入：即使 Token 无效、服务暂停、额度不足或资源不可用导致业务事务回滚，失败调用痕迹仍会保留。

#### 使用日志边界
`log_usage` 定义的是**真实代理流量使用日志**，例如：
- 客户端IP
- 出口IP
- 协议
- 目标Host/URL
- 上下行字节
- 请求结果
- 状态码
- 耗时

因此 M7 **不会把“提取IP”伪造成“使用IP”**。

当前 M7 已提供使用日志后台查询能力；真正写入 `log_usage` 必须等待 Proxy / VPN / Tunnel / ROS 等真实数据面回传协议确认后接入。目标URL等敏感字段后续展示和导出必须继续受权限与脱敏策略控制。

- Flyway `V11__open_api_runtime_logs.sql`
- Open API 实际契约：`docs/api/openapi-open-v1.yaml`
- 运行日志 Admin API 实际契约：`docs/api/openapi-runtime-log-m7.yaml`
- M7 CI 在全新 PostgreSQL 上验证 V1-V11 以及 M3→M7 完整调用链

## 当前核心业务链

```text
客户
 ↓
客户账号
 ↓
订单 → 订单项
        ↓
    产品 + 套餐
        ↓
    产品资源策略
        ↓
      服务实例
        ↓
  Open Access Grant
        ↓
     Open Token
        ↓
     IP提取API
        ↓
   统一资源池 → IP
        ↓
IP提取日志 + API日志
```

资源/数据面关系：

```text
真实运维API / 外部供应商API
             ↓
      ResourceAdapter
             ↓
 CentOS → ROS → 家宽/线路 → IP
             ↓
         统一资源池
             ↓
       产品资源策略
             ↓
          服务实例
             ↓
     Open API / 数据面配置
             ↓
 Proxy / VPN / Tunnel 数据面
             ↓
     实际代理流量与使用日志
```

## 管理面与数据面边界

Spring Boot 管理平台负责：
- 客户、账号、认证
- 资源同步与资源池组织
- 产品/套餐/资源策略
- 订单与服务实例
- Open Token 与调用权限
- IP提取API
- 运行日志、后续监控/告警/统计

Spring Boot 不承担真实代理/VPN/隧道流量转发。真实数据面仍通过 Adapter / Gateway / ROS 等组件对接。

在真实 VPN、Tunnel Gateway、Proxy Gateway 或 ROS 控制/日志协议尚未确认时，正式工程不会使用 CI Mock 冒充真实数据面能力。

## 当前数据库迁移

```text
V1  系统 + 客户基础
V2  资源基础模型
V3  产品 + 订单 + 服务基础模型
V4  监控 + 日志 + 告警 + 统计基础模型
V5  基础字典
V6  系统基础能力
V7  客户管理
V8  资源管理
V9  产品管理
V10 订单与服务管理
V11 Open API + 运行日志体系
```

M7 验收通过后，**V1-V11 作为已执行历史迁移冻结**。后续数据库变化从 `V12__` 开始，不回改历史迁移。

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

> 默认账号仅用于本地开发验证。非开发环境必须修改 `M1_ADMIN_PASSWORD`、`JWT_SECRET`，并单独配置生产资源、数据面和供应商密钥。

## 分开开发

```bash
docker compose up -d postgres redis
cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev
```

Vite 开发服务器默认 `http://localhost:5173`，会代理 `/api` 到 8080 后端。

## 设计与接口基线

- 数据库：`docs/database/` + `database/migration/`
- Admin API 总体基线：`docs/api/openapi-admin-v1.json`
- M3 客户管理：`docs/api/openapi-customer-m3.yaml`
- M4 资源管理：`docs/api/openapi-resource-m4.yaml`
- M5 产品管理：`docs/api/openapi-product-m5.yaml`
- M6 订单与服务：`docs/api/openapi-order-service-m6.yaml`
- M7 客户 Open API：`docs/api/openapi-open-v1.yaml`
- M7 运行日志 Admin API：`docs/api/openapi-runtime-log-m7.yaml`
- 静态原型：`prototype/`

## 后续增强边界

当前尚未伪造实现的能力：
- VPN / 隧道 / Proxy Gateway 真实数据面 Adapter
- 真实 `log_usage` 数据面回传
- 自动到期扫描与批量释放
- 失败释放记录的人工/自动重试
- 多个 API_EXTRACT 服务时的客户侧服务选择协议
- 更复杂的套餐变更、资源池变更、线路/IP更换流程
- 更严格的 Open API 限流、网关级防护和分布式额度计数

下一阶段可进入 **M8 监控 + 告警 + 数据统计**：基于 M4 资源状态、M6 服务实例以及 M7 已产生的真实调用日志，形成资源监控、服务监控、告警规则/事件、钉钉通知和按小时统计闭环。
