# 工作台后台 · Role Group（已废弃）

## 文档定位

本文仅保留为历史兼容说明。当前 workspace 管理主模型已统一使用 `team`，不再以 `role group` 作为独立后台资源。

## 当前状态

- 已移除 `GET /api/workspace/role-groups` 及对应 controller / service / dto。
- 人员、班次定义、导入导出、月排班现在统一使用 `teamId` / `teamName`。

## 历史参考

| 类型 | 入口 |
|---|---|
| 历史路径文件 | [../../../api/paths/workspace/role-groups.yaml](../../../api/paths/workspace/role-groups.yaml) |
| OpenAPI 聚合入口 | [../../../api/openapi.yaml](../../../api/openapi.yaml) |

## 迁移后的职责归属

原由 `role group` 承载的分组语义，现统一收敛到团队资源：

- `teamCode`、`name`：业务标识与展示名称
- `color`、`displayOrder`：展示配置
- `visible`、`description`：可见性与补充说明

## 替代入口

- 团队资源见 [teams.md](./teams.md)
- 人员目录见 [staff.md](./staff.md)
- 班次定义见 [shift-definitions.md](./shift-definitions.md)
- 导入导出见 [import-export.md](./import-export.md)
