# Viewer Role Groups

## 资源范围

- 接口：`GET /api/role-groups`
- controller：`RoleGroupController`
- 输出 DTO：`RoleGroupDto`

## 对应 OpenAPI 契约

- 路径文件：[api/paths/viewer/role-groups.yaml](../../../api/paths/viewer/role-groups.yaml)
- 聚合入口：[api/openapi.yaml](../../../api/openapi.yaml)

## 对应源码

- Controller：[RoleGroupController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/RoleGroupController.java)
- Service：[RoleGroupService.java](../../../src/main/java/com/support/server/supportrosterserver/service/RoleGroupService.java)
- DTO：[RoleGroupDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/RoleGroupDto.java)

## 资源约束

- 当前只暴露列表读取接口。
- `RoleGroupService` 通过 `WorkspaceLookupService` 读取角色组数据并转换为 viewer DTO。

## 请求字段与 DTO 字段映射

### 请求字段

- 无请求体，无查询参数。

### 响应 DTO 字段

| DTO 字段 | OpenAPI 字段 | 说明 |
|------|------|------|
| `id` | `id` | 角色组编码 |
| `name` | `name` | 角色组名称 |
| `category` | `category` | 分类 |
| `region` | `region` | 区域 |
