# Workspace Admin Overview

## 能力边界

workspace 后台能力统一挂载在 `/api/workspace/**`，覆盖以下资源：

- 总览看板
- 角色组字典
- 人员目录
- 班次定义
- 团队映射
- 月度排班
- 校验中心
- Excel 导入导出

## 对应 OpenAPI 契约

- 聚合入口：[api/openapi.yaml](../../../api/openapi.yaml)
- 结构说明：[.specs/api/openapi-layout.md](../../api/openapi-layout.md)
- workspace 路径目录：[api/paths/workspace](../../../api/paths/workspace)

## 源码入口

- controller 目录：[src/main/java/com/support/server/supportrosterserver/controller/workspace](../../../src/main/java/com/support/server/supportrosterserver/controller/workspace)
- service 目录：[src/main/java/com/support/server/supportrosterserver/service/workspace](../../../src/main/java/com/support/server/supportrosterserver/service/workspace)
- DTO 目录：[src/main/java/com/support/server/supportrosterserver/dto/workspace](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace)

## 技术方案

### 持久化栈

- 数据库：PostgreSQL
- ORM：MyBatis-Plus
- 主键：雪花算法
- 审计字段：所有核心表均包含 `create_time`、`update_time`

### 初始化方式

- 建表 DDL 位于 `.specs/db/ddl/001_init_workspace_tables.sql`
- 本地空库启动时通过 `src/main/resources/schema.sql` 自动初始化

## 核心表

| 表名 | 说明 |
|------|------|
| `workspace_role_group` | 角色组字典，承载 code/name/category/region |
| `workspace_team` | 团队主表，承载展示色、排序、显示状态 |
| `workspace_team_role_group_rel` | 团队与角色组多对多映射 |
| `workspace_staff` | 人员主数据 |
| `workspace_shift_definition` | 班次定义 |
| `workspace_roster_assignment` | 排班事实表，按人 + 日期 + 班次编码存储 |
| `workspace_import_batch` | 导入批次 |
| `workspace_import_record` | 导入预览记录 |
| `workspace_import_issue` | 导入与校验问题 |
| `workspace_operation_log` | 后台关键操作日志 |

## 跨资源约束

- 月排班不保存为整月 JSON，而是按“员工 + 自然日 + 班次编码”写入事实表。
- 校验结果既用于导入预览，也用于校验中心接口返回。
- viewer 只读接口继续保留，通过服务层从数据库适配输出，不与 workspace 写接口合并。

## 资源文档

- [dashboard-overview.md](./dashboard-overview.md) 对应 [api/paths/workspace/overview.yaml](../../../api/paths/workspace/overview.yaml)
- [role-groups.md](./role-groups.md) 对应 [api/paths/workspace/role-groups.yaml](../../../api/paths/workspace/role-groups.yaml)
- [staff.md](./staff.md) 对应 [api/paths/workspace/staff.yaml](../../../api/paths/workspace/staff.yaml)
- [shift-definitions.md](./shift-definitions.md) 对应 [api/paths/workspace/shift-definitions.yaml](../../../api/paths/workspace/shift-definitions.yaml)
- [teams.md](./teams.md) 对应 [api/paths/workspace/teams.yaml](../../../api/paths/workspace/teams.yaml)
- [roster.md](./roster.md) 对应 [api/paths/workspace/roster.yaml](../../../api/paths/workspace/roster.yaml)
- [validation.md](./validation.md) 对应 [api/paths/workspace/validation.yaml](../../../api/paths/workspace/validation.yaml)
- [import-export.md](./import-export.md) 对应 [api/paths/workspace/import-export.yaml](../../../api/paths/workspace/import-export.yaml)