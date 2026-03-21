# Workspace Admin 分册目录

## 文档定位

本分册描述 `/api/workspace/**` 管理后台能力，覆盖聚合首页、主数据维护、月度排班、校验中心与 Excel 导入导出。

## 阅读路径

| 目标 | 建议顺序 |
|---|---|
| 先理解整体边界 | [overview.md](./overview.md) → 资源文档 |
| 评审首页聚合 | [dashboard-overview.md](./dashboard-overview.md) |
| 评审主数据维护 | [staff.md](./staff.md) / [shift-definitions.md](./shift-definitions.md) / [teams.md](./teams.md) |
| 评审排班写入链路 | [roster.md](./roster.md) → [validation.md](./validation.md) |
| 评审导入流程 | [import-export.md](./import-export.md) → [validation.md](./validation.md) |

## 资源目录

| 文档 | 能力 | 对应 OpenAPI |
|---|---|---|
| [overview.md](./overview.md) | workspace 能力边界、核心表、跨资源约束 | [../../../api/openapi.yaml](../../../api/openapi.yaml) |
| [dashboard-overview.md](./dashboard-overview.md) | 工作台首页聚合 | [../../../api/paths/workspace/overview.yaml](../../../api/paths/workspace/overview.yaml) |
| [staff.md](./staff.md) | 人员目录 CRUD | [../../../api/paths/workspace/staff.yaml](../../../api/paths/workspace/staff.yaml) |
| [shift-definitions.md](./shift-definitions.md) | 班次定义 CRUD | [../../../api/paths/workspace/shift-definitions.yaml](../../../api/paths/workspace/shift-definitions.yaml) |
| [teams.md](./teams.md) | 团队管理 CRUD | [../../../api/paths/workspace/teams.yaml](../../../api/paths/workspace/teams.yaml) |
| [roster.md](./roster.md) | 月度排班查询与保存 | [../../../api/paths/workspace/roster.yaml](../../../api/paths/workspace/roster.yaml) |
| [validation.md](./validation.md) | 校验中心 | [../../../api/paths/workspace/validation.yaml](../../../api/paths/workspace/validation.yaml) |
| [import-export.md](./import-export.md) | 导入预览、应用、导出与模板下载 | [../../../api/paths/workspace/import-export.yaml](../../../api/paths/workspace/import-export.yaml) |
| [role-groups.md](./role-groups.md) | 已废弃的历史兼容说明 | [../../../api/paths/workspace/role-groups.yaml](../../../api/paths/workspace/role-groups.yaml) |

## 契约与源码映射

| 文档 | Controller | Service | DTO |
|---|---|---|---|
| `dashboard-overview.md` | [WorkspaceOverviewController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceOverviewController.java) | [WorkspaceOverviewService.java](../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceOverviewService.java) | [WorkspaceOverviewResponse.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceOverviewResponse.java) |
| `staff.md` | [WorkspaceStaffController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceStaffController.java) | [WorkspaceStaffService.java](../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceStaffService.java) | [WorkspaceStaffDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceStaffDto.java) / [WorkspaceStaffUpsertRequest.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceStaffUpsertRequest.java) |
| `shift-definitions.md` | [WorkspaceShiftDefinitionController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceShiftDefinitionController.java) | [WorkspaceShiftDefinitionService.java](../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceShiftDefinitionService.java) | [WorkspaceShiftDefinitionDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceShiftDefinitionDto.java) / [WorkspaceShiftDefinitionUpsertRequest.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceShiftDefinitionUpsertRequest.java) |
| `teams.md` | [WorkspaceTeamController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceTeamController.java) | [WorkspaceTeamService.java](../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceTeamService.java) | [WorkspaceTeamDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceTeamDto.java) / [WorkspaceTeamUpsertRequest.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceTeamUpsertRequest.java) |
| `roster.md` | [WorkspaceRosterController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceRosterController.java) | [WorkspaceRosterService.java](../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceRosterService.java) | [WorkspaceMonthlyRosterResponse.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceMonthlyRosterResponse.java) / [WorkspaceRosterSaveRequest.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceRosterSaveRequest.java) |
| `validation.md` | [WorkspaceValidationController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceValidationController.java) | [WorkspaceValidationService.java](../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceValidationService.java) | [WorkspaceValidationResponse.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceValidationResponse.java) |
| `import-export.md` | [WorkspaceImportExportController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceImportExportController.java) | [WorkspaceImportService.java](../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceImportService.java) | [WorkspaceImportPreviewResponse.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceImportPreviewResponse.java) / [WorkspaceImportApplyResponse.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceImportApplyResponse.java) |
| `role-groups.md` | 已废弃 | 已废弃 | 已废弃 |

## 维护提示

- 资源级差异应落到具体文档，不在本目录页堆叠细节。
- 若 workspace 新增资源，应同时补充本页的“资源目录”和“契约与源码映射”两张表。
