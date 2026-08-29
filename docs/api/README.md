# IP代理管理平台 API 设计基线 V1.0

## 1. 文件

- `openapi-admin-v1.json`：后台管理端 OpenAPI 3.1.2 根规范，Server Base URL：`/api/admin/v1`。
  - `admin-paths/`：按业务模块拆分的路径定义。
  - `admin-request-schemas.json`：Admin 请求对象 Schema。
- `openapi-open-v1.yaml`：客户开放 API OpenAPI 3.1.2 规范，Server Base URL：`/api/open/v1`。
- `API-CATALOG.md`：Admin API 人类可读接口清单，目前共 189 个 operation。

两套 API 均采用 **OpenAPI 3.1.2**。Admin 规范使用 OpenAPI 标准支持的外部 `$ref` 进行模块化拆分，后续可由 Swagger/OpenAPI 工具链打包或生成代码。

## 2. API分层

### Admin API

供 Vue 管理后台调用，使用 Spring Security + JWT Bearer Token。

主要 Tag：

- Auth / Dashboard
- Customer / CustomerAuth / CustomerAccount
- Resource / Pool / Exclusive / Domain / Supplier / SyncTask
- Product / Package / Strategy
- Order / Service
- Monitor / Log / Alarm / Statistics
- User / Role / Dictionary / Parameter / Audit

### Open API

供代理服务客户调用。当前仅纳入业务已确认接口：

- `POST /auth/token`：用户名密码换取/刷新 Token
- `GET /proxy/extract`：按 Token + amount 提取代理IP
- `GET /whitelist`：查询白名单
- `POST /whitelist`：新增白名单
- `DELETE /whitelist/{ip}`：删除白名单

未确认的客户侧订单、余额、财务等能力不在本版本中扩展。

### Internal API

已在系统架构中保留 `/api/internal/v1/*` 边界，但本轮不生成对外 OpenAPI 文件。原因是 Internal API 取决于后续 ROS、CentOS、VPN、Tunnel Gateway 的真实接口与部署方式，目前尚未确认，避免提前固化错误协议。

## 3. REST约定

- 列表：`GET /resources`
- 新增：`POST /resources`
- 详情：`GET /resources/{id}`
- 普通编辑：`PUT /resources/{id}`
- 高影响业务动作：显式命令接口，例如：
  - `POST /services/{id}/terminate`
  - `POST /services/{id}/credentials/reset`
  - `POST /exclusive-allocations/{id}/release`
  - `POST /customer-auth/{id}/approve`

## 4. 统一返回

```json
{
  "code": "0",
  "message": "success",
  "data": {},
  "requestId": "req_xxx",
  "timestamp": 1787900000000
}
```

分页数据：

```json
{
  "page": 1,
  "size": 20,
  "total": 218,
  "items": []
}
```

## 5. 错误响应

OpenAPI统一声明：

- `400` 参数错误
- `401` 未认证/Token失效
- `403` 无权限
- `404` 对象不存在
- `409` 状态或唯一约束冲突
- `500` 服务内部错误

业务实现时 `code` 使用平台业务错误码，不直接把数据库错误暴露给调用方。

## 6. 鉴权

### Admin

```http
Authorization: Bearer <jwt>
```

### Open API

当前为了兼容既有接口习惯，在规范中保留查询参数：

```text
?token=xxx
```

正式实现时建议同时支持 `Authorization` Header，并在完成客户端兼容迁移后逐步弱化 URL Token，避免 Token 出现在代理、浏览器和访问日志中。
