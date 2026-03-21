# 第 1 章 接口规范目录

## 文档定位

本章用于维护 HTTP API 的总体约定、OpenAPI 契约组织方式，以及 viewer / workspace 两条接口线的阅读入口。

## 阅读建议

| 先读 | 再读 | 适用任务 |
|---|---|---|
| [../api-standard.md](../api-standard.md) | [openapi-layout.md](./openapi-layout.md) | 从总体路由、状态码与请求响应规范入手 |
| [../api-standard.md](../api-standard.md) | [../features/viewer/_index.md](../features/viewer/_index.md) | 审阅 viewer 只读接口 |
| [../api-standard.md](../api-standard.md) | [../features/workspace-admin/_index.md](../features/workspace-admin/_index.md) | 审阅 workspace 后台接口 |

## 目录清单

| 文档 | 角色 | 重点内容 |
|---|---|---|
| [../api-standard.md](../api-standard.md) | 总体规范 | 路由命名、通用请求响应、状态码、DTO 速查、当前安全边界 |
| [openapi-layout.md](./openapi-layout.md) | OpenAPI 维护规范 | `api/openapi.yaml` 聚合方式、路径文件拆分、controller 对应关系 |
| [../features/workspace-admin-backend.md](../features/workspace-admin-backend.md) | 专题入口 | workspace 资源目录与 viewer 兼容边界 |

## 契约入口

| 契约位置 | 说明 |
|---|---|
| [`../../api/openapi.yaml`](../../api/openapi.yaml) | OpenAPI 聚合入口，保留 `info`、`tags` 与 `paths` 聚合引用 |
| [`../../api/components/common.yaml`](../../api/components/common.yaml) | 通用参数、共享 schema 与公共响应 |
| [`../../api/paths/viewer/`](../../api/paths/viewer/) | viewer 路径文件目录 |
| [`../../api/paths/workspace/`](../../api/paths/workspace/) | workspace 路径文件目录 |

## 维护提示

- 接口语义发生变化时，优先更新对应的 feature 资源文档，再修订总体规范。
- 新增 controller 时，应同步补充 `openapi-layout.md` 中的映射表。
- 历史兼容接口若已废弃，应在资源文档中显式标记，而不是直接从目录中消失。
