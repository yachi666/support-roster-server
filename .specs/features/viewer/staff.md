# Viewer · 人员接口（Viewer Staff）

## 文档定位

本文描述 `GET /api/staff` 与 `GET /api/staff/{id}` 的只读行为与响应结构。

## 资源范围

- `GET /api/staff`
- `GET /api/staff/{id}`
- Controller：`StaffController`

## 契约与源码映射

| 类型 | 入口 |
|---|---|
| OpenAPI 路径 | [../../../api/paths/viewer/staff.yaml](../../../api/paths/viewer/staff.yaml) |
| OpenAPI 聚合入口 | [../../../api/openapi.yaml](../../../api/openapi.yaml) |
| Controller | [StaffController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/StaffController.java) |
| Service | [StaffService.java](../../../src/main/java/com/support/server/supportrosterserver/service/StaffService.java) |
| DTO | [StaffDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/StaffDto.java) |

## 能力边界

- viewer staff service 当前委托 `WorkspaceStaffService` 生成只读 DTO。
- 列表接口与详情接口共用 `StaffDto`。
- 当前返回的分组信息实际来源于团队字段，`roleGroups` 仅作为历史兼容响应字段保留。

## 字段映射

### 请求字段

| 接口 | 输入位置 | 字段 | 控制器参数 | 必填 | 说明 |
|---|---|---|---|---|---|
| `GET /api/staff` | - | 无 | - | - | 列表接口无查询参数 |
| `GET /api/staff/{id}` | path | `id` | `Long id` | 是 | 人员主键 |

### 响应 DTO 字段

| DTO 字段 | OpenAPI 字段 | 说明 |
|---|---|---|
| `id` | `id` | 人员主键 |
| `name` | `name` | 人员姓名 |
| `avatar` | `avatar` | 基于 `staffCode` 实时拼接 |
| `email` | `email` | 邮箱 |
| `phone` | `phone` | 电话 |
| `slack` | `slack` | Slack |
| `region` | `region` | 区域 |
| `contact` | `contact` | 联系方式摘要 |
| `roleGroups` | `roleGroups` | 历史兼容字段，当前返回团队编码列表 |

## 维护提示

- viewer staff 是只读适配结果；若后台 staff DTO 发生重构，应确认 viewer 是否仍保留同一兼容字段集合。
