# Admin API 接口清单
共 **189** 个 operation，Base URL：`/api/admin/v1`。

## Auth - 后台认证

|方法|路径|说明|operationId|
|---|---|---|---|
|POST|`/auth/login`|后台登录|`adminLogin`|
|POST|`/auth/logout`|退出登录|`adminLogout`|
|GET|`/auth/me`|当前用户信息|`getCurrentUser`|

## Dashboard - 工作台

|方法|路径|说明|operationId|
|---|---|---|---|
|GET|`/dashboard`|工作台概览|`getDashboard`|

## Customer - 客户

|方法|路径|说明|operationId|
|---|---|---|---|
|GET|`/customers`|Customer列表|`listCustomer`|
|POST|`/customers`|新增Customer|`createCustomer`|
|GET|`/customers/{id}`|Customer详情|`getCustomer`|
|PUT|`/customers/{id}`|修改Customer|`updateCustomer`|
|POST|`/customers/{id}/freeze`|冻结客户|`freezeCustomer`|
|POST|`/customers/{id}/resume`|恢复客户|`resumeCustomer`|
|POST|`/customers/{id}/disable`|停用客户|`disableCustomer`|
|GET|`/customers/{id}/services`|客户服务列表|`listCustomerServices`|
|GET|`/customers/{id}/usage`|客户使用概览|`getCustomerUsage`|

## CustomerAuth - 客户认证

|方法|路径|说明|operationId|
|---|---|---|---|
|GET|`/customer-auth`|认证申请列表|`listCustomerAuth`|
|GET|`/customer-auth/{id}`|认证申请详情|`getCustomerAuth`|
|POST|`/customer-auth/{id}/approve`|审核通过|`approveCustomerAuth`|
|POST|`/customer-auth/{id}/reject`|审核驳回|`rejectCustomerAuth`|

## CustomerAccount - 客户账号

|方法|路径|说明|operationId|
|---|---|---|---|
|GET|`/customer-accounts`|客户账号列表|`listCustomerAccounts`|
|POST|`/customer-accounts`|创建客户账号|`createCustomerAccount`|
|GET|`/customer-accounts/{id}`|客户账号详情|`getCustomerAccount`|
|POST|`/customer-accounts/{id}/reset-password`|重置客户密码|`ResetpasswordCustomerAccount`|
|POST|`/customer-accounts/{id}/freeze`|冻结客户账号|`FreezeCustomerAccount`|
|POST|`/customer-accounts/{id}/resume`|恢复客户账号|`ResumeCustomerAccount`|
|POST|`/customer-accounts/{id}/disable`|停用客户账号|`DisableCustomerAccount`|

## Resource - 基础资源

|方法|路径|说明|operationId|
|---|---|---|---|
|GET|`/resources/overview`|资源概览|`getResourceOverview`|
|GET|`/resources/centos`|CENTOS列表|`listCentos`|
|GET|`/resources/centos/{id}`|CENTOS详情|`getCentos`|
|GET|`/resources/ros`|ROS列表|`listRos`|
|GET|`/resources/ros/{id}`|ROS详情|`getRos`|
|GET|`/resources/lines`|LINE列表|`listLine`|
|GET|`/resources/lines/{id}`|LINE详情|`getLine`|
|GET|`/resources/ips`|IP列表|`listIp`|
|GET|`/resources/ips/{id}`|IP详情|`getIp`|

## Pool - 资源池

|方法|路径|说明|operationId|
|---|---|---|---|
|GET|`/resource-pools`|Pool列表|`listPool`|
|POST|`/resource-pools`|新增Pool|`createPool`|
|GET|`/resource-pools/{id}`|Pool详情|`getPool`|
|PUT|`/resource-pools/{id}`|修改Pool|`updatePool`|
|GET|`/resource-pools/{id}/ips`|资源池IP列表|`listPoolIps`|
|POST|`/resource-pools/{id}/enable`|启用资源池|`enablePool`|
|POST|`/resource-pools/{id}/disable`|停用资源池|`disablePool`|
|POST|`/resource-pools/{id}/recalculate`|重新计算资源池成员|`recalculatePool`|

## Exclusive - 独享资源

|方法|路径|说明|operationId|
|---|---|---|---|
|GET|`/exclusive-allocations`|独享占用列表|`listExclusiveAllocations`|
|GET|`/exclusive-allocations/{id}`|独享占用详情|`getExclusiveAllocation`|
|POST|`/exclusive-allocations/{id}/release`|释放独享资源|`releaseExclusiveAllocation`|

## Domain - 域名

