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

## 7. 第三轮增量候选契约

[openapi-v12-round3-contract.yaml](openapi-v12-round3-contract.yaml) 单独定义套餐宽限、真实交付摘要、出口IPv4授权、人工候选与切换预检/任务、到期队列和异步操作查询。

状态仅为增量候选。与原Admin根规范的路径/统一ApiResponse封装、精确权限码和数据面幂等签约须经联调核对；不表示全量路径已实现。尤其 `failover-tasks` 在实际资源API/产品接口确认前必须拒绝执行而不是返回虚假成功。

## 8. 付款优先的正式业务规则和实现边界

[付款核实及外部采购增量契约](openapi-v12-r3-payment-first.yaml)说明独立的 `POST /orders/{orderId}/payment-confirmation`、客户专属采购 `sourceCustomerOrderId` 来源校验，以及采购成本、套餐额度和剩余额度为可选展示数据。**此接口仅记录运营核实过的外部到账凭证，并不实际收款、扣款、向供应商付款或自动采购。** 缺少核实凭证时，平台开通及客户采购登记后端拒绝，真三方联调仍另行验收。

## 9. 主子表与跨单据导航（2026-10-14）

[七个业务只读关联接口与验收说明](V1.2-R3-业务主子表关联与跨单据导航契约.md)：客户→订单与服务、线路→成员资源池与服务/出口、池→直接服务绑定、采购→反向代理与服务、服务→带业务名称的资源绑定。采用原单据外键和有效占用证据，不从旧线路客户字段或动态当前IP猜关系；每次只显示至多200条，校验父子业务权限。**实现接口/页面不等于真实产品网络交付。**
