# Viewer 总览

## 文档定位

本文描述公开查看页只读接口的能力边界、委托关系与兼容约束，用于帮助读者快速理解 viewer 资源为何仍保留独立命名空间与只读语义。

## 能力边界

viewer 接口统一挂载在 `/api/**` 下，面向公开查看页提供只读数据，不提供后台写操作。

当前覆盖资源：

- 团队列表
- 排班列表与排班详情
- 人员列表与人员详情
- 班次编码列表

遗留兼容说明：viewer 侧 `role-group` 接口已废弃，仅保留历史说明文档。

## 只读边界图

```mermaid
graph LR
    UI[Public Viewer UI] --> API[/api/** viewer APIs]
    API --> SERVICE[Viewer / Adapter Services]
    SERVICE --> WS[Workspace-related data adaptation]
    WS --> DB[(PostgreSQL)]

    API -.不开放写能力.-> WRITE[/api/workspace/**]
```

## 契约与源码入口

| 类型 | 位置 |
|---|---|
| OpenAPI 聚合入口 | [api/openapi.yaml](../../../api/openapi.yaml) |
| viewer 路径目录 | [api/paths/viewer](../../../api/paths/viewer) |
| Controller 目录 | [src/main/java/com/support/server/supportrosterserver/controller](../../../src/main/java/com/support/server/supportrosterserver/controller) |
| Service 目录 | [src/main/java/com/support/server/supportrosterserver/service](../../../src/main/java/com/support/server/supportrosterserver/service) |
| DTO 目录 | [src/main/java/com/support/server/supportrosterserver/dto](../../../src/main/java/com/support/server/supportrosterserver/dto) |

## 兼容性约束

- viewer 接口继续保留旧路由，不迁移到 `/api/workspace/**`。
- 当前 viewer 数据已不再依赖运行时 Excel 内存仓库，而是通过 service 层从数据库与 workspace 相关数据适配输出。
- 即使内部复用 workspace 相关 service，也必须保持只读语义。

## 继续阅读

- [teams.md](./teams.md)
- [shifts.md](./shifts.md)
- [staff.md](./staff.md)
- [shift-codes.md](./shift-codes.md)
- [role-groups.md](./role-groups.md)

## 维护提示

- 若 viewer 新增资源，应同时更新本页、`viewer/_index.md` 与 OpenAPI viewer 路径目录。
- 若某字段源自 workspace 适配层，需显式标注委托关系，避免被误读为独立数据源。