|方法|路径|说明|operationId|
|---|---|---|---|
|GET|`/domains`|Domain列表|`listDomain`|
|POST|`/domains`|新增Domain|`createDomain`|
|GET|`/domains/{id}`|Domain详情|`getDomain`|
|PUT|`/domains/{id}`|修改Domain|`updateDomain`|
|POST|`/domains/{id}/refresh`|刷新域名解析|`refreshDomain`|

## Supplier - 供应商

|方法|路径|说明|operationId|
|---|---|---|---|
|GET|`/suppliers`|Supplier列表|`listSupplier`|
|POST|`/suppliers`|新增Supplier|`createSupplier`|
|GET|`/suppliers/{id}`|Supplier详情|`getSupplier`|
|PUT|`/suppliers/{id}`|修改Supplier|`updateSupplier`|
|POST|`/suppliers/{id}/test`|测试供应商接口|`testSupplier`|
|POST|`/suppliers/{id}/enable`|启用供应商|`enableSupplier`|
|POST|`/suppliers/{id}/pause`|暂停供应商|`pauseSupplier`|
|POST|`/suppliers/{id}/disable`|停用供应商|`disableSupplier`|

## SyncTask - 资源同步

|方法|路径|说明|operationId|
|---|---|---|---|
|POST|`/resources/centos/sync`|触发CENTOS同步|`syncCentos`|
|POST|`/resources/ros/sync`|触发ROS同步|`syncRos`|
|POST|`/resources/lines/sync`|触发LINE同步|`syncLine`|
|POST|`/resources/ips/sync`|触发IP同步|`syncIp`|
|GET|`/sync-tasks`|同步任务列表|`listSyncTasks`|
|POST|`/sync-tasks`|创建同步任务|`createSyncTask`|
|GET|`/sync-tasks/{id}`|同步任务详情|`getSyncTask`|
|POST|`/sync-tasks/{id}/retry`|重试同步任务|`retrySyncTask`|
|GET|`/sync-tasks/{id}/details`|同步明细|`listSyncTaskDetails`|

## Product - 产品

|方法|路径|说明|operationId|
|---|---|---|---|
|GET|`/products`|Product列表|`listProduct`|
|POST|`/products`|新增Product|`createProduct`|
|GET|`/products/{id}`|Product详情|`getProduct`|
|PUT|`/products/{id}`|修改Product|`updateProduct`|
|POST|`/products/{id}/enable`|启用产品|`enableProduct`|
|POST|`/products/{id}/disable`|停用产品|`disableProduct`|
|POST|`/products/{id}/copy`|复制产品|`copyProduct`|
|GET|`/products/{id}/config`|产品专项配置|`getProductConfig`|
|PUT|`/products/{id}/config`|更新产品专项配置|`updateProductConfig`|

## Package - 套餐

|方法|路径|说明|operationId|
|---|---|---|---|
|GET|`/packages`|Package列表|`listPackage`|
|POST|`/packages`|新增Package|`createPackage`|
|GET|`/packages/{id}`|Package详情|`getPackage`|
|PUT|`/packages/{id}`|修改Package|`updatePackage`|
|POST|`/packages/{id}/enable`|启用套餐|`enablePackage`|
|POST|`/packages/{id}/disable`|停用套餐|`disablePackage`|
|POST|`/packages/{id}/copy`|复制套餐|`copyPackage`|

## Strategy - 产品资源策略

|方法|路径|说明|operationId|
|---|---|---|---|
|GET|`/resource-strategies`|Strategy列表|`listResourceStrategy`|
|POST|`/resource-strategies`|新增Strategy|`createResourceStrategy`|
|GET|`/resource-strategies/{id}`|Strategy详情|`getResourceStrategy`|
|PUT|`/resource-strategies/{id}`|修改Strategy|`updateResourceStrategy`|
|POST|`/resource-strategies/{id}/validate`|验证资源策略|`validateResourceStrategy`|
|POST|`/resource-strategies/{id}/enable`|启用资源策略|`enableResourceStrategy`|
|POST|`/resource-strategies/{id}/disable`|停用资源策略|`disableResourceStrategy`|

## Order - 订单

|方法|路径|说明|operationId|
|---|---|---|---|
|GET|`/orders`|订单列表|`listOrders`|
|POST|`/orders`|创建订单|`createOrder`|
|GET|`/orders/{id}`|订单详情|`getOrder`|
|PUT|`/orders/{id}`|修改草稿订单|`updateOrder`|
|POST|`/orders/{id}/confirm`|确认订单|`confirmOrder`|
|POST|`/orders/{id}/cancel`|取消订单|`cancelOrder`|
|POST|`/orders/{id}/provision`|按订单开通服务|`provisionOrder`|

