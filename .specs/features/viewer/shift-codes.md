# Viewer Shift Codes

## 资源范围

- 接口：`GET /api/shift-codes`
- controller：`ShiftCodeController`
- 输出 DTO：`ShiftCodeDto`

## 对应 OpenAPI 契约

- 路径文件：[api/paths/viewer/shift-codes.yaml](../../../api/paths/viewer/shift-codes.yaml)
- 聚合入口：[api/openapi.yaml](../../../api/openapi.yaml)

## 对应源码

- Controller：[ShiftCodeController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/ShiftCodeController.java)
- Service：[WorkspaceShiftDefinitionService.java](../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceShiftDefinitionService.java)
- DTO：[ShiftCodeDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/ShiftCodeDto.java)

## 资源约束

- viewer 班次编码接口当前直接复用 workspace 班次定义服务生成输出。
- 仅返回供公开查看页展示的班次编码摘要，不暴露班次定义完整配置。

## 请求字段与 DTO 字段映射

### 请求字段

- 无请求体，无查询参数。

### 响应 DTO 字段

| DTO 字段 | OpenAPI 字段 | 说明 |
|------|------|------|
| `code` | `code` | 班次编码 |
| `meaning` | `meaning` | 班次含义 |
| `color` | `color` | 展示颜色 |
