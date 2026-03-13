# Workspace Dashboard Overview

## 资源范围

- 接口：`GET /api/workspace/overview`
- controller：`WorkspaceOverviewController`
- 输出 DTO：`WorkspaceOverviewResponse`

## 对应 OpenAPI 契约

- 路径文件：[api/paths/workspace/overview.yaml](../../../api/paths/workspace/overview.yaml)
- 聚合入口：[api/openapi.yaml](../../../api/openapi.yaml)

## 对应源码

- Controller：[WorkspaceOverviewController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceOverviewController.java)
- Service：[WorkspaceOverviewService.java](../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceOverviewService.java)
- DTO：[WorkspaceOverviewResponse.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceOverviewResponse.java)
- 子 DTO：[WorkspaceSummaryStatDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceSummaryStatDto.java)、[WorkspaceActivityDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceActivityDto.java)、[WorkspaceQuickActionDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceQuickActionDto.java)

## 返回结构

接口聚合三个展示区块：

- `stats`：顶部摘要指标，元素类型为 `WorkspaceSummaryStatDto`
- `activity`：近期活动，元素类型为 `WorkspaceActivityDto`
- `quickActions`：快捷操作入口，元素类型为 `WorkspaceQuickActionDto`

## 职责约束

- 该接口是聚合视图，不承担写操作。
- 聚合结果面向工作台首页，不应承载人员、团队、班次等资源的完整明细。
- 若首页需要新增卡片，应优先扩展现有聚合响应，而不是在首页并行拉取多个明细接口。

## 请求字段与 DTO 字段映射

### 请求字段

- 无请求体，无查询参数。

### 响应 DTO 字段

| DTO 字段 | OpenAPI 字段 | 说明 |
|------|------|------|
| `stats` | `stats` | 摘要指标列表，映射 `WorkspaceSummaryStatDto` |
| `activity` | `activity` | 最近活动列表，映射 `WorkspaceActivityDto` |
| `quickActions` | `quickActions` | 快捷操作列表，映射 `WorkspaceQuickActionDto` |

### 子 DTO 字段

| DTO | 字段 | OpenAPI 字段 | 说明 |
|------|------|------|------|
| `WorkspaceSummaryStatDto` | `label` | `stats[].label` | 指标名 |
| `WorkspaceSummaryStatDto` | `value` | `stats[].value` | 指标值 |
| `WorkspaceSummaryStatDto` | `trend` | `stats[].trend` | 趋势 |
| `WorkspaceSummaryStatDto` | `status` | `stats[].status` | 状态 |
| `WorkspaceSummaryStatDto` | `progress` | `stats[].progress` | 进度值 |
| `WorkspaceActivityDto` | `user` | `activity[].user` | 操作人 |
| `WorkspaceActivityDto` | `action` | `activity[].action` | 行为 |
| `WorkspaceActivityDto` | `time` | `activity[].time` | 时间文本 |
| `WorkspaceQuickActionDto` | `title` | `quickActions[].title` | 标题 |
| `WorkspaceQuickActionDto` | `subtitle` | `quickActions[].subtitle` | 副标题 |
| `WorkspaceQuickActionDto` | `variant` | `quickActions[].variant` | 样式变体 |
| `WorkspaceQuickActionDto` | `actionKey` | `quickActions[].actionKey` | 前端动作键 |