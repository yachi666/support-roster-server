# OpenAPI 契约目录结构

## 目标

- 避免将所有 API 契约堆叠在单个 `api/openapi.yaml` 中。
- 契约文件按 controller 维度维护，降低单次修改的冲突面。
- 将 workspace 管理接口与 viewer 只读接口在目录层级上明确隔离。

## 当前目录结构

```text
api/
├── openapi.yaml
├── components/
│   └── common.yaml
└── paths/
    ├── viewer/
    │   ├── shift-codes.yaml
    │   ├── shifts.yaml
    │   ├── staff.yaml
    │   └── teams.yaml
    └── workspace/
        ├── import-export.yaml
        ├── overview.yaml
        ├── roster.yaml
        ├── shift-definitions.yaml
        ├── staff.yaml
        ├── teams.yaml
        └── validation.yaml
```

## 文件职责

### `api/openapi.yaml`

- 作为 OpenAPI 主入口文件。
- 只保留 `openapi`、`info`、`servers`、`tags` 与 `paths` 聚合引用。
- 不再直接承载具体路径实现和公共 schema 明细。

### `api/components/common.yaml`

- 承载跨 controller 复用的参数定义、通用响应与 schema。
- 路径文件中的 `$ref` 直接引用本文件，避免重复定义同名结构。

### `api/paths/viewer/*.yaml`

- 与 `controller` 包下的 viewer controller 一一对应。
- 单文件内可包含同一个 controller 暴露的多个 path item 片段，例如集合接口与详情接口。

### `api/paths/workspace/*.yaml`

- 与 `controller/workspace` 包下的 workspace controller 一一对应。
- workspace 接口必须放在独立目录下，不与 viewer 契约混排。

## Controller 映射

| Controller | 契约文件 |
|------|------|
| `TeamController` | `api/paths/viewer/teams.yaml` |
| `ShiftController` | `api/paths/viewer/shifts.yaml` |
| `StaffController` | `api/paths/viewer/staff.yaml` |
| `ShiftCodeController` | `api/paths/viewer/shift-codes.yaml` |
| `WorkspaceOverviewController` | `api/paths/workspace/overview.yaml` |
| `WorkspaceStaffController` | `api/paths/workspace/staff.yaml` |
| `WorkspaceShiftDefinitionController` | `api/paths/workspace/shift-definitions.yaml` |
| `WorkspaceTeamController` | `api/paths/workspace/teams.yaml` |
| `WorkspaceRosterController` | `api/paths/workspace/roster.yaml` |
| `WorkspaceValidationController` | `api/paths/workspace/validation.yaml` |
| `WorkspaceImportExportController` | `api/paths/workspace/import-export.yaml` |

## 维护约束

- 新增 controller 时，优先新增对应的独立路径文件，而不是继续扩展主入口文件。
- 若新增的是 `/api/workspace/**` 接口，必须放入 `api/paths/workspace/`。
- 若多个 controller 共享同一响应结构，应优先抽到 `api/components/common.yaml`。
- `api/openapi.yaml` 只作为聚合入口，不应再次演变回大而全文件。