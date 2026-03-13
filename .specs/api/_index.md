# API Spec Index

## 范围

- 本目录承载接口契约、请求响应格式、状态码、鉴权、接口演进记录等内容。
- 新增接口规范时，优先放在本目录下并在此处登记。

## 当前文档

| 文档 | 说明 |
|------|------|
| `../api-standard.md` | 当前服务端 API 总体规范，包含 viewer 接口与 `/api/workspace/**` 管理接口约定 |
| `openapi-layout.md` | OpenAPI 契约文件的目录结构、controller 到 YAML 的映射关系与维护规则 |
| `../features/workspace-admin-backend.md` | Workspace 后台管理接口分组、导入流程、校验规则与兼容策略 |

## 当前 OpenAPI 组织

- `api/openapi.yaml` 仅保留元信息、标签与路径聚合入口。
- viewer 接口按 controller 拆分到 `api/paths/viewer/`。
- workspace 接口统一拆分到 `api/paths/workspace/`，避免与 viewer 只读接口混放。
- 共享的参数、响应和 schema 统一放在 `api/components/common.yaml`。