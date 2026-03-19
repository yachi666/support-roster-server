# Workspace Role Groups (Deprecated)

## 当前状态

- 该文档仅保留为历史兼容说明。
- 当前 workspace 管理主模型已统一使用 `team`，不再以 role group 作为独立后台资源。
- 已移除 `GET /api/workspace/role-groups` 及对应 controller/service/dto。

## 历史实现参考

- 历史路径文件：[api/paths/workspace/role-groups.yaml](../../../api/paths/workspace/role-groups.yaml)
- 历史聚合入口：[api/openapi.yaml](../../../api/openapi.yaml)

## 已废弃能力

- 原先的 role group 读取能力已被移除。
- 人员、班次定义、导入导出、月排班现在全部直接使用 `teamId` / `teamName`。

## 迁移后的职责归属

原由角色组承载的业务分组语义，现统一收敛到团队资源：

- `teamCode`、`name`：业务标识与展示名称
- `color`、`displayOrder`：展示配置
- `visible`、`description`：可见性与补充说明

## 兼容说明

- 数据库中可能仍保留历史 `role_group_id` 列或字典表，用于兼容旧数据。
- 这些字段不再作为 workspace API 的输入输出契约。
- 新增或编辑数据时，应始终以 `team` 作为唯一分组维度。

## 替代文档

- 团队主资源说明见 [teams.md](./teams.md)
- 人员目录说明见 [staff.md](./staff.md)
- 班次定义说明见 [shift-definitions.md](./shift-definitions.md)
- 导入导出说明见 [import-export.md](./import-export.md)