## Service - 服务实例

|方法|路径|说明|operationId|
|---|---|---|---|
|GET|`/services`|服务实例列表|`listServices`|
|POST|`/services`|直接/按订单开通服务|`createService`|
|GET|`/services/{id}`|服务实例详情|`getService`|
|POST|`/services/{id}/suspend`|暂停服务|`suspendService`|
|POST|`/services/{id}/resume`|恢复服务|`resumeService`|
|POST|`/services/{id}/terminate`|终止服务|`terminateService`|
|POST|`/services/{id}/renew`|服务续费|`renewService`|
|POST|`/services/{id}/changes`|发起服务变更|`createServiceChange`|
|GET|`/services/{id}/changes`|服务变更记录|`listServiceChanges`|
|GET|`/services/{id}/resource-bindings`|服务资源绑定|`listServiceResourceBindings`|
|GET|`/services/{id}/credentials`|服务认证凭证|`listServiceCredentials`|
|POST|`/services/{id}/credentials/reset`|重置服务凭证|`resetServiceCredential`|
|GET|`/services/{id}/whitelist`|服务白名单|`listServiceWhitelist`|
|POST|`/services/{id}/whitelist`|新增服务白名单|`addServiceWhitelist`|
|DELETE|`/services/{id}/whitelist/{whitelistId}`|删除服务白名单|`deleteServiceWhitelist`|
|GET|`/service-changes`|全部服务变更列表|`listAllServiceChanges`|
|GET|`/service-changes/{id}`|服务变更详情|`getServiceChange`|
|GET|`/service-releases`|资源释放记录|`listServiceReleases`|
|GET|`/service-releases/{id}`|资源释放详情|`getServiceRelease`|
|POST|`/service-releases/{id}/retry`|重试资源释放|`retryServiceRelease`|

## Monitor - 监控

|方法|路径|说明|operationId|
|---|---|---|---|
|GET|`/monitor/centos`|CENTOS监控列表|`listMonitorCentos`|
|GET|`/monitor/centos/{id}`|CENTOS监控详情|`getMonitorCentos`|
|GET|`/monitor/centos/{id}/metrics`|CENTOS历史指标|`getMonitorCentosMetrics`|
|GET|`/monitor/ros`|ROS监控列表|`listMonitorRos`|
|GET|`/monitor/ros/{id}`|ROS监控详情|`getMonitorRos`|
|GET|`/monitor/ros/{id}/metrics`|ROS历史指标|`getMonitorRosMetrics`|
|GET|`/monitor/lines`|LINE监控列表|`listMonitorLine`|
|GET|`/monitor/lines/{id}`|LINE监控详情|`getMonitorLine`|
|GET|`/monitor/lines/{id}/metrics`|LINE历史指标|`getMonitorLineMetrics`|
|GET|`/monitor/ips`|IP监控列表|`listMonitorIp`|
|GET|`/monitor/ips/{id}`|IP监控详情|`getMonitorIp`|
|GET|`/monitor/ips/{id}/metrics`|IP历史指标|`getMonitorIpMetrics`|
|GET|`/monitor/suppliers`|SUPPLIER监控列表|`listMonitorSupplier`|
|GET|`/monitor/suppliers/{id}`|SUPPLIER监控详情|`getMonitorSupplier`|
|GET|`/monitor/suppliers/{id}/metrics`|SUPPLIER历史指标|`getMonitorSupplierMetrics`|
|GET|`/monitor/services`|SERVICE监控列表|`listMonitorService`|
|GET|`/monitor/services/{id}`|SERVICE监控详情|`getMonitorService`|
|GET|`/monitor/services/{id}/metrics`|SERVICE历史指标|`getMonitorServiceMetrics`|

## Log - 日志

|方法|路径|说明|operationId|
|---|---|---|---|
|GET|`/logs/ip-extract`|IP提取日志|`listIpExtractLogs`|
|GET|`/logs/usage`|使用日志|`listUsageLogs`|
|GET|`/logs/api`|API日志|`listApiLogs`|
|GET|`/logs/ros`|ROS运行日志|`listRosLogs`|
|GET|`/logs/line-dial`|线路拨号日志|`listLineDialLogs`|
|GET|`/logs/ip-change`|IP变化日志|`listIpChangeLogs`|
|GET|`/logs/external`|外部资源获取日志|`listExternalLogs`|
|GET|`/logs/operation`|操作日志|`listOperationLogs`|

