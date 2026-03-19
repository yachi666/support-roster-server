# Viewer Role Groups (Deprecated)

## 当前状态

- 该文档仅保留为历史兼容说明。
- 当前 viewer 主链路已不再使用独立 role-group 只读接口。
- `GET /api/role-groups` 及对应 controller/service/dto 已移除。

## 历史实现参考

- 历史路径文件：[api/paths/viewer/role-groups.yaml](../../../api/paths/viewer/role-groups.yaml)
- 历史聚合入口：[api/openapi.yaml](../../../api/openapi.yaml)

## 迁移说明

- viewer 团队与人员数据均已通过 team 字段输出。
- 若需要分组信息，应优先读取团队接口或人员接口中的团队相关字段。

## 替代文档

- 团队资源见 [teams.md](./teams.md)
- 人员资源见 [staff.md](./staff.md)
- 排班资源见 [shifts.md](./shifts.md)
