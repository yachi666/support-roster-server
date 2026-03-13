# Workspace Import Export

## 资源范围

- `POST /api/workspace/import-export/preview`
- `POST /api/workspace/import-export/{batchId}/apply`
- `GET /api/workspace/import-export/export`
- `GET /api/workspace/import-export/template`

controller：`WorkspaceImportExportController`

## 对应 OpenAPI 契约

- 路径文件：[api/paths/workspace/import-export.yaml](../../../api/paths/workspace/import-export.yaml)
- 聚合入口：[api/openapi.yaml](../../../api/openapi.yaml)

## 对应源码

- Controller：[WorkspaceImportExportController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceImportExportController.java)
- Service：[WorkspaceImportService.java](../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceImportService.java)
- 预览响应：[WorkspaceImportPreviewResponse.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceImportPreviewResponse.java)
- 应用响应：[WorkspaceImportApplyResponse.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceImportApplyResponse.java)

## 预览阶段

导入文件必须遵循 `spec/source_excel.md` 中当前 3 个 sheet 的格式约定。

预览阶段负责：

1. 解析 Excel 内容。
2. 生成导入记录与问题记录。
3. 返回批次号、有效记录数、无效记录数、问题列表。

预览阶段不直接落正式排班表。

## 应用阶段

应用接口仅对已预览批次执行正式写入：

1. 校验批次状态。
2. 将有效记录写入 `workspace_roster_assignment`。
3. 更新批次状态与操作日志。

## 导出阶段

- 导出接口按 `year`、`month` 输出 CSV。
- 当前 controller 直接返回二进制响应体，由服务层负责文件内容与响应头。
- CSV 以 UTF-8 编码输出，并包含 UTF-8 BOM 与 `text/csv; charset=UTF-8` 响应头，保证 Excel 打开中文字段时不乱码。

## 模版下载

- 模版接口返回预生成的 Excel 模版文件。
- 模版文件位于 `src/main/resources/roster.xlsx`。
- 响应头：`Content-Type: application/vnd.openxmlformats-offreadsheetml.sheet`
- 响应头：`Content-Disposition: attachment; filename=import-template.xlsx`

### 模版结构

模版包含 3 个 sheet：

| Sheet Index | Sheet Name | 说明 |
|-------------|------------|------|
| 0 | Shift Definitions | 班次定义，包含 role_group, code, meaning, start_time, end_time, timezone, show_on_roster_page, remark |
| 1 | Staff Shifts | 员工班次，包含 name, staff_id, role_group, region, contact, notes, 1-31 天列 |
| 2 | Color Definitions | 颜色定义，包含 code, color_name, rgb, hex |

## 资源约束

- 导入预览与应用必须通过 `batchId` 建立批次关联。
- `batchId` 在 JSON 中按字符串传输，避免前端处理超大整数时精度丢失。
- 预览问题列表与校验中心问题模型保持一致，避免出现两套问题口径。
- `operator` 作为可选操作人标记参与批次与日志记录。

## 导入验证规则

### 批次状态判定

- `VALIDATED`：无 `high` 或 `medium` 级别问题，可执行应用操作
- `INVALID`：存在 `high` 或 `medium` 级别问题，阻止应用

### 问题严重级别

| 级别 | 说明 | 是否阻止导入 |
|------|------|-------------|
| `high` | 严重错误（如数据格式错误、必填字段缺失） | 是 |
| `medium` | 中等问题（如映射失败、数据不完整） | 是 |
| `low` | 轻微警告（如 Missing Primary Coverage） | 否 |

### 数据过滤规则

导入时自动过滤以下无效行：
- 标题行（role_group 列为 "role_group"）
- 空行（role_group 或 code 为空）
- 非班次定义行（start_time、end_time、timezone 均为空）
- 颜色定义混入数据（start_time 以 "#" 开头）

## 请求字段与 DTO 字段映射

### 预览请求字段

| 请求字段 | 输入位置 | 控制器参数 | 响应 DTO 字段 | 必填 | 说明 |
|------|------|------|------|------|------|
| `file` | multipart | `MultipartFile file` | 无直接同名字段 | 是 | Excel 文件 |
| `year` | form/query | `Integer year` | `year` | 是 | 年份 |
| `month` | form/query | `Integer month` | `month` | 是 | 月份 |
| `operator` | form/query | `String operator` | 无直接同名字段 | 否 | 操作人 |

### 应用请求字段

| 请求字段 | 输入位置 | 控制器参数 | 响应 DTO 字段 | 必填 | 说明 |
|------|------|------|------|------|------|
| `batchId` | path | `Long batchId` | `batchId` | 是 | 导入批次主键，JSON 响应中按字符串传输 |
| `operator` | query | `String operator` | 无直接同名字段 | 否 | 操作人 |

### 导出请求字段

| 请求字段 | 输入位置 | 控制器参数 | 必填 | 说明 |
|------|------|------|------|------|
| `year` | query | `Integer year` | 是 | 年份 |
| `month` | query | `Integer month` | 是 | 月份 |

### 模版下载请求字段

无参数，直接返回模版文件。

### 响应 DTO 字段

| DTO | 字段 | OpenAPI 字段 | 说明 |
|------|------|------|------|
| `WorkspaceImportPreviewResponse` | `batchId` | `batchId` | 预览批次主键，JSON 传输时为字符串 |
| `WorkspaceImportPreviewResponse` | `year` | `year` | 年份 |
| `WorkspaceImportPreviewResponse` | `month` | `month` | 月份 |
| `WorkspaceImportPreviewResponse` | `status` | `status` | 批次状态 |
| `WorkspaceImportPreviewResponse` | `totalRecords` | `totalRecords` | 总记录数 |
| `WorkspaceImportPreviewResponse` | `validRecords` | `validRecords` | 有效记录数 |
| `WorkspaceImportPreviewResponse` | `invalidRecords` | `invalidRecords` | 无效记录数 |
| `WorkspaceImportPreviewResponse` | `issues` | `issues` | 问题列表 |
| `WorkspaceImportApplyResponse` | `batchId` | `batchId` | 应用批次主键，JSON 传输时为字符串 |
| `WorkspaceImportApplyResponse` | `year` | `year` | 年份 |
| `WorkspaceImportApplyResponse` | `month` | `month` | 月份 |
| `WorkspaceImportApplyResponse` | `status` | `status` | 应用状态 |
| `WorkspaceImportApplyResponse` | `appliedRecords` | `appliedRecords` | 已应用记录数 |