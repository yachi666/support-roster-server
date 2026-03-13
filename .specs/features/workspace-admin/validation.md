# Workspace Validation

## 资源范围

- 接口：`GET /api/workspace/validation`
- controller：`WorkspaceValidationController`
- 参数：`year`、`month`
- 输出模型：`WorkspaceValidationResponse`

## 对应 OpenAPI 契约

- 路径文件：[api/paths/workspace/validation.yaml](../../../api/paths/workspace/validation.yaml)
- 聚合入口：[api/openapi.yaml](../../../api/openapi.yaml)

## 对应源码

- Controller：[WorkspaceValidationController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceValidationController.java)
- Service：[WorkspaceValidationService.java](../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceValidationService.java)
- 响应 DTO：[WorkspaceValidationResponse.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceValidationResponse.java)
- 子 DTO：[WorkspaceValidationSummaryDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceValidationSummaryDto.java)、[WorkspaceValidationIssueDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceValidationIssueDto.java)

## 返回结构

- `summary`：按 `high`、`medium`、`low` 汇总问题数量
- `issues`：问题列表，每项包含 `severity`、`type`、`description`、`team`、`date`
- `issues[].id` 与 resolve 接口中的 issue ID 在 JSON 中统一按字符串传输，避免浏览器对超大 Long 精度截断。

## 规则来源

当前版本至少覆盖以下规则：

- 无效班次编码
- 同一员工同一日期重复排班
- 主班次缺失
- 员工缺少 timezone
- 班次定义开始时间晚于或等于结束时间
- 排班引用了不存在的员工
- 排班引用了不存在的角色组 / 团队映射

## 复用关系

- 该接口与导入预览共享问题定义模型。
- 导入类问题只聚合当前请求 `year` / `month` 对应导入批次中尚未 resolved 的记录，避免跨月份批次污染当前月视图。
- 排班保存后的风险提示应与该接口的规则口径保持一致。

## 请求字段与 DTO 字段映射

### 请求字段

| 接口 | 输入位置 | 字段 | 控制器参数 | 必填 | 说明 |
|------|------|------|------|------|------|
| `GET /api/workspace/validation` | query | `year` | `Integer year` | 否 | 年份 |
| `GET /api/workspace/validation` | query | `month` | `Integer month` | 否 | 月份 |

### 响应 DTO 字段

| DTO 字段 | OpenAPI 字段 | 说明 |
|------|------|------|
| `summary` | `summary` | 汇总对象，映射 `WorkspaceValidationSummaryDto` |
| `issues` | `issues` | 问题列表，映射 `WorkspaceValidationIssueDto` |

### 子 DTO 字段

| DTO | 字段 | OpenAPI 字段 | 说明 |
|------|------|------|------|
| `WorkspaceValidationSummaryDto` | `high` | `summary.high` | 高优先级问题数 |
| `WorkspaceValidationSummaryDto` | `medium` | `summary.medium` | 中优先级问题数 |
| `WorkspaceValidationSummaryDto` | `low` | `summary.low` | 低优先级问题数 |
| `WorkspaceValidationIssueDto` | `id` | `issues[].id` | 问题主键，JSON 传输时为字符串 |
| `WorkspaceValidationIssueDto` | `severity` | `issues[].severity` | 严重级别 |
| `WorkspaceValidationIssueDto` | `type` | `issues[].type` | 问题类型 |
| `WorkspaceValidationIssueDto` | `description` | `issues[].description` | 问题描述 |
| `WorkspaceValidationIssueDto` | `team` | `issues[].team` | 团队 |
| `WorkspaceValidationIssueDto` | `date` | `issues[].date` | 日期 |