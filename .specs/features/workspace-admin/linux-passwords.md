# Linux 密码库（workspace）接口规格

## 文档定位

本文定义 `/api/workspace/linux-passwords` 资源的接口、权限、持久化模型与前端联调约束。该资源服务于独立路由页 `/linux-passwords`，但后端仍归入 workspace 写能力体系维护。

## 能力边界

- 管理 Linux 主机的登录凭据、状态与业务单元归属。
- 为前端独立页面提供列表、详情、新增、编辑、删除能力。
- 提供独立目录接口，供前端左侧目录树单独加载。
- 支持同一台机器维护多个 Linux 登录账户。
- 密码只在用户点击查看或复制时由后端解密，并记录访问审计。

## 路由

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/workspace/linux-passwords` | 列表查询，支持 `search` / `businessUnit` |
| `GET` | `/api/workspace/linux-passwords/{id}` | 获取单条记录详情 |
| `GET` | `/api/workspace/linux-password-directories` | 获取左侧目录列表 |
| `GET` | `/api/workspace/linux-passwords/access-audits` | 管理员查询密码访问审计记录 |
| `POST` | `/api/workspace/linux-passwords` | 新增记录 |
| `POST` | `/api/workspace/linux-passwords/credentials/{credentialId}/secret` | 按需解密单个登录账户密码，并写入审计 |
| `PUT` | `/api/workspace/linux-passwords/{id}` | 编辑记录 |
| `DELETE` | `/api/workspace/linux-passwords/{id}` | 删除记录 |

## 权限规则

### 读取

- `GET` 列表与详情都要求已登录。
- 密码解密接口要求已登录，并从 `AuthContextService.requireLogin()` 解析当前 `accountId`、`staffRecordId`、`staffId`、`staffName` 后写入审计，禁止由前端传入 staff 标识。
- 审计查询接口仅允许 workspace `admin` 访问，统一复用 `AuthContextService.requireAdmin()`。
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
      "credentials": [
        {
          "id": "501",
          "username": "admin",
          "notes": "root maintenance",
          "hasPassword": true
        }
      ],
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
- 列表和详情接口不得返回明文密码，也不得返回密文字段；浏览器只获得账户元信息。

### 密码解密接口

请求：

```json
{
  "action": "VIEW"
}
```

- `action` 仅允许 `VIEW` 或 `COPY`。
- 后端先校验登录态，再读取 credential，最后使用服务端密钥解密。
- 无论是查看还是复制，只要后端返回明文密码，都必须写入 `workspace_linux_password_access_audit`。
- 解密失败（credential 不存在或密文损坏）时，也必须写入 `result=FAILED` 的审计记录；该写入在任何异常抛出前独立提交，不会被事务回滚。

响应：

```json
{
  "password": "Proxy@Infra99"
}
```

### 审计记录查询接口

`GET /api/workspace/linux-passwords/access-audits`

仅 `admin` 可访问。支持按多个维度组合过滤，并按访问时间倒序返回分页结果。

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `keyword` | `string` | 否 | 在员工、机器、登录账户、动作、结果、来源 IP 中模糊搜索 |
| `staffId` | `string` | 否 | 员工 ID 模糊匹配 |
| `staffName` | `string` | 否 | 员工姓名模糊匹配 |
| `hostname` | `string` | 否 | 主机名模糊匹配 |
| `ip` | `string` | 否 | IP 地址模糊匹配 |
| `username` | `string` | 否 | Linux 登录账户模糊匹配 |
| `action` | `string` | 否 | `VIEW` / `COPY` |
| `result` | `string` | 否 | `SUCCESS` / `FAILED` |
| `from` / `to` | `string` | 否 | 日期或日期时间范围 |
| `page` / `pageSize` | `number` | 否 | 默认 `1` / `20`，`pageSize` 最大 `100` |

响应结构：

```json
{
  "items": [
    {
      "id": "901",
      "accountId": "1",
      "staffRecordId": "11",
      "staffId": "U001",
      "staffName": "Alice Wang",
      "serverId": "1",
      "hostname": "infra-proxy-01",
      "ip": "10.0.1.2",
      "credentialId": "501",
      "username": "admin",
      "action": "COPY",
      "result": "SUCCESS",
      "clientIp": "127.0.0.1",
      "userAgent": "Mozilla/5.0",
      "createTime": "2026-04-27T10:00:00"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1
}
```

## 写接口契约

### 新增请求体

```json
{
  "hostname": "prod-web-01",
  "ip": "10.0.0.9",
  "credentials": [
    {
      "username": "root",
      "password": "TopSecret!9",
      "notes": "primary maintenance account"
    },
    {
      "username": "deploy",
      "password": "DeploySecret!9"
    }
  ],
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
  "credentials": [
    {
      "id": "501",
      "username": "root",
      "password": "",
      "notes": "primary maintenance account"
    }
  ],
  "businessUnits": ["Infrastructure", "Web"],
  "status": "maintenance"
}
```

- 编辑已有 credential 时，若 `password` 为空且带有 `id`，服务端保留原密文。
- 新增 credential 或创建机器时，`password` 必填。

## 校验规则

- `hostname`、`ip` 必填。
- 至少需要 1 个 `credentials` 项。
- credential 的 `username` 必填；创建新 credential 时 `password` 必填。
- 同一机器下 `username` 唯一（忽略前后空格与大小写）。
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
| `400` | 解密动作不是 `VIEW` / `COPY` |

## 持久化模型

### 主表：`workspace_linux_password_server`

| 字段 | 说明 |
|---|---|
| `id` | 雪花 ID |
| `hostname` | 主机名 |
| `ip` | IP 地址 |
| `username` | 历史兼容字段；新写入不再使用 |
| `password` | 历史兼容字段；新写入不再使用，运行时回填到 credential 表后加密保存 |
| `status` | `online` / `maintenance` / `offline` |
| `deleted` | 逻辑删除标记 |
| `create_time` / `update_time` | 审计字段 |

### 登录账户表：`workspace_linux_password_credential`

| 字段 | 说明 |
|---|---|
| `id` | 雪花 ID |
| `server_id` | 关联主机 |
| `username` | Linux 登录用户名 |
| `password_ciphertext` | 服务端 AES-GCM 加密后的密码密文 |
| `password_iv` | AES-GCM IV |
| `key_version` | 密钥版本，当前为 `v1` |
| `notes` | 账户备注 |
| `deleted` | 逻辑删除标记 |
| `create_time` / `update_time` | 审计字段 |

### 密码访问审计表：`workspace_linux_password_access_audit`

| 字段 | 说明 |
|---|---|
| `id` | 雪花 ID |
| `account_id` | workspace 账号记录 ID |
| `staff_record_id` | `workspace_staff.id` |
| `staff_id` | 员工 ID，即 workspace 登录账户 |
| `staff_name` | 员工姓名 |
| `server_id` | 被访问主机 |
| `credential_id` | 被访问登录账户 |
| `action` | `VIEW` / `COPY` |
| `result` | `SUCCESS` / `FAILED` |
| `client_ip` | 请求来源 IP |
| `user_agent` | 浏览器 User-Agent |
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
- 创建/编辑机器：登录账户写入 `workspace_linux_password_credential`，密码只保存密文。
- 编辑机器：先更新机器目录关联，再删除所有“已无任何机器引用”的目录记录。
- 删除机器：删除机器、登录账户及目录关联后，同样清理无引用目录。
- 若历史主表存在 `username/password` 且没有 credential 行，服务端读取列表/详情时会回填一条加密 credential。

## 密钥配置

| 环境 | 配置来源 | 说明 |
|---|---|---|
| 非 local 环境（生产/预发） | 环境变量 `SUPPORT_LINUX_PASSWORD_SECRET_KEY` | 必须显式设置；缺失则服务启动失败 |
| local 开发环境 | `application-local.yml` 中 `support.linux-passwords.secret-key` 固定值 | 仅用于开发，禁止在生产使用 |

- **不允许**回退到 JWT secret key（`SA_TOKEN_JWT_SECRET_KEY`）或任何硬编码默认值。
- `LinuxPasswordSecretService` 收到空白密钥时会抛出 `IllegalStateException`，使服务快速失败（fail-fast）。

## 源码映射

| 角色 | 文件 |
|---|---|
| Controller | `src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceLinuxPasswordController.java` |
| Directory Controller | `src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceLinuxPasswordDirectoryController.java` |
| Service | `src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceLinuxPasswordService.java` |
| Secret Service | `src/main/java/com/support/server/supportrosterserver/service/workspace/LinuxPasswordSecretService.java` |
| DTO | `src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceLinuxPasswordDto.java` |
| Credential DTO | `src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceLinuxPasswordCredentialDto.java` |
| Secret DTO | `src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceLinuxPasswordSecretRequest.java` / `WorkspaceLinuxPasswordSecretResponse.java` |
| Audit DTO | `src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceLinuxPasswordAccessAuditDto.java` / `WorkspaceLinuxPasswordAccessAuditListResponse.java` |
| Request DTO | `src/main/java/com/support/server/supportrosterserver/dto/workspace/WorkspaceLinuxPasswordUpsertRequest.java` |
| Entity | `src/main/java/com/support/server/supportrosterserver/entity/workspace/LinuxPasswordServerEntity.java` |
| Credential Entity | `src/main/java/com/support/server/supportrosterserver/entity/workspace/LinuxPasswordCredentialEntity.java` |
| Access Audit Entity | `src/main/java/com/support/server/supportrosterserver/entity/workspace/LinuxPasswordAccessAuditEntity.java` |
| Relation Entity | `src/main/java/com/support/server/supportrosterserver/entity/workspace/LinuxPasswordServerBusinessUnitEntity.java` |
| Directory Entity | `src/main/java/com/support/server/supportrosterserver/entity/workspace/LinuxPasswordDirectoryEntity.java` |
| Mapper | `src/main/java/com/support/server/supportrosterserver/mapper/LinuxPasswordServerMapper.java` |
| Credential Mapper | `src/main/java/com/support/server/supportrosterserver/mapper/LinuxPasswordCredentialMapper.java` |
| Access Audit Mapper | `src/main/java/com/support/server/supportrosterserver/mapper/LinuxPasswordAccessAuditMapper.java` |
| Directory Mapper | `src/main/java/com/support/server/supportrosterserver/mapper/LinuxPasswordDirectoryMapper.java` |
| Flyway | `src/main/resources/db/migration/V6__workspace_linux_passwords.sql` / `V7__workspace_linux_password_directories.sql` / `V8__workspace_linux_password_directories_backfill.sql` / `V11__workspace_linux_password_credentials_audit.sql` |

## 验证命令

- `cd support-roster-server && mvn -q -Dtest=WorkspaceLinuxPasswordServiceTest,LinuxPasswordSecretServiceTest,LinuxPasswordSecretServicePropertyWiringTest,WorkspaceAccountServiceTest,WorkspaceOverviewControllerTest test`
