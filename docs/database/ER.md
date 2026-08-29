# 核心ER关系

```mermaid
erDiagram
    SYS_USER ||--o{ CUSTOMER : owns
    CUSTOMER ||--o{ CUSTOMER_AUTH : submits
    CUSTOMER ||--o{ CUSTOMER_ACCOUNT : has
    CUSTOMER ||--o{ BIZ_ORDER : places
    BIZ_ORDER ||--|{ BIZ_ORDER_ITEM : contains

    PRD_PRODUCT ||--o{ PRD_PACKAGE : has
    PRD_PRODUCT ||--o{ PRD_RESOURCE_STRATEGY : uses
    PRD_RESOURCE_STRATEGY }o--o{ RES_POOL : selects

    RES_CENTOS ||--o{ RES_ROS : contains
    RES_ROS ||--o{ RES_LINE : contains
    RES_LINE ||--o{ RES_IP : produces
    RES_SUPPLIER ||--o{ RES_IP : supplies
    RES_POOL }o--o{ RES_IP : groups

    CUSTOMER ||--o{ SVC_INSTANCE : owns
    BIZ_ORDER_ITEM ||--o{ SVC_INSTANCE : provisions
    PRD_PRODUCT ||--o{ SVC_INSTANCE : defines
    PRD_PACKAGE ||--o{ SVC_INSTANCE : entitles
    PRD_RESOURCE_STRATEGY ||--o{ SVC_INSTANCE : resolves

    SVC_INSTANCE ||--o{ SVC_RESOURCE_BINDING : binds
    SVC_INSTANCE ||--o{ SVC_CREDENTIAL : authenticates
    SVC_INSTANCE ||--o{ SVC_WHITELIST : allows
    SVC_INSTANCE ||--o{ SVC_CHANGE : changes
    SVC_INSTANCE ||--o{ SVC_RELEASE_RECORD : releases
    SVC_INSTANCE ||--o{ RES_EXCLUSIVE_ALLOCATION : occupies

    ALARM_RULE ||--o{ ALARM_EVENT : triggers
    ALARM_EVENT ||--o{ ALARM_PROCESS_RECORD : processed_by
    ALARM_EVENT ||--o{ ALARM_NOTIFICATION : notifies
```

## 关键说明

- `RES_POOL <-> RES_IP` 为多对多；一个IP允许进入多个逻辑池。
- `SVC_RESOURCE_BINDING` 使用 `resource_type + resource_id` 表达多种资源绑定，因此是业务层多态关联，不对每种资源建立数据库外键。
- `MON_OBJECT_STATUS`、`MON_METRIC_SAMPLE`、`ALARM_EVENT` 的监控对象同样采用 `object_type + object_id` 多态引用。
- 产品的五类专项配置分别保存，避免把核心结构完全埋入 JSON。
- 服务实例保存产品、套餐和策略快照，保证历史可追溯。
