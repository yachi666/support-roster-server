# Workspace Staff

## 资源范围

- `GET /api/workspace/staff`
- `GET /api/workspace/staff/{id}`
- `POST /api/workspace/staff`
- `PUT /api/workspace/staff/{id}`
- `DELETE /api/workspace/staff/{id}`

controller：`WorkspaceStaffController`

## 对应 OpenAPI 契约

- 路径文件：[api/paths/workspace/staff.yaml](../../../api/paths/workspace/staff.yaml)
- 聚合入口：[api/openapi.yaml](../../../api/openapi.yaml)

## 对应源码

- Controller：[WorkspaceStaffController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceStaffController.java)
- Service：[WorkspaceStaffService.java](../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceStaffService.java)
- DTO：[WorkspaceStaffDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceStaffDto.java)
- 写入请求：[WorkspaceStaffUpsertRequest.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceStaffUpsertRequest.java)

## 查询语义

- 列表接口支持可选参数 `keyword`。
- 列表返回元素类型为 `WorkspaceStaffDto`。
- 详情接口按主键返回单个 `WorkspaceStaffDto`。

## 写入语义

写接口统一使用 `WorkspaceStaffUpsertRequest`，核心字段包括：

- `staffCode`
- `name`
- `teamId`
- `email`、`phone`、`slack`
- `region`、`timezone`
- `roleName`、`status`
- `notes`

## 资源约束

- `staffCode`、`name`、`teamId` 为必填字段。
- 人员必须挂靠已存在的团队。
- 删除人员时，应确保相关排班或引用关系按服务层约定被一并处理或阻止删除。

## 返回视图

`WorkspaceStaffDto` 在列表与详情中同时承担展示模型，除人员基础信息外，还会携带：

- `teamId`
- `teamCode`
- `teamName`
- `rosterTags`

这些字段用于后台表格与筛选显示，不应视为单独资源的主数据写入口。

## 请求字段与 DTO 字段映射

### 请求字段

| 请求字段 | Request DTO | Response DTO 字段 | 必填 | 说明 |
|------|------|------|------|------|
| `staffCode` | `WorkspaceStaffUpsertRequest.staffCode` | `staffCode` | 是 | 人员编码 |
| `name` | `WorkspaceStaffUpsertRequest.name` | `name` | 是 | 人员姓名 |
| `email` | `WorkspaceStaffUpsertRequest.email` | `email` | 否 | 邮箱 |
| `phone` | `WorkspaceStaffUpsertRequest.phone` | `phone` | 否 | 电话 |
| `slack` | `WorkspaceStaffUpsertRequest.slack` | `slack` | 否 | Slack |
| `region` | `WorkspaceStaffUpsertRequest.region` | `region` | 否 | 区域 |
| `timezone` | `WorkspaceStaffUpsertRequest.timezone` | `timezone` | 否 | 时区 |
| `roleName` | `WorkspaceStaffUpsertRequest.roleName` | `roleName` | 否 | 展示角色名 |
| `teamId` | `WorkspaceStaffUpsertRequest.teamId` | `teamId` | 是 | 团队主键 |
| `status` | `WorkspaceStaffUpsertRequest.status` | `status` | 否 | 状态 |
| `avatar` | `WorkspaceStaffUpsertRequest.avatar` | `avatar` | 否 | 历史兼容字段，服务端返回时改为基于 `staffCode` 实时拼接 |
| `notes` | `WorkspaceStaffUpsertRequest.notes` | `notes` | 否 | 备注 |

### 查询参数与路径字段

| 接口 | 输入位置 | 字段 | 控制器参数 | 必填 | 说明 |
|------|------|------|------|------|------|
| `GET /api/workspace/staff` | query | `keyword` | `String keyword` | 否 | 关键字筛选 |
| `GET/PUT/DELETE /api/workspace/staff/{id}` | path | `id` | `Long id` | 是 | 人员主键 |

### 响应 DTO 字段

| DTO 字段 | OpenAPI 字段 | 说明 |
|------|------|------|
| `id` | `id` | 主键 |
| `staffCode` | `staffCode` | 人员编码 |
| `name` | `name` | 姓名 |
| `email` | `email` | 邮箱 |
| `phone` | `phone` | 电话 |
| `slack` | `slack` | Slack |
| `region` | `region` | 区域 |
| `timezone` | `timezone` | 时区 |
| `roleName` | `roleName` | 角色名 |
| `teamId` | `teamId` | 所属团队主键 |
| `teamCode` | `teamCode` | 所属团队编码 |
| `teamName` | `teamName` | 所属团队名称 |
| `status` | `status` | 状态 |
| `avatar` | `avatar` | 基于 `staffCode` 实时拼接的头像 URL |
| `notes` | `notes` | 备注 |
| `rosterTags` | `rosterTags` | 排班标签列表 |