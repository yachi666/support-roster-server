# Workspace Roster

## 资源范围

- `GET /api/workspace/roster`
- `POST /api/workspace/roster/save`

controller：`WorkspaceRosterController`

## 对应 OpenAPI 契约

- 路径文件：[api/paths/workspace/roster.yaml](../../../api/paths/workspace/roster.yaml)
- 聚合入口：[api/openapi.yaml](../../../api/openapi.yaml)

## 对应源码

- Controller：[WorkspaceRosterController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceRosterController.java)
- Service：[WorkspaceRosterService.java](../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceRosterService.java)
- 响应 DTO：[WorkspaceMonthlyRosterResponse.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceMonthlyRosterResponse.java)
- 写入请求：[WorkspaceRosterSaveRequest.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceRosterSaveRequest.java)
- 单元格请求：[WorkspaceRosterCellUpdateRequest.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceRosterCellUpdateRequest.java)
- 子 DTO：[WorkspaceRosterGroupDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceRosterGroupDto.java)、[WorkspaceRosterPersonDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceRosterPersonDto.java)

## 查询语义

- 查询参数：`year`、`month`，均为可选。
- 返回模型：`WorkspaceMonthlyRosterResponse`
- 响应按团队分组输出 `groups`，每组包含人员和按日排班 `schedule`。
- 响应额外返回 `shiftDetailsByTeam`，供前端在 Monthly Roster 单元格悬停时展示班次 meaning、时间段、时区与跨天信息。

## 保存语义

- 保存接口使用 `WorkspaceRosterSaveRequest`。
- 请求体包含 `year`、`month` 与 `updates`。
- 每条 `updates` 元素对应一个“员工 + 日期单元格”的增量修改。

## 存储约定

- 排班事实按“员工 + 自然日 + 班次编码”粒度保存。
- 不使用整月 JSON 覆盖式存储。
- 保存后返回最新月视图，而不是仅返回受影响单元格。

## 资源约束

- `updates` 中的 `staffId` 必须引用已存在人员。
- 所有对外返回的 Long 主键以字符串形式传输，避免浏览器对超大整数产生精度丢失；`updates[].staffId` 也按字符串传输，服务端再转换为 Long。
- `shiftCode` 应与有效班次定义一致，或符合清空单元格的服务层约定。
- 班次存在性校验基于“团队 + 班次定义关联关系”，而不是 `workspace_shift_definition.team_id` 单字段。
- 保存后产生的校验告警通过 `validationWarning` 或校验中心接口体现。

## 请求字段与 DTO 字段映射

### 查询参数

| 接口 | 输入位置 | 字段 | 控制器参数 | 必填 | 说明 |
|------|------|------|------|------|------|
| `GET /api/workspace/roster` | query | `year` | `Integer year` | 否 | 年份 |
| `GET /api/workspace/roster` | query | `month` | `Integer month` | 否 | 月份 |

### 保存请求字段

| 请求字段 | Request DTO | Response DTO 字段 | 必填 | 说明 |
|------|------|------|------|------|
| `year` | `WorkspaceRosterSaveRequest.year` | `year` | 是 | 年份 |
| `month` | `WorkspaceRosterSaveRequest.month` | `month` | 是 | 月份 |
| `updates` | `WorkspaceRosterSaveRequest.updates` | 无直接同名字段 | 是 | 单元格更新列表 |
| `updates[].staffId` | `WorkspaceRosterCellUpdateRequest.staffId` | `groups[].staff[].staffId` | 是 | 人员主键，JSON 传输时为字符串 |
| `updates[].day` | `WorkspaceRosterCellUpdateRequest.day` | `groups[].staff[].schedule` 的 key | 是 | 月内天数 |
| `updates[].shiftCode` | `WorkspaceRosterCellUpdateRequest.shiftCode` | `groups[].staff[].schedule` 的 value | 否 | 班次编码 |

### 响应 DTO 字段

| DTO 字段 | OpenAPI 字段 | 说明 |
|------|------|------|
| `year` | `year` | 年份 |
| `month` | `month` | 月份 |
| `groups` | `groups` | 团队分组列表，映射 `WorkspaceRosterGroupDto` |
| `shiftCodeOptions` | `shiftCodeOptions` | 可选班次编码 |
| `shiftCodeOptionsByTeam` | `shiftCodeOptionsByTeam` | 团队维度的可选班次编码 |
| `shiftDetailsByTeam` | `shiftDetailsByTeam` | 团队维度的班次详细元数据 |
| `validationWarning` | `validationWarning` | 校验提示 |

### 子 DTO 字段

| DTO | 字段 | OpenAPI 字段 | 说明 |
|------|------|------|------|
| `WorkspaceRosterGroupDto` | `teamId` | `groups[].teamId` | 团队主键，JSON 传输时为字符串 |
| `WorkspaceRosterGroupDto` | `teamName` | `groups[].teamName` | 团队名称 |
| `WorkspaceRosterGroupDto` | `color` | `groups[].color` | 团队颜色 |
| `WorkspaceRosterGroupDto` | `staff` | `groups[].staff` | 人员列表 |
| `WorkspaceRosterPersonDto` | `staffId` | `groups[].staff[].staffId` | 人员主键，JSON 传输时为字符串 |
| `WorkspaceRosterPersonDto` | `staffName` | `groups[].staff[].staffName` | 人员名称 |
| `WorkspaceRosterPersonDto` | `roleName` | `groups[].staff[].roleName` | 角色名 |
| `WorkspaceRosterPersonDto` | `schedule` | `groups[].staff[].schedule` | 按天映射的班次编码 |