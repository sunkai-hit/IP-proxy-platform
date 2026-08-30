# IP代理管理平台

当前已完成 **M1 工程骨架 + M2 系统基础能力 + M3 客户管理**。仓库同时保留需求原型、数据库/API设计以及正式前后端工程。

## 目录

```text
.
├── prototype/              # 已确认的静态原型，作为产品/视觉基准
├── frontend/               # Vue 3 + TypeScript + Vite
├── backend/                # Java 21 + Spring Boot 4.1.1
├── database/migration/     # PostgreSQL Flyway V1-V7
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
- Vue 登录页、后台 Layout、Dashboard 真实接口联调
- 原型移动到 `prototype/` 保留

### M2 系统基础能力
- 用户、角色、权限、字典、参数
- JWT 角色与权限载荷
- 前后端路由/按钮权限控制
- 登录日志、操作审计
- SUPER_ADMIN 与当前管理员防误操作保护

### M3 客户管理
- 客户列表、分页筛选、新增、编辑、详情
- 客户状态：ACTIVE / FROZEN / DISABLED
- 个人/企业认证申请、详情、审批通过/驳回
- 客户账号创建、密码重置、冻结/恢复/停用
- 客户详情聚合认证记录、账号、服务引用与使用概览
- 服务认证信息独立权限 `customer:credential:read`，仅展示 `secret_mask`
- 客户模块操作全部写入操作审计
- Flyway `V7__customer_management.sql`
- M3 CI 覆盖客户创建 → 认证 → 审批 → 账号 → 状态流转 → 详情/审计

> M3 仍按主线需求实现。运营端反馈分支中的余额、折扣、客户来源、专属注册链接、充值/退款/财务等内容尚未并入正式工程。

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

> 默认账号仅用于本地开发验证。非开发环境必须修改 `M1_ADMIN_PASSWORD` 与 `JWT_SECRET`。

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
- Open API：`docs/api/openapi-open-v1.yaml`
- 静态原型：`prototype/`

下一阶段进入资源/产品前，建议继续坚持“数据库迁移 + 后端业务 + Vue 页面 + OpenAPI + CI 冒烟”作为每个模块的完成标准。
