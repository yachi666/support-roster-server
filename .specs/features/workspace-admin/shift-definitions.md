# 工作台后台 · 班次定义（Workspace Shift Definitions）

## 文档定位

本文描述 `/api/workspace/shift-definitions` 资源的查询与 CRUD 规则，重点说明班次定义与团队的关联方式。

## 资源范围

- `GET /api/workspace/shift-definitions`
- `GET /api/workspace/shift-definitions/{id}`
- `POST /api/workspace/shift-definitions`
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
- 响应通过 `teams` 数组返回共享团队列表，同时兼容保留 `teamId` / `teamCode` / `teamName` 作为主显示团队。

## 核心规则

- 班次定义必须至少绑定一个已存在团队。
- 同一团队下，相同 `code` 只能关联一条有效班次定义。
- 共享班次通过团队关联表实现，而不是复制多条主记录。
- `startTime` 与 `endTime` 不可形成无效时间范围。
- `primaryShift` 参与主班次校验规则；`visible` 控制是否出现在后台排班选项和相关展示中。

## 关联影响

- 月度排班保存时通过班次编码引用本资源。
- 校验中心会基于班次定义完整性输出问题。
- 导入预览复用同一套班次存在性校验口径。

## 字段映射

### 请求字段

| 请求字段 | Request DTO | Response DTO 字段 | 必填 | 说明 |
|---|---|---|---|---|
| `teamIds` | `WorkspaceShiftDefinitionUpsertRequest.teamIds` | `teams[].id` | 是 | 共享团队主键列表 |
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
|---|---|---|---|---|---|
| `GET /api/workspace/shift-definitions` | query | `keyword` | `String keyword` | 否 | 关键字筛选 |
| `GET/PUT/DELETE /api/workspace/shift-definitions/{id}` | path | `id` | `Long id` | 是 | 班次定义主键 |

### 响应 DTO 字段

| DTO 字段 | OpenAPI 字段 | 说明 |
|---|---|---|
| `id` | `id` | 主键 |
| `teamId` | `teamId` | 主显示团队主键 |
| `teamCode` | `teamCode` | 主显示团队编码 |
| `teamName` | `teamName` | 主显示团队名称 |
| `code` | `code` | 班次编码 |
| `meaning` | `meaning` | 班次含义 |
| `startTime` | `startTime` | 开始时间 |
| `endTime` | `endTime` | 结束时间 |
| `timezone` | `timezone` | 时区 |
| `primaryShift` | `primaryShift` | 是否主班次 |
| `visible` | `visible` | 是否可见 |
| `colorHex` | `colorHex` | 颜色 |
| `remark` | `remark` | 备注 |
| `teams` | `teams` | 共享团队列表 |

## 维护提示

- 若团队共享规则变化，应同步更新班次定义资源与月度排班、导入校验文档。
- `teamId/teamCode/teamName` 属于兼容展示字段，不代表班次定义只能绑定单一团队。
