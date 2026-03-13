# Workspace Teams

## 资源范围

- `GET /api/workspace/teams`
- `GET /api/workspace/teams/{id}`
- `POST /api/workspace/teams`
- `PUT /api/workspace/teams/{id}`
- `DELETE /api/workspace/teams/{id}`

controller：`WorkspaceTeamController`

## 对应 OpenAPI 契约

- 路径文件：[api/paths/workspace/teams.yaml](../../../api/paths/workspace/teams.yaml)
- 聚合入口：[api/openapi.yaml](../../../api/openapi.yaml)

## 对应源码

- Controller：[WorkspaceTeamController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceTeamController.java)
- Service：[WorkspaceTeamService.java](../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceTeamService.java)
- DTO：[WorkspaceTeamDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceTeamDto.java)
- 写入请求：[WorkspaceTeamUpsertRequest.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceTeamUpsertRequest.java)

## 数据职责

团队资源承载：

- `teamCode`
- `name`
- `color`
- `displayOrder`
- `visible`
- `description`
- `roleGroups`

## 写入语义

写接口统一使用 `WorkspaceTeamUpsertRequest`，其中 `roleGroupIds` 用于维护团队与角色组的映射关系。

## 资源约束

- 团队基础信息与角色组映射应在一次写请求中保持一致。
- `displayOrder` 用于前端展示排序，不应复用为业务优先级含义。
- `visible` 为展示控制字段，不等同于逻辑删除。

## 关联影响

- 团队映射会影响排班分组展示。
- 角色组与团队关系也是校验中心判断“角色组 / 团队映射缺失”的依据之一。

## 请求字段与 DTO 字段映射

### 请求字段

| 请求字段 | Request DTO | Response DTO 字段 | 必填 | 说明 |
|------|------|------|------|------|
| `teamCode` | `WorkspaceTeamUpsertRequest.teamCode` | `teamCode` | 是 | 团队编码 |
| `name` | `WorkspaceTeamUpsertRequest.name` | `name` | 是 | 团队名称 |
| `color` | `WorkspaceTeamUpsertRequest.color` | `color` | 是 | 展示色 |
| `displayOrder` | `WorkspaceTeamUpsertRequest.displayOrder` | `displayOrder` | 是 | 排序 |
| `visible` | `WorkspaceTeamUpsertRequest.visible` | `visible` | 是 | 是否显示 |
| `description` | `WorkspaceTeamUpsertRequest.description` | `description` | 否 | 描述 |
| `roleGroupIds` | `WorkspaceTeamUpsertRequest.roleGroupIds` | `roleGroups` | 是 | 角色组映射列表 |

### 路径字段

| 接口 | 输入位置 | 字段 | 控制器参数 | 必填 | 说明 |
|------|------|------|------|------|------|
| `GET/PUT/DELETE /api/workspace/teams/{id}` | path | `id` | `Long id` | 是 | 团队主键 |

### 响应 DTO 字段

| DTO 字段 | OpenAPI 字段 | 说明 |
|------|------|------|
| `id` | `id` | 主键 |
| `teamCode` | `teamCode` | 团队编码 |
| `name` | `name` | 名称 |
| `color` | `color` | 颜色 |
| `displayOrder` | `displayOrder` | 排序 |
| `visible` | `visible` | 是否显示 |
| `description` | `description` | 描述 |
| `roleGroups` | `roleGroups` | 角色组对象列表 |