# Workspace Admin Backend 总览

## 文档定位

本文档保留为 workspace 管理后台专题的总览入口。资源级规范已经拆分到 `features/workspace-admin/` 目录，本文不再堆叠字段明细与逐接口表格。

## 能力范围

workspace 后台接口统一挂载在 `/api/workspace/**`，覆盖：

- 工作台首页聚合
- 人员目录维护
- 班次定义维护
- 团队管理
- 月度排班查询与保存
- 校验中心
- Excel 导入预览、应用、导出与模板下载

## 分册导航

| 类型 | 文档 |
|---|---|
| 总览与共性约束 | [workspace-admin/_index.md](./workspace-admin/_index.md)、[workspace-admin/overview.md](./workspace-admin/overview.md) |
| 聚合资源 | [workspace-admin/dashboard-overview.md](./workspace-admin/dashboard-overview.md) |
| 主数据资源 | [workspace-admin/staff.md](./workspace-admin/staff.md)、[workspace-admin/shift-definitions.md](./workspace-admin/shift-definitions.md)、[workspace-admin/teams.md](./workspace-admin/teams.md) |
| 过程型资源 | [workspace-admin/roster.md](./workspace-admin/roster.md)、[workspace-admin/validation.md](./workspace-admin/validation.md)、[workspace-admin/import-export.md](./workspace-admin/import-export.md) |
| 历史兼容 | [workspace-admin/role-groups.md](./workspace-admin/role-groups.md) |

## 与 viewer 的边界

为保持公开查看页兼容，以下 viewer 只读接口继续保留：

- `/api/teams`
- `/api/shifts`
- `/api/shifts/{id}`
- `/api/staff`
- `/api/staff/{id}`
- `/api/shift-codes`

这些接口继续通过服务层做只读适配，不与 workspace 写接口合并。

## 维护提示

- 需要资源级字段映射时，请直接进入 `workspace-admin/` 子目录，不再回写到本总览文档。
- 若后台主资源边界发生变化，应同时更新本文与 [workspace-admin/overview.md](./workspace-admin/overview.md)。