## Alarm - 告警

|方法|路径|说明|operationId|
|---|---|---|---|
|GET|`/alarms`|告警记录列表|`listAlarms`|
|GET|`/alarms/{id}`|告警详情|`getAlarm`|
|POST|`/alarms/{id}/acknowledge`|确认告警|`acknowledgeAlarm`|
|POST|`/alarms/{id}/process`|开始处理|`processAlarm`|
|POST|`/alarms/{id}/recover`|标记恢复|`recoverAlarm`|
|POST|`/alarms/{id}/close`|关闭告警|`closeAlarm`|
|GET|`/alarm-rules`|Alarm列表|`listAlarmRule`|
|POST|`/alarm-rules`|新增Alarm|`createAlarmRule`|
|GET|`/alarm-rules/{id}`|Alarm详情|`getAlarmRule`|
|PUT|`/alarm-rules/{id}`|修改Alarm|`updateAlarmRule`|
|POST|`/alarm-rules/{id}/test`|测试告警规则|`testAlarmRule`|
|POST|`/alarm-rules/{id}/enable`|启用告警规则|`enableAlarmRule`|
|POST|`/alarm-rules/{id}/disable`|停用告警规则|`disableAlarmRule`|
|POST|`/alarm-notifications/dingtalk/test`|测试钉钉通知|`testDingTalk`|

## Statistics - 统计

|方法|路径|说明|operationId|
|---|---|---|---|
|GET|`/statistics/resources`|基础资源统计|`getResourcesStatistics`|
|GET|`/statistics/ips`|IP资源统计|`getIpsStatistics`|
|GET|`/statistics/customer-usage`|客户使用统计|`getCustomerUsageStatistics`|
|GET|`/statistics/product-services`|产品与服务统计|`getProductServicesStatistics`|
|GET|`/statistics/suppliers`|外部供应商统计|`getSuppliersStatistics`|

## User - 用户

|方法|路径|说明|operationId|
|---|---|---|---|
|GET|`/system/users`|User列表|`listUser`|
|POST|`/system/users`|新增User|`createUser`|
|GET|`/system/users/{id}`|User详情|`getUser`|
|PUT|`/system/users/{id}`|修改用户|`updateUser`|
|POST|`/system/users/{id}/reset-password`|重置密码|`resetpasswordUser`|
|POST|`/system/users/{id}/freeze`|冻结用户|`freezeUser`|
|POST|`/system/users/{id}/resume`|恢复用户|`resumeUser`|
|POST|`/system/users/{id}/disable`|停用用户|`disableUser`|

## Role - 角色权限

|方法|路径|说明|operationId|
|---|---|---|---|
|GET|`/system/roles`|Role列表|`listRole`|
|POST|`/system/roles`|新增Role|`createRole`|
|GET|`/system/roles/{id}`|Role详情|`getRole`|
|PUT|`/system/roles/{id}`|修改Role|`updateRole`|
|GET|`/system/permissions/tree`|权限树|`getPermissionTree`|

## Dictionary - 字典

|方法|路径|说明|operationId|
|---|---|---|---|
|GET|`/system/dictionaries`|Dictionary列表|`listDictionary`|
|POST|`/system/dictionaries`|新增Dictionary|`createDictionary`|
|GET|`/system/dictionaries/{id}`|Dictionary详情|`getDictionary`|
|PUT|`/system/dictionaries/{id}`|修改Dictionary|`updateDictionary`|
|GET|`/system/dictionaries/{id}/items`|字典项列表|`listDictionaryItems`|
|POST|`/system/dictionaries/{id}/items`|新增字典项|`createDictionaryItem`|
|PUT|`/system/dictionary-items/{id}`|修改字典项|`updateDictionaryItem`|
|POST|`/system/dictionary-items/{id}/enable`|启用字典项|`enableDictionaryItem`|
|POST|`/system/dictionary-items/{id}/disable`|停用字典项|`disableDictionaryItem`|

## Parameter - 参数

|方法|路径|说明|operationId|
|---|---|---|---|
|GET|`/system/parameters`|Parameter列表|`listParameter`|
|POST|`/system/parameters`|新增Parameter|`createParameter`|
|GET|`/system/parameters/{id}`|Parameter详情|`getParameter`|
|PUT|`/system/parameters/{id}`|修改Parameter|`updateParameter`|

## Audit - 审计

|方法|路径|说明|operationId|
|---|---|---|---|
|GET|`/system/login-logs`|登录日志|`listLoginLogs`|
|GET|`/system/operation-logs`|操作审计|`listAuditLogs`|
