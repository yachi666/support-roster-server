# Viewer Staff

## 资源范围

- `GET /api/staff`
- `GET /api/staff/{id}`

controller：`StaffController`

## 对应 OpenAPI 契约

- 路径文件：[api/paths/viewer/staff.yaml](../../../api/paths/viewer/staff.yaml)
- 聚合入口：[api/openapi.yaml](../../../api/openapi.yaml)

## 对应源码

- Controller：[StaffController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/StaffController.java)
- Service：[StaffService.java](../../../src/main/java/com/support/server/supportrosterserver/service/StaffService.java)
- DTO：[StaffDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/StaffDto.java)

## 资源约束

- viewer staff service 当前委托 `WorkspaceStaffService` 生成只读 DTO。
- 列表接口与详情接口共用 `StaffDto`，但当前实现中 `roleGroups` 的填充粒度可能因 service 路径不同而存在差异，应以代码实际行为为准。

## 请求字段与 DTO 字段映射

### 请求字段

| 接口 | 输入位置 | 字段 | 控制器参数 | 必填 | 说明 |
|------|------|------|------|------|------|
| `GET /api/staff` | - | 无 | - | - | 列表接口无查询参数 |
| `GET /api/staff/{id}` | path | `id` | `Long id` | 是 | 人员主键 |

### 响应 DTO 字段

| DTO 字段 | OpenAPI 字段 | 说明 |
|------|------|------|
| `id` | `id` | 人员主键 |
| `name` | `name` | 人员姓名 |
| `avatar` | `avatar` | 头像 |
| `email` | `email` | 邮箱 |
| `phone` | `phone` | 电话 |
| `slack` | `slack` | Slack |
| `region` | `region` | 区域 |
| `contact` | `contact` | 联系方式摘要 |
| `roleGroups` | `roleGroups` | 角色组列表 |
