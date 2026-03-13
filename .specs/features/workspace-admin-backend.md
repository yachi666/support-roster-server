# Workspace Admin Backend

## 范围

本专题文档保留为 workspace 管理后台能力的总览入口。更细的资源级规范已拆分到 `features/workspace-admin/` 目录，避免继续将所有后台资源堆叠在单个文档中。

## 能力范围

workspace 后台接口统一挂载在 `/api/workspace/**` 下，覆盖：

- 总览看板
- 角色组字典
- 人员目录
- 班次定义
- 团队映射
- 月度排班
- 校验中心
- Excel 导入导出

## 文档导航

### 总览与共性约束

- `workspace-admin/_index.md`
- `workspace-admin/overview.md`

### 资源级规范

- `workspace-admin/dashboard-overview.md`
- `workspace-admin/role-groups.md`
- `workspace-admin/staff.md`
- `workspace-admin/shift-definitions.md`
- `workspace-admin/teams.md`
- `workspace-admin/roster.md`
- `workspace-admin/validation.md`
- `workspace-admin/import-export.md`

## 兼容性约定

为避免影响现有公开查看页，旧 viewer 接口继续保留：

- `/api/teams`
- `/api/shifts`
- `/api/shifts/{id}`
- `/api/staff`
- `/api/staff/{id}`
- `/api/role-groups`
- `/api/shift-codes`

这些接口继续通过服务层从数据库适配输出，不与 workspace 写接口合并。

## 测试约定

- 以单元测试覆盖核心聚合与校验逻辑。
- 当前仓库不保留依赖真实数据库启动的默认 Spring 上下文测试。
- 本地联调依赖 `application.yml` 中的 PostgreSQL 配置。