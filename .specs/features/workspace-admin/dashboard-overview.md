# 工作台后台 · 首页总览（Workspace Dashboard Overview）

## 文档定位

本文描述 `GET /api/workspace/overview` 聚合接口，用于工作台首页展示摘要指标、活动流与快捷操作。

## 资源范围

- 接口：`GET /api/workspace/overview`
- Controller：`WorkspaceOverviewController`
- 输出 DTO：`WorkspaceOverviewResponse`

## 契约与源码映射

| 类型 | 入口 |
|---|---|
| OpenAPI 路径 | [../../../api/paths/workspace/overview.yaml](../../../api/paths/workspace/overview.yaml) |
| OpenAPI 聚合入口 | [../../../api/openapi.yaml](../../../api/openapi.yaml) |
| Controller | [WorkspaceOverviewController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceOverviewController.java) |
| Service | [WorkspaceOverviewService.java](../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceOverviewService.java) |
| 主 DTO | [WorkspaceOverviewResponse.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceOverviewResponse.java) |
| 子 DTO | [WorkspaceSummaryStatDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceSummaryStatDto.java)、[WorkspaceActivityDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceActivityDto.java)、[WorkspaceQuickActionDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceQuickActionDto.java) |

## 能力边界

- 本接口是首页聚合视图，不承担写操作。
- 聚合结果仅面向首页，不替代人员、团队、班次等明细资源。
- 若首页新增卡片，优先扩展现有聚合响应，而不是让前端并行拉取多个明细接口。

## 返回视图

| 字段 | 说明 |
|---|---|
| `stats` | 顶部摘要指标，元素类型为 `WorkspaceSummaryStatDto` |
| `activity` | 近期活动，元素类型为 `WorkspaceActivityDto` |
| `quickActions` | 快捷操作入口，元素类型为 `WorkspaceQuickActionDto` |

## 字段映射

### 请求字段

- 无请求体，无查询参数。

### 响应 DTO 字段

| DTO 字段 | OpenAPI 字段 | 说明 |
|---|---|---|
| `stats` | `stats` | 摘要指标列表 |
| `activity` | `activity` | 最近活动列表 |
| `quickActions` | `quickActions` | 快捷操作列表 |

### 子 DTO 字段

| DTO | 字段 | OpenAPI 字段 | 说明 |
|---|---|---|---|
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

## 维护提示

- 首页数据如果引入新的聚合区块，应保持“轻量摘要”定位，不扩展为明细接口。
- 任何字段调整都应同时更新 OpenAPI、DTO 与前端消费约定。
