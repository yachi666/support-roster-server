# 工作台后台 · 团队资源（Workspace Teams）

## 文档定位

本文描述 `/api/workspace/teams` 团队资源的查询与 CRUD 规则。

## 资源范围

- `GET /api/workspace/teams`
- `GET /api/workspace/teams/{id}`
- `POST /api/workspace/teams`
- `PUT /api/workspace/teams/{id}`
- `DELETE /api/workspace/teams/{id}`
- Controller：`WorkspaceTeamController`

## 契约与源码映射

| 类型 | 入口 |
|---|---|
| OpenAPI 路径 | [../../../api/paths/workspace/teams.yaml](../../../api/paths/workspace/teams.yaml) |
| OpenAPI 聚合入口 | [../../../api/openapi.yaml](../../../api/openapi.yaml) |
| Controller | [WorkspaceTeamController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceTeamController.java) |
| Service | [WorkspaceTeamService.java](../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceTeamService.java) |
| 响应 DTO | [WorkspaceTeamDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceTeamDto.java) |
| 写入请求 | [WorkspaceTeamUpsertRequest.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceTeamUpsertRequest.java) |

## 能力边界

团队资源承载以下字段：

- `name`
- `color`
- `displayOrder`
- `visible`
- `description`

写接口仅维护团队自身属性，不再维护角色组映射关系。

## 核心规则

- `displayOrder` 仅用于前端展示排序，不应承载业务优先级含义。
- `visible` 为展示控制字段，不等同于逻辑删除。
- 团队变更会影响排班分组展示、导入映射与班次定义共享关系。

## 字段映射

### 请求字段

| 请求字段 | Request DTO | Response DTO 字段 | 必填 | 说明 |
|---|---|---|---|---|
| `name` | `WorkspaceTeamUpsertRequest.name` | `name` | 是 | 团队名称 |
| `color` | `WorkspaceTeamUpsertRequest.color` | `color` | 是 | 展示色 |
| `displayOrder` | `WorkspaceTeamUpsertRequest.displayOrder` | `displayOrder` | 是 | 排序 |
| `visible` | `WorkspaceTeamUpsertRequest.visible` | `visible` | 是 | 是否显示 |
| `description` | `WorkspaceTeamUpsertRequest.description` | `description` | 否 | 描述 |

### 路径字段

| 接口 | 输入位置 | 字段 | 控制器参数 | 必填 | 说明 |
|---|---|---|---|---|---|
| `GET/PUT/DELETE /api/workspace/teams/{id}` | path | `id` | `Long id` | 是 | 团队主键 |

### 响应 DTO 字段

| DTO 字段 | OpenAPI 字段 | 说明 |
|---|---|---|
| `id` | `id` | 主键 |
| `name` | `name` | 名称 |
| `color` | `color` | 颜色 |
| `displayOrder` | `displayOrder` | 排序 |
| `visible` | `visible` | 是否显示 |
| `description` | `description` | 描述 |

## 维护提示

- 团队是当前主分组维度；若再次引入 role-group 类资源，应明确其与 `team` 的关系，而不是隐式恢复旧模型。
- `name` 是团队唯一业务标识，服务端按“去首尾空格 + 大小写不敏感”执行唯一约束。
