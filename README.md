# IP代理管理平台

当前已完成 **M1 工程骨架 + M2 系统基础能力 + M3 客户管理 + M4 资源管理**。仓库同时保留需求原型、数据库/API设计以及正式前后端工程。

## 目录

```text
.
├── prototype/              # 已确认的静态原型，作为产品/视觉基准
├── frontend/               # Vue 3 + TypeScript + Vite
├── backend/                # Java 21 + Spring Boot 4.1.1
├── database/migration/     # PostgreSQL Flyway V1-V8
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
- 资源配置和高影响动作写入操作审计
- Flyway `V8__resource_management.sql`
- M4 CI 在全新PostgreSQL上验证 M3回归 + V1-V8 + CentOS→ROS→线路→IP→资源池 + 供应商外部IP入池 + 域名引用

## M4 适配器边界

当前尚未拿到最终运维资源接口、供应商接口以及DNS更新接口协议，因此正式代码不写死猜测字段和控制命令。

```text
真实运维API / 外部供应商API
             ↓
      ResourceAdapter
             ↓
 标准化 CentOS / ROS / Line / IP
             ↓
      统一资源数据模型
             ↓
 资源池 → 产品资源策略（后续M5）
```

后续拿到真实接口后，应新增具体 Adapter，并将原始返回保存在 `raw_data` / 同步明细中；不需要修改资源域核心模型。

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
- Open API：`docs/api/openapi-open-v1.yaml`
- 静态原型：`prototype/`

下一阶段可进入 M5 产品管理，重点建立五类客户产品与统一资源池之间的产品资源策略关系。
