# IP代理管理平台

当前已完成 **M1 工程骨架 + M2 系统基础能力 + M3 客户管理 + M4 资源管理 + M5 产品管理 + M6 订单与服务管理**。仓库同时保留需求原型、数据库/API设计以及正式前后端工程。

## 目录

```text
.
├── prototype/              # 已确认的静态原型，作为产品/视觉基准
├── frontend/               # Vue 3 + TypeScript + Vite
├── backend/                # Java 21 + Spring Boot 4.1.1
├── database/migration/     # PostgreSQL Flyway V1-V10
├── docs/                   # ER、OpenAPI、架构设计
├── docker-compose.yml      # PostgreSQL + Redis + Backend + Frontend
└── .github/workflows/      # CI 构建与真实接口生命周期验证
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
- `biz_order + biz_order_item` 订单模型，可支持一个订单包含多个产品项
- 订单状态：待确认 → 待开通 → 已开通；未开通订单支持取消
- 订单本身不占用资源，只有服务开通阶段才执行资源选择与绑定
- 服务开通时固化产品、套餐、资源策略快照，历史服务不受后续配置修改影响
- 服务实例统一关联客户、订单/订单项、产品、套餐、资源策略
- 共享类服务绑定资源池；独享IP服务额外选择具体可用IP并生成 `res_exclusive_allocation`
- 独享IP选择使用数据库锁与占用校验，避免同一IP并发重复分配
- 隧道产品可绑定入口域名；VPN/隧道实际数据面仍通过 Adapter 对接
- `ServiceProvisionAdapter` 管理面/数据面边界：
  - 默认环境不会伪造 VPN/隧道数据面开通成功
  - `m6-mock` 仅用于 CI / 本地生命周期验证
- 服务凭证：Token / 用户名密码 / 隧道账号等按产品类型生成
- 新密钥仅在开通或重置操作响应中返回一次；历史查询只返回 `secret_mask`，不返回明文或哈希
- 服务白名单支持新增和停用
- 服务生命周期支持暂停、恢复、续费、终止
- 暂停服务同步冻结服务凭证；恢复时重新激活
- 续费形成 `svc_change` 变更记录
- 终止服务执行数据面释放后，撤销凭证、释放资源绑定并产生 `svc_release_record`
- 独享资源终止后进入 `PENDING_RECYCLE`，后续由资源检测/回收流程处理，而不是立即重新分配
- 订单、服务、凭证、白名单和生命周期操作均纳入权限与操作审计
- Flyway `V10__order_service_management.sql`
- M6 CI 在全新 PostgreSQL 上验证 V1-V10 + M3/M4/M5 回归 + 订单→服务→资源→凭证→白名单→暂停/恢复→续费→终止→释放完整生命周期

## 当前核心业务链

```text
客户
  ↓
订单 ─────→ 订单项
              ↓
          产品 + 套餐
              ↓
          产品资源策略
              ↓
      固化 Product/Package/Strategy Snapshot
              ↓
           服务实例
        ┌─────┼─────────┐
        ↓     ↓         ↓
     资源绑定  凭证      白名单
        ↓
   统一资源池
        ↓
共享资源 / 独享IP占用
```

资源与产品链：

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
短效IP / 长效IP / 独享IP / VPN / 隧道
             ↓
           套餐
             ↓
        订单 / 服务实例
```

## M6 数据面边界

Spring Boot 仍然是管理面，不承担真实代理/VPN/隧道流量转发：

```text
管理平台
  ↓
创建服务 / 选择资源 / 生成凭证 / 下发配置
  ↓
ServiceProvisionAdapter
  ↓
Proxy / VPN / Tunnel 数据面
  ↓
ROS / 家宽线路 / 出口IP
```

真实 VPN、Tunnel Gateway 或其他代理执行组件的接口协议尚未确认时，正式环境会明确返回“数据面适配器未配置”，不会使用 CI Mock 数据冒充真实开通结果。

## 当前数据库迁移

```text
V1  系统 + 客户基础
V2  资源基础模型
V3  产品 + 订单 + 服务基础模型
V4  监控 + 日志 + 告警 + 统计
V5  基础字典
V6  系统基础能力
V7  客户管理
V8  资源管理
V9  产品管理
V10 订单与服务管理
```

M6 验收通过后，**V1-V10 作为已执行历史迁移冻结**；后续数据库变化新增 V11 及以后迁移，不回改历史版本。

## 一键启动（推荐）

```bash
cp .env.example .env
docker compose up --build
```

访问：
- 前端：http://localhost:8081
- 后端：http://localhost:8080
- Swagger：http://localhost:8080/swagger-ui.html
- Health：http://localhost:8080/actuator/health

开发环境默认账号：`admin / admin123`。

> 默认账号仅用于本地开发验证。非开发环境必须修改 `M1_ADMIN_PASSWORD`、`JWT_SECRET`，并单独配置生产资源与数据面密钥。

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
- M3 客户管理实际契约：`docs/api/openapi-customer-m3.yaml`
- M4 资源管理实际契约：`docs/api/openapi-resource-m4.yaml`
- M5 产品管理实际契约：`docs/api/openapi-product-m5.yaml`
- M6 订单与服务实际契约：`docs/api/openapi-order-service-m6.yaml`
- Open API：`docs/api/openapi-open-v1.yaml`
- 静态原型：`prototype/`

## M6 后续增强项

以下内容没有在本里程碑中伪造实现，后续结合真实运行环境继续增强：
- VPN / 隧道真实数据面 Adapter
- 自动到期扫描与批量释放任务
- 失败释放记录的人工/自动重试
- 更复杂的套餐变更、资源池变更、线路/IP更换流程
- 直接手工开通服务的更严格业务开关与客户状态策略

下一阶段建议进入 **M7 Open API 与运行日志体系**：把现有服务实例、Token、白名单和产品资源策略真正接到 `/api/open/v1` 客户调用链，并同步产生 IP 提取/API/使用日志，为后续监控、告警和统计提供真实运行数据。
