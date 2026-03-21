# Viewer · Role Group（已废弃）

## 文档定位

本文仅保留为历史兼容说明。当前 viewer 主链路已不再使用独立 `role-group` 只读接口。

## 当前状态

- `GET /api/role-groups` 及对应 controller / service / dto 已移除。
- viewer 团队与人员数据均已通过 `team` 字段输出。

## 历史参考

| 类型 | 入口 |
|---|---|
| 历史路径文件 | [../../../api/paths/viewer/role-groups.yaml](../../../api/paths/viewer/role-groups.yaml) |
| OpenAPI 聚合入口 | [../../../api/openapi.yaml](../../../api/openapi.yaml) |

## 替代入口

- 团队资源见 [teams.md](./teams.md)
- 人员资源见 [staff.md](./staff.md)
- 排班资源见 [shifts.md](./shifts.md)
