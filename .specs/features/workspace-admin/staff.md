# 工作台后台 · 人员目录（Workspace Staff）

## 文档定位

本文描述 `/api/workspace/staff` 人员资源的查询、详情、创建、更新与删除约定。

## 资源范围

- `GET /api/workspace/staff`
- `GET /api/workspace/staff/{id}`
- `POST /api/workspace/staff`
- `PUT /api/workspace/staff/{id}`
- `DELETE /api/workspace/staff/{id}`
- Controller：`WorkspaceStaffController`

## 契约与源码映射

| 类型 | 入口 |
|---|---|
| OpenAPI 路径 | [../../../api/paths/workspace/staff.yaml](../../../api/paths/workspace/staff.yaml) |
| OpenAPI 聚合入口 | [../../../api/openapi.yaml](../../../api/openapi.yaml) |
| Controller | [WorkspaceStaffController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceStaffController.java) |
| Service | [WorkspaceStaffService.java](../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceStaffService.java) |
| 响应 DTO | [WorkspaceStaffDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceStaffDto.java) |
| 写入请求 | [WorkspaceStaffUpsertRequest.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceStaffUpsertRequest.java) |

## 能力边界

- 列表接口支持可选 `keyword` 关键字筛选。
- 列表与详情统一返回 `WorkspaceStaffDto`。
- 写接口统一使用 `WorkspaceStaffUpsertRequest`。

## 核心规则

- `staffId`、`name`、`teamId` 为必填字段。
- 人员必须挂靠已存在团队。
- 删除人员时，应按服务层约定处理排班或引用关系，不能留下悬挂引用。
- `avatar` 为历史兼容入参；服务端返回时使用基于 `staffId` 实时拼接的头像 URL。
- `rosterTags` 属于展示聚合字段，不是独立主数据写入口。

## 返回视图

`WorkspaceStaffDto` 同时承担列表与详情模型，除基础信息外，还会携带：

- `teamId`
- `teamName`
- `rosterTags`

## 字段映射

### 请求字段

| 请求字段 | Request DTO | Response DTO 字段 | 必填 | 说明 |
|---|---|---|---|---|
| `staffId` | `WorkspaceStaffUpsertRequest.staffId` | `staffId` | 是 | 人员编码 |
| `name` | `WorkspaceStaffUpsertRequest.name` | `name` | 是 | 人员姓名 |
| `email` | `WorkspaceStaffUpsertRequest.email` | `email` | 否 | 邮箱 |
| `phone` | `WorkspaceStaffUpsertRequest.phone` | `phone` | 否 | 电话 |
| `slack` | `WorkspaceStaffUpsertRequest.slack` | `slack` | 否 | Slack |
| `region` | `WorkspaceStaffUpsertRequest.region` | `region` | 否 | 区域 |
| `timezone` | `WorkspaceStaffUpsertRequest.timezone` | `timezone` | 否 | 时区 |
| `roleName` | `WorkspaceStaffUpsertRequest.roleName` | `roleName` | 否 | 展示角色名 |
| `teamId` | `WorkspaceStaffUpsertRequest.teamId` | `teamId` | 是 | 所属团队 |
| `status` | `WorkspaceStaffUpsertRequest.status` | `status` | 否 | 状态 |
| `avatar` | `WorkspaceStaffUpsertRequest.avatar` | `avatar` | 否 | 历史兼容字段 |
| `notes` | `WorkspaceStaffUpsertRequest.notes` | `notes` | 否 | 备注 |

### 查询参数与路径字段

| 接口 | 输入位置 | 字段 | 控制器参数 | 必填 | 说明 |
|---|---|---|---|---|---|
| `GET /api/workspace/staff` | query | `keyword` | `String keyword` | 否 | 关键字筛选 |
| `GET/PUT/DELETE /api/workspace/staff/{id}` | path | `id` | `Long id` | 是 | 人员主键 |

### 响应 DTO 字段

| DTO 字段 | OpenAPI 字段 | 说明 |
|---|---|---|
| `id` | `id` | 主键 |
| `staffId` | `staffId` | 人员编码 |
| `name` | `name` | 姓名 |
| `email` | `email` | 邮箱 |
| `phone` | `phone` | 电话 |
| `slack` | `slack` | Slack |
| `region` | `region` | 区域 |
| `timezone` | `timezone` | 时区 |
| `roleName` | `roleName` | 角色名 |
| `teamId` | `teamId` | 所属团队主键 |
| `teamName` | `teamName` | 所属团队名称 |
| `status` | `status` | 状态 |
| `avatar` | `avatar` | 实时拼接头像 URL |
| `notes` | `notes` | 备注 |
| `rosterTags` | `rosterTags` | 排班标签列表 |

## 维护提示

- 人员主数据变化会同时影响 viewer staff 输出与月度排班分组显示。
- 若新增展示字段，需确认它属于主数据字段还是聚合展示字段。
