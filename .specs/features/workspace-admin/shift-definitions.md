# 工作台后台 · 班次定义（Workspace Shift Definitions）

## 文档定位

本文描述 `/api/workspace/shift-definitions` 资源的查询、CRUD 与 TEAM 维度重排规则，重点说明班次定义与团队的关联方式。

## 资源范围

- `GET /api/workspace/shift-definitions`
- `GET /api/workspace/shift-definitions/{id}`
- `POST /api/workspace/shift-definitions`
- `POST /api/workspace/shift-definitions/reorder`
- `PUT /api/workspace/shift-definitions/{id}`
- `DELETE /api/workspace/shift-definitions/{id}`
- Controller：`WorkspaceShiftDefinitionController`

## 契约与源码映射

| 类型 | 入口 |
|---|---|
| OpenAPI 路径 | [../../../api/paths/workspace/shift-definitions.yaml](../../../api/paths/workspace/shift-definitions.yaml) |
| OpenAPI 聚合入口 | [../../../api/openapi.yaml](../../../api/openapi.yaml) |
| Controller | [WorkspaceShiftDefinitionController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceShiftDefinitionController.java) |
| Service | [WorkspaceShiftDefinitionService.java](../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceShiftDefinitionService.java) |
| 响应 DTO | [WorkspaceShiftDefinitionDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceShiftDefinitionDto.java) |
| 写入请求 | [WorkspaceShiftDefinitionUpsertRequest.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceShiftDefinitionUpsertRequest.java) |

## 能力边界

- 列表接口支持可选 `keyword`。
- 单条班次定义可关联多个团队。
- 响应通过 `teams` 数组返回共享团队列表，并保留 `teamId` / `teamName` 作为主显示团队。
- 更新共享班次时，如果原 `teamId` 仍包含在新的 `teamIds` 中，必须保持原主显示团队不变；只有原团队被移除时才使用新的首个团队作为主显示团队。
- `teams[].displayOrder` 记录当前班次在对应 TEAM 下的展示顺序，供工作台拖拽排序、月排班和导入预览复用。

## 核心规则

- 班次定义必须至少绑定一个已存在团队。
- 同一团队下，相同 `code` 只能关联一条有效班次定义。
- 共享班次通过团队关联表实现，而不是复制多条主记录。
- `teamId/teamName` 是主显示团队，更新 `teamIds` 时应优先保留既有主显示团队，避免普通编辑改变共享班次身份与默认排序锚点。
- TEAM 维度的排序持久化在 `workspace_shift_definition_team_rel.display_order`，允许同一条共享班次在不同 TEAM 下拥有不同顺序。
- 写入语义使用 `startTime + durationMinutes`，其中 `durationMinutes` 范围为 `1..1440`。
- `primaryShift` 参与主班次校验规则；`visible` 控制是否出现在后台排班选项和公共 Viewer 中，不能再额外依赖 `primaryShift=true` 才可见。
- 历史排班与人员分配通过 `shiftDefinitionId` 关联；编辑 `code` 不会破坏既有 assignment。

## 关联影响

- 月度排班保存时通过班次编码引用本资源。
- 校验中心会基于班次定义完整性输出问题。
- 导入预览复用同一套班次存在性校验口径。
- 月排班与导入预览返回的 `shiftCodeOptionsByTeam` 需要遵循同一 TEAM 维度展示顺序。

## 字段映射

### 请求字段

| 请求字段 | Request DTO | Response DTO 字段 | 必填 | 说明 |
|---|---|---|---|---|
| `teamIds` | `WorkspaceShiftDefinitionUpsertRequest.teamIds` | `teams[].id` | 是 | 共享团队主键列表 |
| `code` | `WorkspaceShiftDefinitionUpsertRequest.code` | `code` | 是 | 班次编码 |
| `meaning` | `WorkspaceShiftDefinitionUpsertRequest.meaning` | `meaning` | 是 | 班次说明 |
| `startTime` | `WorkspaceShiftDefinitionUpsertRequest.startTime` | `startTime` | 是 | 开始时间 |
| `durationMinutes` | `WorkspaceShiftDefinitionUpsertRequest.durationMinutes` | `durationMinutes` | 是 | 时长（分钟） |
| `timezone` | `WorkspaceShiftDefinitionUpsertRequest.timezone` | `timezone` | 是 | 时区 |
| `primaryShift` | `WorkspaceShiftDefinitionUpsertRequest.primaryShift` | `primaryShift` | 是 | 是否主班次 |
| `visible` | `WorkspaceShiftDefinitionUpsertRequest.visible` | `visible` | 是 | 是否可见 |
| `colorHex` | `WorkspaceShiftDefinitionUpsertRequest.colorHex` | `colorHex` | 否 | 颜色 |
| `remark` | `WorkspaceShiftDefinitionUpsertRequest.remark` | `remark` | 否 | 备注 |

### 查询参数与路径字段

| 接口 | 输入位置 | 字段 | 控制器参数 | 必填 | 说明 |
|---|---|---|---|---|---|
| `GET /api/workspace/shift-definitions` | query | `keyword` | `String keyword` | 否 | 关键字筛选 |
| `POST /api/workspace/shift-definitions/reorder` | body | `teamId` | `WorkspaceShiftDefinitionReorderRequest.teamId` | 是 | 被重排的团队主键；JSON 中按字符串传输以避免前端 Long 精度丢失 |
| `POST /api/workspace/shift-definitions/reorder` | body | `shiftDefinitionIds[]` | `WorkspaceShiftDefinitionReorderRequest.shiftDefinitionIds` | 是 | 该 TEAM 下完整班次顺序；JSON 中按字符串传输 |
| `GET/PUT/DELETE /api/workspace/shift-definitions/{id}` | path | `id` | `Long id` | 是 | 班次定义主键 |

### 响应 DTO 字段

| DTO 字段 | OpenAPI 字段 | 说明 |
|---|---|---|
| `id` | `id` | 主键 |
| `teamId` | `teamId` | 主显示团队主键 |
| `teamName` | `teamName` | 主显示团队名称 |
| `code` | `code` | 班次编码 |
| `meaning` | `meaning` | 班次含义 |
| `startTime` | `startTime` | 开始时间 |
| `endTime` | `endTime` | 由 `startTime + durationMinutes` 推导的结束时间 |
| `durationMinutes` | `durationMinutes` | 班次时长（分钟） |
| `timezone` | `timezone` | 时区 |
| `primaryShift` | `primaryShift` | 是否主班次 |
| `visible` | `visible` | 是否可见 |
| `colorHex` | `colorHex` | 颜色 |
| `remark` | `remark` | 备注 |
| `teams` | `teams` | 共享团队列表 |
| `teams[].displayOrder` | `teams[].displayOrder` | 当前班次在对应 TEAM 下的展示顺序 |

## 维护提示

- 若团队共享规则变化，应同步更新班次定义资源与月度排班、导入校验文档。
- `teamId/teamName` 属于主显示辅助字段，不代表班次定义只能绑定单一团队。
