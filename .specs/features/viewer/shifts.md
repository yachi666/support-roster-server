# Viewer · 排班接口（Viewer Shifts）

## 文档定位

本文描述 `GET /api/shifts` 与 `GET /api/shifts/{id}` 的只读行为、筛选规则与 DTO 输出。

## 资源范围

- `GET /api/shifts`
- `GET /api/shifts/{id}`
- Controller：`ShiftController`

## 契约与源码映射

| 类型 | 入口 |
|---|---|
| OpenAPI 路径 | [../../../api/paths/viewer/shifts.yaml](../../../api/paths/viewer/shifts.yaml) |
| OpenAPI 聚合入口 | [../../../api/openapi.yaml](../../../api/openapi.yaml) |
| Controller | [ShiftController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/ShiftController.java) |
| Service | [RosterService.java](../../../src/main/java/com/support/server/supportrosterserver/service/RosterService.java) |
| DTO | [ShiftDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/ShiftDto.java) |
| 子 DTO | [ContactDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/ContactDto.java)、[BackupDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/BackupDto.java) |

## 能力边界

- `GET /api/shifts` 必须提供 `date`。
- `teamId` 为可选筛选参数；未命中团队映射时当前实现会退化为全量查询。
- `timezone` 缺省为 `UTC`；非法值在当前实现中可能导致 `500`。
- `GET /api/shifts/{id}` 使用字符串 path 参数查询单条排班，当前实现仍有历史不完整说明。

## 核心规则

- 当前仅返回满足“可见团队 + 可见班次”为真的排班；`primaryShift` 只作为 DTO 语义字段返回，不再作为 Viewer 可见性的硬过滤条件。
- `contact` 来自人员主数据聚合。
- `backup` 字段在 DTO 中存在，但 `RosterService` 当前未填充该字段。

## 字段映射

### 请求字段

| 接口 | 输入位置 | 字段 | 控制器参数 | 必填 | 说明 |
|---|---|---|---|---|---|
| `GET /api/shifts` | query | `date` | `LocalDate date` | 是 | 查询日期 |
| `GET /api/shifts` | query | `teamId` | `String teamId` | 否 | 团队筛选 |
| `GET /api/shifts` | query | `timezone` | `String timezone` | 否 | 目标时区，默认 `UTC` |
| `GET /api/shifts/{id}` | path | `id` | `String id` | 是 | 排班标识 |

### 响应 DTO 字段

| DTO 字段 | OpenAPI 字段 | 说明 |
|---|---|---|
| `id` | `id` | 排班标识 |
| `teamId` | `teamId` | 团队编码 |
| `staffId` | `staffId` | 人员主键 |
| `userName` | `userName` | 人员名称 |
| `userAvatar` | `userAvatar` | 基于 `staffId` 实时拼接 |
| `code` | `code` | 班次编码 |
| `meaning` | `meaning` | 班次含义 |
| `start` | `start` | 开始时间 |
| `end` | `end` | 结束时间 |
| `timezone` | `timezone` | 班次时区标记 |
| `isPrimary` | `isPrimary` | 是否主班次 |
| `showOnRoster` | `showOnRoster` | 是否展示 |
| `remark` | `remark` | 备注 |
| `contact` | `contact` | 联系方式对象 |
| `backup` | `backup` | 备用联系人对象 |

### 嵌套 DTO 字段

| DTO | 字段 | OpenAPI 字段 | 说明 |
|---|---|---|---|
| `ContactDto` | `slack` | `contact.slack` | Slack |
| `ContactDto` | `email` | `contact.email` | 邮箱 |
| `ContactDto` | `phone` | `contact.phone` | 电话 |
| `BackupDto` | `name` | 无 | 当前 OpenAPI 未声明该对象 |
| `BackupDto` | `contact` | 无 | 当前 OpenAPI 未声明该对象 |

## 维护提示

- 排班筛选行为与领域规则高度耦合；若修改主班次集合、时区转换或团队映射，应同步回看本文。
- `GET /api/shifts/{id}` 的历史兼容行为应明确标注，不要误写为完整详情能力。
