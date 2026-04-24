# Linux 密码库（workspace）接口规格

## 文档定位

本文定义 `/api/workspace/linux-passwords` 资源的接口、权限、持久化模型与前端联调约束。该资源服务于独立路由页 `/linux-passwords`，但后端仍归入 workspace 写能力体系维护。

## 能力边界

- 管理 Linux 主机的登录凭据、状态与业务单元归属。
- 为前端独立页面提供列表、详情、新增、编辑、删除能力。
- 提供独立目录接口，供前端左侧目录树单独加载。

## 路由

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/workspace/linux-passwords` | 列表查询，支持 `search` / `businessUnit` |
| `GET` | `/api/workspace/linux-passwords/{id}` | 获取单条记录详情 |
| `GET` | `/api/workspace/linux-password-directories` | 获取左侧目录列表 |
| `POST` | `/api/workspace/linux-passwords` | 新增记录 |
| `PUT` | `/api/workspace/linux-passwords/{id}` | 编辑记录 |
| `DELETE` | `/api/workspace/linux-passwords/{id}` | 删除记录 |

## 权限规则

### 读取

- `GET` 列表与详情都要求已登录。
- 不复用 `workspace access policy` 的页面粒度写权限；后端只依赖当前登录态与 workspace 账号角色。

### 写入

- `POST`：任意已登录用户可调用。
- `PUT` / `DELETE`：仅 workspace `admin` 可调用，统一复用 `AuthContextService.requireAdmin()`。

## 列表查询契约

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `search` | `string` | 否 | 按 `hostname` 或 `ip` 模糊匹配 |
| `businessUnit` | `string` | 否 | 按业务单元精确过滤 |

### 响应结构

```json
{
  "items": [
    {
      "id": "123",
      "hostname": "infra-proxy-01",
      "ip": "10.0.1.2",
      "username": "admin",
      "password": "Proxy@Infra99",
      "businessUnits": ["Infrastructure", "Web"],
      "status": "online"
    }
  ],
  "businessUnits": ["Infrastructure", "Web"]
}
```

### 目录接口响应结构

```json
["Database", "Infrastructure", "Web"]
```

- 目录接口直接读取目录表，按名称升序返回。
- 不再返回预制目录常量；只有真实存在的目录会出现在左侧。

## 写接口契约

### 新增请求体

```json
{
  "hostname": "prod-web-01",
  "ip": "10.0.0.9",
  "username": "root",
  "password": "TopSecret!9",
  "businessUnits": ["Infrastructure", "Web"]
}
```

- `status` 不接受前端创建时自定义，服务端固定写入 `online`。
- 前端可在同一次提交中：
  - 勾选已有目录
  - 再输入 1 个新的目录名
  - 最终统一合并进 `businessUnits`

### 编辑请求体

```json
{
  "hostname": "prod-web-01",
  "ip": "10.0.0.9",
  "username": "root",
  "password": "TopSecret!9",
  "businessUnits": ["Infrastructure", "Web"],
  "status": "maintenance"
}
```

## 校验规则

- `hostname`、`ip`、`username`、`password` 必填。
- `status` 仅允许：
  - `online`
  - `maintenance`
  - `offline`
- `hostname` 全局唯一（忽略前后空格与大小写）。
- `ip` 全局唯一（忽略前后空格与大小写）。
- `businessUnits` 为空时，服务端归一化为 `["Uncategorized"]`。
- `businessUnits` 中的目录名会做 trim、去空、去重。
- 目录表名称唯一性按忽略大小写处理。

## 错误语义

| 状态码 | 场景 |
|---|---|
| `401` | 未登录访问列表、详情或新增 |
| `403` | 非 admin 调用编辑或删除 |
| `404` | 记录不存在 |
| `400` | 字段缺失、状态非法、`hostname`/`ip` 冲突 |

## 持久化模型

### 主表：`workspace_linux_password_server`

| 字段 | 说明 |
|---|---|
| `id` | 雪花 ID |
| `hostname` | 主机名 |
| `ip` | IP 地址 |
| `username` | 登录用户名 |
| `password` | 明文密码（本期按内网工具约束保留） |
| `status` | `online` / `maintenance` / `offline` |
| `deleted` | 逻辑删除标记 |
| `create_time` / `update_time` | 审计字段 |

### 关联表：`workspace_linux_password_server_business_unit`

| 字段 | 说明 |
|---|---|
| `id` | 雪花 ID |
| `server_id` | 关联主表 |
| `business_unit` | 业务单元名称 |
| `deleted` | 逻辑删除标记 |
| `create_time` / `update_time` | 审计字段 |

### 目录表：`workspace_linux_password_directory`

| 字段 | 说明 |
|---|---|
| `id` | 雪花 ID |
| `name` | 目录名称 |
| `deleted` | 逻辑删除标记 |
| `create_time` / `update_time` | 审计字段 |

### 自动维护规则

- 创建机器：若 `businessUnits` 中包含目录表里不存在的名称，则自动补建目录记录。
- 编辑机器：先更新机器目录关联，再删除所有“已无任何机器引用”的目录记录。
- 删除机器：删除机器及其目录关联后，同样清理无引用目录。

## 源码映射

| 角色 | 文件 |
|---|---|
| Controller | `src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceLinuxPasswordController.java` |
| Directory Controller | `src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceLinuxPasswordDirectoryController.java` |
| Service | `src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceLinuxPasswordService.java` |
| DTO | `src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceLinuxPasswordDto.java` |
| Request DTO | `src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceLinuxPasswordUpsertRequest.java` |
| Entity | `src/main/java/com/support/server/supportrosterserver/entity/workspace/LinuxPasswordServerEntity.java` |
| Relation Entity | `src/main/java/com/support/server/supportrosterserver/entity/workspace/LinuxPasswordServerBusinessUnitEntity.java` |
| Directory Entity | `src/main/java/com/support/server/supportrosterserver/entity/workspace/LinuxPasswordDirectoryEntity.java` |
| Mapper | `src/main/java/com/support/server/supportrosterserver/mapper/LinuxPasswordServerMapper.java` |
| Directory Mapper | `src/main/java/com/support/server/supportrosterserver/mapper/LinuxPasswordDirectoryMapper.java` |
| Flyway | `src/main/resources/db/migration/V6__workspace_linux_passwords.sql` / `src/main/resources/db/migration/V7__workspace_linux_password_directories.sql` / `src/main/resources/db/migration/V8__workspace_linux_password_directories_backfill.sql` |

## 验证命令

- `cd support-roster-server && mvn -q -Dtest=WorkspaceLinuxPasswordServiceTest,WorkspaceAccountServiceTest,WorkspaceOverviewControllerTest test`
