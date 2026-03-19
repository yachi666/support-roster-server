# Viewer Spec Index

## 范围

- 本目录承载公开查看页只读接口的资源级规范。
- 文档按 viewer controller 拆分，覆盖团队、排班、人员与班次编码。
- role-group 相关文档仅保留为历史兼容说明。

## 文档导航

| 文档 | 说明 | 对应 OpenAPI |
|------|------|------|
| `overview.md` | Viewer 只读接口边界、兼容性约束与共享入口 | `../../../api/openapi.yaml` |
| `teams.md` | 团队列表接口与团队 DTO 字段 | `../../../api/paths/viewer/teams.yaml` |
| `shifts.md` | 排班列表/详情接口、查询参数与排班 DTO 字段 | `../../../api/paths/viewer/shifts.yaml` |
| `staff.md` | 人员列表/详情接口与人员 DTO 字段 | `../../../api/paths/viewer/staff.yaml` |
| `role-groups.md` | 历史兼容说明，记录已废弃的 role-group viewer 接口 | `../../../api/paths/viewer/role-groups.yaml` |
| `shift-codes.md` | 班次编码列表接口与班次编码 DTO 字段 | `../../../api/paths/viewer/shift-codes.yaml` |

## 源码映射

| 资源文档 | Controller | Service | DTO |
|------|------|------|------|
| `teams.md` | `../../../src/main/java/com/support/server/supportrosterserver/controller/TeamController.java` | `../../../src/main/java/com/support/server/supportrosterserver/service/RosterService.java` | `../../../src/main/java/com/support/server/supportrosterserver/dto/TeamDto.java` |
| `shifts.md` | `../../../src/main/java/com/support/server/supportrosterserver/controller/ShiftController.java` | `../../../src/main/java/com/support/server/supportrosterserver/service/RosterService.java` | `../../../src/main/java/com/support/server/supportrosterserver/dto/ShiftDto.java`, `../../../src/main/java/com/support/server/supportrosterserver/dto/ContactDto.java`, `../../../src/main/java/com/support/server/supportrosterserver/dto/BackupDto.java` |
| `staff.md` | `../../../src/main/java/com/support/server/supportrosterserver/controller/StaffController.java` | `../../../src/main/java/com/support/server/supportrosterserver/service/StaffService.java` | `../../../src/main/java/com/support/server/supportrosterserver/dto/StaffDto.java` |
| `role-groups.md` | 已废弃 | 已废弃 | 已废弃 |
| `shift-codes.md` | `../../../src/main/java/com/support/server/supportrosterserver/controller/ShiftCodeController.java` | `../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceShiftDefinitionService.java` | `../../../src/main/java/com/support/server/supportrosterserver/dto/ShiftCodeDto.java` |

## 维护规则

- viewer spec 变更应与 `api/paths/viewer/` 下的 OpenAPI 契约同步。
- 若 viewer 接口继续复用 workspace service 适配层，应在文档中明确标注委托关系，避免误判为独立数据源.
