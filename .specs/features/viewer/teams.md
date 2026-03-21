# Viewer · 团队列表（Viewer Teams）

## 文档定位

本文描述 `GET /api/teams` 团队只读接口。

## 资源范围

- 接口：`GET /api/teams`
- Controller：`TeamController`
- 输出 DTO：`TeamDto`

## 契约与源码映射

| 类型 | 入口 |
|---|---|
| OpenAPI 路径 | [../../../api/paths/viewer/teams.yaml](../../../api/paths/viewer/teams.yaml) |
| OpenAPI 聚合入口 | [../../../api/openapi.yaml](../../../api/openapi.yaml) |
| Controller | [TeamController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/TeamController.java) |
| Service | [RosterService.java](../../../src/main/java/com/support/server/supportrosterserver/service/RosterService.java) |
| DTO | [TeamDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/TeamDto.java) |

## 能力边界

- 接口只返回可展示团队列表。
- viewer team 数据通过 `RosterService` 委托 workspace 团队能力生成，不维护第二套团队模型。

## 字段映射

### 请求字段

- 无请求体，无查询参数。

### 响应 DTO 字段

| DTO 字段 | OpenAPI 字段 | 说明 |
|---|---|---|
| `id` | `id` | 团队标识 |
| `name` | `name` | 团队名称 |
| `color` | `color` | 展示颜色 |
| `order` | `order` | 排序值 |

## 维护提示

- viewer 团队输出若与 workspace 团队资源产生差异，应优先标注“展示裁剪”而不是复制第二份主数据说明。
