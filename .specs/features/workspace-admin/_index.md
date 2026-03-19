# Workspace Admin Spec Index

## 范围

- 本目录承载 `/api/workspace/**` 管理后台能力的资源级规范。
- 每个文档聚焦单一资源或单一能力，避免将人员、班次、团队、排班、导入等主题继续混写在单个文档内。

## 文档导航

| 文档 | 说明 | 对应 OpenAPI |
|------|------|------|
| `overview.md` | Workspace 后台能力边界、持久化栈、核心表与跨资源约束 | `../../../api/openapi.yaml` |
| `dashboard-overview.md` | 总览看板聚合接口与输出结构 | `../../../api/paths/workspace/overview.yaml` |
| `role-groups.md` | 历史兼容说明，记录已废弃的 role-group 后台资源 | `../../../api/paths/workspace/role-groups.yaml` |
| `staff.md` | 人员目录的查询、创建、更新、删除与筛选约束 | `../../../api/paths/workspace/staff.yaml` |
| `shift-definitions.md` | 班次定义的 CRUD 规则与可见性约束 | `../../../api/paths/workspace/shift-definitions.yaml` |
| `teams.md` | 团队资源自身的维护规则 | `../../../api/paths/workspace/teams.yaml` |
| `roster.md` | 月度排班查询、单元格保存与存储约定 | `../../../api/paths/workspace/roster.yaml` |
| `validation.md` | 校验中心输出结构与规则来源 | `../../../api/paths/workspace/validation.yaml` |
| `import-export.md` | Excel 导入预览、应用与导出流程 | `../../../api/paths/workspace/import-export.yaml` |

## 源码映射

| 资源文档 | Controller | Service | DTO |
|------|------|------|------|
| `dashboard-overview.md` | `../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceOverviewController.java` | `../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceOverviewService.java` | `../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceOverviewResponse.java` |
| `role-groups.md` | 已废弃 | 已废弃 | 已废弃 |
| `staff.md` | `../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceStaffController.java` | `../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceStaffService.java` | `../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceStaffDto.java`, `../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceStaffUpsertRequest.java` |
| `shift-definitions.md` | `../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceShiftDefinitionController.java` | `../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceShiftDefinitionService.java` | `../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceShiftDefinitionDto.java`, `../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceShiftDefinitionUpsertRequest.java` |
| `teams.md` | `../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceTeamController.java` | `../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceTeamService.java` | `../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceTeamDto.java`, `../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceTeamUpsertRequest.java` |
| `roster.md` | `../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceRosterController.java` | `../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceRosterService.java` | `../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceMonthlyRosterResponse.java`, `../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceRosterSaveRequest.java`, `../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceRosterCellUpdateRequest.java` |
| `validation.md` | `../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceValidationController.java` | `../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceValidationService.java` | `../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceValidationResponse.java`, `../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceValidationSummaryDto.java`, `../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceValidationIssueDto.java` |
| `import-export.md` | `../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceImportExportController.java` | `../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceImportService.java` | `../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceImportPreviewResponse.java`, `../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceImportApplyResponse.java` |

## 维护规则

- 新增 workspace 资源接口时，优先在本目录新增对应文档，而不是继续扩展总览文档。
- 涉及多个资源的共性约束写入 `overview.md`，具体资源规则落在对应单文档。
- 若 OpenAPI、controller 与 feature spec 发生差异，应同时修正本目录中的资源文档。