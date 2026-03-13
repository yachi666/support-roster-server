# Workspace Shift Definitions

## 资源范围

- `GET /api/workspace/shift-definitions`
- `GET /api/workspace/shift-definitions/{id}`
- `POST /api/workspace/shift-definitions`
- `PUT /api/workspace/shift-definitions/{id}`
- `DELETE /api/workspace/shift-definitions/{id}`

controller：`WorkspaceShiftDefinitionController`

## 对应 OpenAPI 契约

- 路径文件：[api/paths/workspace/shift-definitions.yaml](../../../api/paths/workspace/shift-definitions.yaml)
- 聚合入口：[api/openapi.yaml](../../../api/openapi.yaml)

## 对应源码

- Controller：[WorkspaceShiftDefinitionController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceShiftDefinitionController.java)
- Service：[WorkspaceShiftDefinitionService.java](../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceShiftDefinitionService.java)
- DTO：[WorkspaceShiftDefinitionDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceShiftDefinitionDto.java)
- 写入请求：[WorkspaceShiftDefinitionUpsertRequest.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceShiftDefinitionUpsertRequest.java)

## 查询语义

- 列表接口支持可选 `keyword` 参数。
- 返回模型为 `WorkspaceShiftDefinitionDto`。

## 写入字段

写接口统一使用 `WorkspaceShiftDefinitionUpsertRequest`，必填字段包括：

- `roleGroupId`
- `code`
- `meaning`
- `startTime`
- `endTime`
- `timezone`
- `primaryShift`
- `visible`

可选补充字段：

- `colorHex`
- `remark`

## 资源约束

- 班次定义必须绑定已存在的角色组。
- `startTime` 与 `endTime` 不可形成无效时间范围。
- `primaryShift` 用于主班次校验规则。
- `visible` 用于决定是否可出现在后台排班选项和相关展示中。

## 关联影响

- 月度排班保存时使用班次编码引用该资源。
- 校验中心会基于班次定义完整性输出问题。
- 导入预览也会复用同一套班次存在性校验。

## 请求字段与 DTO 字段映射

### 请求字段

| 请求字段 | Request DTO | Response DTO 字段 | 必填 | 说明 |
|------|------|------|------|------|
| `roleGroupId` | `WorkspaceShiftDefinitionUpsertRequest.roleGroupId` | `roleGroupId` | 是 | 角色组主键 |
| `code` | `WorkspaceShiftDefinitionUpsertRequest.code` | `code` | 是 | 班次编码 |
| `meaning` | `WorkspaceShiftDefinitionUpsertRequest.meaning` | `meaning` | 是 | 班次说明 |
| `startTime` | `WorkspaceShiftDefinitionUpsertRequest.startTime` | `startTime` | 是 | 开始时间 |
| `endTime` | `WorkspaceShiftDefinitionUpsertRequest.endTime` | `endTime` | 是 | 结束时间 |
| `timezone` | `WorkspaceShiftDefinitionUpsertRequest.timezone` | `timezone` | 是 | 时区 |
| `primaryShift` | `WorkspaceShiftDefinitionUpsertRequest.primaryShift` | `primaryShift` | 是 | 是否主班次 |
| `visible` | `WorkspaceShiftDefinitionUpsertRequest.visible` | `visible` | 是 | 是否可见 |
| `colorHex` | `WorkspaceShiftDefinitionUpsertRequest.colorHex` | `colorHex` | 否 | 颜色 |
| `remark` | `WorkspaceShiftDefinitionUpsertRequest.remark` | `remark` | 否 | 备注 |

### 查询参数与路径字段

| 接口 | 输入位置 | 字段 | 控制器参数 | 必填 | 说明 |
|------|------|------|------|------|------|
| `GET /api/workspace/shift-definitions` | query | `keyword` | `String keyword` | 否 | 关键字筛选 |
| `GET/PUT/DELETE /api/workspace/shift-definitions/{id}` | path | `id` | `Long id` | 是 | 班次定义主键 |

### 响应 DTO 字段

| DTO 字段 | OpenAPI 字段 | 说明 |
|------|------|------|
| `id` | `id` | 主键 |
| `roleGroupId` | `roleGroupId` | 角色组主键 |
| `roleGroupCode` | `roleGroupCode` | 角色组编码 |
| `roleGroupName` | `roleGroupName` | 角色组名称 |
| `code` | `code` | 班次编码 |
| `meaning` | `meaning` | 班次含义 |
| `startTime` | `startTime` | 开始时间 |
| `endTime` | `endTime` | 结束时间 |
| `timezone` | `timezone` | 时区 |
| `primaryShift` | `primaryShift` | 是否主班次 |
| `visible` | `visible` | 是否可见 |
| `colorHex` | `colorHex` | 颜色 |
| `remark` | `remark` | 备注 |