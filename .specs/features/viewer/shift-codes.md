# Viewer · 班次编码（Viewer Shift Codes）

## 文档定位

本文描述 `GET /api/shift-codes` 的只读输出，用于公开查看页展示班次编码摘要。

## 资源范围

- 接口：`GET /api/shift-codes`
- Controller：`ShiftCodeController`
- 输出 DTO：`ShiftCodeDto`

## 契约与源码映射

| 类型 | 入口 |
|---|---|
| OpenAPI 路径 | [../../../api/paths/viewer/shift-codes.yaml](../../../api/paths/viewer/shift-codes.yaml) |
| OpenAPI 聚合入口 | [../../../api/openapi.yaml](../../../api/openapi.yaml) |
| Controller | [ShiftCodeController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/ShiftCodeController.java) |
| Service | [WorkspaceShiftDefinitionService.java](../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceShiftDefinitionService.java) |
| DTO | [ShiftCodeDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/ShiftCodeDto.java) |

## 能力边界

- viewer 班次编码接口直接复用 workspace 班次定义服务生成输出。
- 仅返回公开查看页所需的班次编码摘要，不暴露完整班次定义配置。

## 字段映射

### 请求字段

- 无请求体，无查询参数。

### 响应 DTO 字段

| DTO 字段 | OpenAPI 字段 | 说明 |
|---|---|---|
| `code` | `code` | 班次编码 |
| `meaning` | `meaning` | 班次含义 |
| `color` | `color` | 展示颜色 |

## 维护提示

- 若 shift-code 输出被扩展，应确认新增字段仍适合公开只读场景，而不是泄露后台配置细节。
