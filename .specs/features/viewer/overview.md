# Viewer Overview

## 能力边界

viewer 接口统一挂载在 `/api/**` 下，面向公开查看页提供只读数据，不提供后台写操作。

当前覆盖资源：

- 团队列表
- 排班列表与排班详情
- 人员列表与人员详情
- 角色组列表
- 班次编码列表

## 对应 OpenAPI 契约

- 聚合入口：[api/openapi.yaml](../../../api/openapi.yaml)
- 结构说明：[.specs/api/openapi-layout.md](../../api/openapi-layout.md)
- viewer 路径目录：[api/paths/viewer](../../../api/paths/viewer)

## 源码入口

- controller 目录：[src/main/java/com/support/server/supportrosterserver/controller](../../../src/main/java/com/support/server/supportrosterserver/controller)
- service 目录：[src/main/java/com/support/server/supportrosterserver/service](../../../src/main/java/com/support/server/supportrosterserver/service)
- DTO 目录：[src/main/java/com/support/server/supportrosterserver/dto](../../../src/main/java/com/support/server/supportrosterserver/dto)

## 兼容性约束

- viewer 接口继续保留旧路由，不迁移到 `/api/workspace/**`。
- 当前 viewer 数据已不再依赖运行时 Excel 内存仓库，而是通过 service 层从数据库与 workspace 相关数据适配输出。
- viewer 接口保持只读语义，即使内部复用 workspace service，也不暴露管理端写能力。

## 资源文档

- [teams.md](./teams.md) 对应 [api/paths/viewer/teams.yaml](../../../api/paths/viewer/teams.yaml)
- [shifts.md](./shifts.md) 对应 [api/paths/viewer/shifts.yaml](../../../api/paths/viewer/shifts.yaml)
- [staff.md](./staff.md) 对应 [api/paths/viewer/staff.yaml](../../../api/paths/viewer/staff.yaml)
- [role-groups.md](./role-groups.md) 对应 [api/paths/viewer/role-groups.yaml](../../../api/paths/viewer/role-groups.yaml)
- [shift-codes.md](./shift-codes.md) 对应 [api/paths/viewer/shift-codes.yaml](../../../api/paths/viewer/shift-codes.yaml)
