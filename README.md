# IP代理管理平台

当前进入 **M1 工程骨架阶段**。仓库同时保留需求原型、数据库/API设计以及正式前后端工程。

## 目录

```text
.
├── prototype/              # 已确认的静态原型，作为产品/视觉基准
├── frontend/               # Vue 3 + TypeScript + Vite
├── backend/                # Java 21 + Spring Boot 4.1.1
├── database/migration/     # PostgreSQL Flyway V1-V5
├── docs/                   # ER、OpenAPI、架构设计
├── docker-compose.yml      # PostgreSQL + Redis + Backend + Frontend
└── .github/workflows/      # M1 CI 构建与联调验证
```

## M1 已实现

- Vue 3 / TypeScript / Vite / Element Plus / Pinia / Vue Router / Axios 工程骨架
- Spring Boot 4.1.1 / Spring MVC / Security / MyBatis 工程骨架
- PostgreSQL + Redis Docker Compose
- Flyway 自动加载仓库现有 `database/migration/V1-V5`
- 统一 API 响应、全局异常、Request ID
- JWT 登录与认证过滤器
- 开发管理员自动初始化
- Swagger UI / Actuator
- Vue 登录页、后台 Layout、Dashboard 真实接口联调
- 原型移动到 `prototype/` 保留

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

> 默认账号仅用于 M1 本地开发验证。非开发环境必须修改 `M1_ADMIN_PASSWORD` 与 `JWT_SECRET`。

## 分开开发

```bash
docker compose up -d postgres redis
cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev
```

Vite 开发服务器默认 `http://localhost:5173`，会代理 `/api` 到 8080 后端。

## 设计基线

- 数据库：`docs/database/` + `database/migration/`
- API：`docs/api/`
- 静态原型：`prototype/`

M2 将在该工程骨架上实现用户、角色、权限、字典、参数和审计等系统基础模块。
