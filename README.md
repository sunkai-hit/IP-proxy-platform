# IP代理管理平台

当前已完成 **M1 工程骨架 + M2 系统基础能力 + M3 客户管理 + M4 资源管理 + M5 产品管理**。仓库同时保留需求原型、数据库/API设计以及正式前后端工程。

## 目录

```text
.
├── prototype/              # 已确认的静态原型，作为产品/视觉基准
├── frontend/               # Vue 3 + TypeScript + Vite
├── backend/                # Java 21 + Spring Boot 4.1.1
├── database/migration/     # PostgreSQL Flyway V1-V9
├── docs/                   # ER、OpenAPI、架构设计
├── docker-compose.yml      # PostgreSQL + Redis + Backend + Frontend
└── .github/workflows/      # CI 构建与真实接口冒烟验证
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
- 资源概览与资源模块权限体系
- CentOS、ROS、家宽/线路、IP资源：同步、分页列表、详情
- 明确资源层级：`CentOS → ROS → 家宽/线路 → IP`
- 基础资源不提供未经确认的新增/修改/删除/远程控制能力
- `ResourceAdapter` 接入层：核心资源域只消费标准化资源，不绑定尚未确认的运维接口协议
- 默认 `NoopResourceAdapter`：未配置真实接口时明确返回“适配器未配置”
- `m4-mock` Adapter：仅用于CI/本地联调，不代表真实接口字段
- IP资源池：新增、修改、启停、按来源/地区/运营商基础规则重新计算成员
- 独享资源：列表、详情、受控释放；不在资源模块手工创建占用
- 域名配置：维护域名与线路/IP引用；未配置DNS适配器时刷新仅更新平台目标IP并标记 `PENDING_UPDATE`
- 外部供应商：配置、AES/GCM加密密钥、状态、连通测试、外部IP同步
- 自有IP与外部IP统一进入 `res_ip`，再按规则进入统一资源池
- 同步任务/处理明细、失败/部分成功任务重试
- Flyway `V8__resource_management.sql`

### M5 产品管理
- 五类客户产品作为明确的一等产品类型：短效IP、长效IP、独享IP、VPN、隧道
- 产品通用定义：编号、名称、接入方式、代理协议、认证方式、资源模式、地区/运营商范围、说明、状态
- 五类专项配置分别落在独立配置表，不把不同产品行为混进一个通用JSON：
  - 短效IP：有效期、单次提取、频控、去重、白名单、切换策略
  - 长效IP：保持周期、IP/线路绑定、自动替换、故障阈值和故障转移
  - 独享IP：独享IP/线路、默认数量、锁定超时、替换/释放规则
  - VPN：协议、凭证、设备/连接上限、出口策略；真实协议/运行位置仍保持待确认边界
  - 隧道：入口域名/主机、端口、会话保持、并发和IP切换规则；不猜测实际运行节点
- 套餐管理：服务天数、IP数量、提取/流量/并发/设备/白名单/换IP等权益、价格与扩展配额
- 产品资源策略直接引用 M4 `res_pool`，支持优先级/权重、来源优先级、地区/运营商、可选 CentOS/ROS/线路/IP/NODE_TAG 范围及高级故障/保持/切换规则
- 新策略默认 `DISABLED`；验证资源池状态与实时成员后才允许启用
- 已启用策略一旦修改自动停用，必须重新验证，避免运行中配置静默变化
- 产品启用门槛：专项配置完成 + 至少一条 ACTIVE 且实时验证有效的资源策略
- 产品编号/类型创建后不可变；产品复制只复制通用+专项配置，新副本 `DRAFT`，不复制套餐/策略
- 套餐复制副本默认 `DISABLED`
- M5 配置仅影响后续开通，M6 订单/服务使用既有 product/package/strategy snapshot 隔离历史
- 产品域权限和关键变更全部写入操作审计
- Flyway `V9__product_management.sql`
- M5 CI 在全新 PostgreSQL 上验证 V1-V9 + M3/M4 回归 + 五类产品专项配置 + M4统一资源池→产品策略→策略验证/启用→五类产品启用 + 套餐/复制边界

## M4/M5 资源与产品边界

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
    M6 订单 → 服务实例
```

- M4 管“有哪些资源、资源状态和如何组织成池”。
- M5 管“卖什么产品、套餐权益以及产品从哪些池按什么策略取资源”。
- 产品模块不会直接重拨线路、重启ROS或新增基础资源。
- 后续拿到真实运维/供应商/DNS协议后，通过 Adapter 对接，不改变 M4/M5 核心模型。

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

> 默认账号仅用于本地开发验证。非开发环境必须修改 `M1_ADMIN_PASSWORD`、`JWT_SECRET`，并单独配置生产资源密钥。

## 分开开发

```bash
docker compose up -d postgres redis
cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev
```

Vite 开发服务器默认 `http://localhost:5173`，会代理 `/api` 到 8080 后端。

## 设计基线

- 数据库：`docs/database/` + `database/migration/`
- Admin API 总体基线：`docs/api/openapi-admin-v1.json`
- M3 客户管理实际契约：`docs/api/openapi-customer-m3.yaml`
- M4 资源管理实际契约：`docs/api/openapi-resource-m4.yaml`
- M5 产品管理实际契约：`docs/api/openapi-product-m5.yaml`
- Open API：`docs/api/openapi-open-v1.yaml`
- 静态原型：`prototype/`

下一阶段进入 **M6 订单与服务管理**：重点实现订单确认 → 服务开通 → 固化产品/套餐/策略快照 → 资源绑定/独享占用 → 服务认证信息 → 服务实例状态 → 变更、到期与资源释放闭环。
