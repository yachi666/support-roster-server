# OpenAPI 契约目录结构

## 文档定位

本文档说明 `api/openapi.yaml` 的拆分方式、controller 与路径文件的映射关系，以及维护 OpenAPI 契约时应遵守的目录规则。

## 目录结构

```text
api/
├── openapi.yaml
├── components/
│   └── common.yaml
└── paths/
    ├── viewer/
    │   ├── role-groups.yaml
    │   ├── shift-codes.yaml
    │   ├── shifts.yaml
    │   ├── staff.yaml
    │   └── teams.yaml
    └── workspace/
        ├── import-export.yaml
        ├── overview.yaml
        ├── role-groups.yaml
        ├── roster.yaml
        ├── shift-definitions.yaml
        ├── staff.yaml
        ├── teams.yaml
        └── validation.yaml
```

## 文件职责

| 文件 / 目录 | 角色 |
|---|---|
| `api/openapi.yaml` | 聚合入口，只保留元信息、标签与路径引用 |
| `api/components/common.yaml` | 共享参数、公共响应、复用 schema |
| `api/paths/viewer/*.yaml` | viewer controller 对应的只读路径片段 |
| `api/paths/workspace/*.yaml` | workspace controller 对应的后台路径片段 |

## Controller 映射

| Controller | 契约文件 |
|---|---|
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

## 维护规则

- 新增 controller 时，优先新增独立路径文件，而不是继续把细节写回 `api/openapi.yaml`。
- `/api/workspace/**` 路径必须位于 `api/paths/workspace/`，viewer 只读路径必须位于 `api/paths/viewer/`。
- 多个路径共享的参数或 schema，应抽取到 `api/components/common.yaml`。
- 历史兼容路径若仍保留契约文件，应在对应 feature 文档中标记为“已废弃 / 历史兼容”。
