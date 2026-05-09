# 工作台后台 · 校验中心（Workspace Validation）

## 文档定位

本文描述工作台校验资源的返回结构、规则来源、修复动作以及它与导入预览之间的复用关系。

## 资源范围

- 接口：`GET /api/workspace/validation`
- 接口：`POST /api/workspace/validation/{issueId}/remediation/preview`
- 接口：`POST /api/workspace/validation/{issueId}/remediation/apply`
- 参数：`year`、`month`、`summaryOnly`
- Controller：`WorkspaceValidationController`
- 输出模型：`WorkspaceValidationResponse`

## 契约与源码映射

| 类型 | 入口 |
|---|---|
| OpenAPI 路径 | [../../../api/paths/workspace/validation.yaml](../../../api/paths/workspace/validation.yaml) |
| OpenAPI 聚合入口 | [../../../api/openapi.yaml](../../../api/openapi.yaml) |
| Controller | [WorkspaceValidationController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceValidationController.java) |
| Service | [WorkspaceValidationService.java](../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceValidationService.java) |
| 响应 DTO | [WorkspaceValidationResponse.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceValidationResponse.java) |
| 子 DTO | [WorkspaceValidationSummaryDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceValidationSummaryDto.java)、[WorkspaceValidationIssueDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceValidationIssueDto.java) |

## 返回视图

| 字段 | 说明 |
|---|---|
| `summary` | 按 `high`、`medium`、`low` 汇总问题数量 |
| `topIssue` | 当前月份最高优先级的阻塞型（`high`）问题；若没有 `high` 问题则为空，供月排班页展示警告 |
| `issues` | 问题列表，每项包含 `severity`、`type`、`description`、`team`、`date`，并可按需带出精准跳转字段与 remediation 元数据 |

> `issues[].id` 在 JSON 中按字符串传输，避免浏览器对超大 `Long` 出现精度截断。
> 当 `summaryOnly=true` 时，接口仍返回 `summary` 与 `topIssue`，但 `issues` 可为空数组，供月排班页和侧边栏减少无谓的明细传输。`topIssue` 只代表 `high` 严重级别问题，不会把 medium / low 提升为主警告。

## 核心规则

当前版本至少覆盖以下问题类型：

- 无效班次编码
- 同一员工同一日期重复排班
- 主班次缺失
- 员工缺少 `timezone`
- 班次定义开始时间晚于或等于结束时间
- 排班引用了不存在的员工
- 排班引用了不存在的角色组 / 团队映射

## 复用关系

- 该接口与导入预览共享问题定义模型。
- 导入类问题仅聚合当前请求 `year` / `month` 对应导入批次中尚未 resolved 的记录，避免跨月份污染。
- 排班保存后的风险提示应与本接口保持一致口径。
- remediation preview/apply 通过 `actionKey + recordId` 锁定真实待清理记录，不能依赖可能重排的 synthetic issue id。

## 字段映射

### 请求字段

| 接口 | 输入位置 | 字段 | 控制器参数 | 必填 | 说明 |
|---|---|---|---|---|---|
| `GET /api/workspace/validation` | query | `year` | `Integer year` | 否 | 年份 |
| `GET /api/workspace/validation` | query | `month` | `Integer month` | 否 | 月份 |
| `GET /api/workspace/validation` | query | `summaryOnly` | `Boolean summaryOnly` | 否 | 是否仅返回摘要和最高优先级问题 |

### 响应 DTO 字段

| DTO 字段 | OpenAPI 字段 | 说明 |
|---|---|---|
| `summary` | `summary` | 汇总对象 |
| `topIssue` | `topIssue` | 最高优先级的 `high` 问题；无 `high` 时为空 |
| `issues` | `issues` | 问题列表 |

### 子 DTO 字段

| DTO | 字段 | OpenAPI 字段 | 说明 |
|---|---|---|---|
| `WorkspaceValidationSummaryDto` | `high` | `summary.high` | 高优先级问题数 |
| `WorkspaceValidationSummaryDto` | `medium` | `summary.medium` | 中优先级问题数 |
| `WorkspaceValidationSummaryDto` | `low` | `summary.low` | 低优先级问题数 |
| `WorkspaceValidationIssueDto` | `id` | `issues[].id` | 问题主键，JSON 中按字符串传输 |
| `WorkspaceValidationIssueDto` | `severity` | `issues[].severity` | 严重级别 |
| `WorkspaceValidationIssueDto` | `type` | `issues[].type` | 问题类型 |
| `WorkspaceValidationIssueDto` | `description` | `issues[].description` | 问题描述 |
| `WorkspaceValidationIssueDto` | `team` | `issues[].team` | 团队 |
| `WorkspaceValidationIssueDto` | `date` | `issues[].date` | 日期 |
| `WorkspaceValidationIssueDto` | `staffRecordId` | `issues[].staffRecordId` | 精准跳转到 staff 记录时使用 |
| `WorkspaceValidationIssueDto` | `shiftDefinitionId` | `issues[].shiftDefinitionId` | 精准跳转到班次定义时使用 |
| `WorkspaceValidationIssueDto` | `focusDay` | `issues[].focusDay` | 精准跳转到月排班格子时使用 |

## remediation 预览与应用

- remediation 预览响应除了 `recordIds` 外，还返回 `records[]`，每条记录包含 `recordId`、`title`、`subtitle`、`description`，供前端在确认弹窗中清晰展示删除范围。
- 当前系统清理类动作至少包括：
  - `delete_invalid_team_scope`
  - `delete_orphan_assignment`
- `delete_orphan_assignment` 预览/应用必须按真实 assignment 主键定位；即使 issue 列表因重新排序生成了新的 synthetic id，只要 `recordId` 仍存在，预览和应用都应继续命中同一条历史记录。

## 维护提示

- 新增校验规则时，应同时评估其是否影响导入预览、排班保存提示与问题严重级别。
- 若问题模型变更，应先保持导入预览与校验中心的一致性，再调整前端展示。
- 若新增轻量消费方，应优先复用 `summaryOnly=true` 模式，而不是为摘要场景重复拉取完整 `issues` 列表。
