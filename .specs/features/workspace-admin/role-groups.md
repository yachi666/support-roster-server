# Workspace Role Groups

## 资源范围

- 接口：`GET /api/workspace/role-groups`
- controller：`WorkspaceRoleGroupController`
- 输出 DTO：`WorkspaceRoleGroupDto`

## 对应 OpenAPI 契约

- 路径文件：[api/paths/workspace/role-groups.yaml](../../../api/paths/workspace/role-groups.yaml)
- 聚合入口：[api/openapi.yaml](../../../api/openapi.yaml)

## 对应源码

- Controller：[WorkspaceRoleGroupController.java](../../../src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceRoleGroupController.java)
- Service：[WorkspaceRoleGroupService.java](../../../src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceRoleGroupService.java)
- DTO：[WorkspaceRoleGroupDto.java](../../../src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceRoleGroupDto.java)

## 字段职责

角色组资源承载以下核心属性：

- `code`、`name`：业务标识与展示名称
- `category`、`region`：分类与地区标签
- `description`：补充说明
- `active`：启用状态

## 依赖关系

- 团队资源通过角色组建立可服务范围。
- 人员资源通过 `roleGroupId` 归属角色组。
- 班次定义资源通过 `roleGroupId` 绑定适用角色组。

## 当前实现边界

- 当前 controller 仅暴露列表读取接口，不提供后台直接维护角色组的写接口。
- 若后续需要管理角色组字典，应新增独立写接口与对应规范，而不是复用现有只读列表语义。

## 请求字段与 DTO 字段映射

### 请求字段

- 无请求体，无查询参数。

### 响应 DTO 字段

| DTO 字段 | OpenAPI 字段 | 说明 |
|------|------|------|
| `id` | `id` | 主键 |
| `code` | `code` | 角色组编码 |
| `name` | `name` | 角色组名称 |
| `category` | `category` | 分类 |
| `region` | `region` | 区域 |
| `description` | `description` | 描述 |
| `active` | `active` | 启用状态 |