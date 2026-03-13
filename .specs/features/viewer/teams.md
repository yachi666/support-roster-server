# Viewer Teams

## 资源范围

- 接口：`GET /api/teams`
- controller：`TeamController`
- 输出 DTO：`TeamDto`

## 对应 OpenAPI 契约

- 路径文件：[api/paths/viewer/teams.yaml](../../../api/paths/viewer/teams.yaml)
- 聚合入口：[api/openapi.yaml](../../../api/openapi.yaml)

## 对应源码

- Controller：[TeamController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/TeamController.java)
- Service：[RosterService.java](../../../src/main/java/com/support/server/supportrosterserver/service/RosterService.java)
- DTO：[TeamDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/TeamDto.java)

## 资源约束

- 当前接口只返回可展示团队列表。
- viewer team 数据通过 `RosterService` 委托 workspace 团队能力生成，不单独维护第二套团队模型。

## 请求字段与 DTO 字段映射

### 请求字段

- 无请求体，无查询参数。

### 响应 DTO 字段

| DTO 字段 | OpenAPI 字段 | 说明 |
|------|------|------|
| `id` | `id` | 团队标识 |
| `name` | `name` | 团队名称 |
| `color` | `color` | 展示颜色 |
| `order` | `order` | 排序值 |
