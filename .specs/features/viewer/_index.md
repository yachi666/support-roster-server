# Viewer 分册目录

## 文档定位

本分册描述面向公开查看页的只读接口。viewer 接口继续使用 `/api/**` 命名空间，不与 `/api/workspace/**` 合并。

## 阅读路径

| 目标 | 建议顺序 |
|---|---|
| 先理解只读边界 | [overview.md](./overview.md) |
| 查看团队与人员 | [teams.md](./teams.md) → [staff.md](./staff.md) |
| 查看排班输出 | [shifts.md](./shifts.md) → [shift-codes.md](./shift-codes.md) |
| 理解历史兼容 | [role-groups.md](./role-groups.md) |

## 资源目录

| 文档 | 能力 | 对应 OpenAPI |
|---|---|---|
| [overview.md](./overview.md) | viewer 能力边界、委托关系与兼容规则 | [../../../api/openapi.yaml](../../../api/openapi.yaml) |
| [teams.md](./teams.md) | 团队列表 | [../../../api/paths/viewer/teams.yaml](../../../api/paths/viewer/teams.yaml) |
| [shifts.md](./shifts.md) | 排班列表与详情 | [../../../api/paths/viewer/shifts.yaml](../../../api/paths/viewer/shifts.yaml) |
| [staff.md](./staff.md) | 人员列表与详情 | [../../../api/paths/viewer/staff.yaml](../../../api/paths/viewer/staff.yaml) |
| [shift-codes.md](./shift-codes.md) | 班次编码列表 | [../../../api/paths/viewer/shift-codes.yaml](../../../api/paths/viewer/shift-codes.yaml) |
| [role-groups.md](./role-groups.md) | 已废弃的历史兼容说明 | [../../../api/paths/viewer/role-groups.yaml](../../../api/paths/viewer/role-groups.yaml) |

## 契约与源码映射

| 文档 | Controller | Service | DTO |
|---|---|---|---|
| `teams.md` | [TeamController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/TeamController.java) | [RosterService.java](../../../src/main/java/com/support/server/supportrosterserver/service/RosterService.java) | [TeamDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/TeamDto.java) |
| `shifts.md` | [ShiftController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/ShiftController.java) | [RosterService.java](../../../src/main/java/com/support/server/supportrosterserver/service/RosterService.java) | [ShiftDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/ShiftDto.java) |
| `staff.md` | [StaffController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/StaffController.java) | [StaffService.java](../../../src/main/java/com/support/server/supportrosterserver/service/StaffService.java) | [StaffDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/StaffDto.java) |
| `shift-codes.md` | [ShiftCodeController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/ShiftCodeController.java) | [WorkspaceShiftDefinitionService.java](../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceShiftDefinitionService.java) | [ShiftCodeDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/ShiftCodeDto.java) |
| `role-groups.md` | 已废弃 | 已废弃 | 已废弃 |

## 维护提示

- viewer 文档应始终强调“只读边界”，即便内部复用 workspace service，也不能写成后台写能力的变体说明。
- 若某个 viewer 字段来自 workspace 适配层，应明确标注委托关系，避免读者误判为独立数据源。
