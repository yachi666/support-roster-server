# 接口与权限矩阵

## 认证接口

| 接口 | 方法 | 说明 | 匿名可调 |
|---|---|---|---|
| `/api/auth/login` | `POST` | 使用 `staffId` 登录；未激活账号可走首登设密 | 是 |
| `/api/auth/logout` | `POST` | 注销当前登录态 | 否 |
| `/api/auth/me` | `GET` | 返回当前用户、角色、team 范围与权限摘要 | 否 |
| `/api/auth/change-password` | `POST` | 当前用户修改自己的密码 | 否 |

## 账号管理接口

| 接口 | 方法 | 说明 | 角色要求 |
|---|---|---|---|
| `/api/workspace/accounts` | `GET` | 账号列表，支持按 staffId / 姓名 / 状态搜索 | `admin` |
| `/api/workspace/accounts/{id}` | `GET` | 账号详情 | `admin` |
| `/api/workspace/accounts` | `POST` | 创建账号并绑定 staff | `admin` |
| `/api/workspace/accounts/{id}` | `PUT` | 更新角色、状态、备注、team 授权 | `admin` |
| `/api/workspace/accounts/{id}/reset-password` | `POST` | 重置为未激活或指定临时密码策略 | `admin` |
| `/api/workspace/accounts/{id}/enable` | `POST` | 启用账号 | `admin` |
| `/api/workspace/accounts/{id}/disable` | `POST` | 禁用账号 | `admin` |

## workspace 资源权限矩阵

| 资源 | `admin` | `editor` | `readonly` |
|---|---|---|---|
| Overview | 读 | 读 | 读 |
| Staff | 全量读写 | 仅授权 team 读写 | 只读 |
| Shift Definitions | 全量读写 | 仅授权 team 读写 | 只读 |
| Teams | 全量读写 | 只读或拒绝 | 只读或拒绝 |
| Monthly Roster | 全量读写 | 仅授权 team 读写 | 只读 |
| Validation | 全量处理 | 仅授权 team 处理 | 只读 |
| Import / Export | 全量执行 | 仅授权 team 执行 | 只读导出 |
| Accounts | 全量读写 | 拒绝 | 拒绝 |

## Team 范围规则

### editor

- 账号上保存一组可编辑 `team_id`。
- staff 创建、更新、删除时，目标 staff 的 `team_id` 必须属于该账号的授权列表。
- shift definition 创建、更新、删除时，涉及的全部 `teamIds` 必须属于该账号的授权列表。
- roster 保存时，写入 payload 内涉及的全部 `teamId` / `staffId` 所属 team 必须在授权范围内。
- import preview 与 import apply 需要对实际落地的 team 集合做权限校验，避免批量跨 team 修改。
- validation resolve 仅允许处理授权 team 的问题。

### readonly

- 默认可读取全部 team 数据。
- 不允许任何写操作。

## 错误语义

| 场景 | 状态码 | 说明 |
|---|---|---|
| 未登录 | `401` | token 缺失、失效或解析失败 |
| 已登录但角色不足 | `403` | 例如 editor 访问账号管理 |
| 已登录但 team 范围不足 | `403` | 例如 editor 修改未授权 team 数据 |
| 账号被禁用 | `403` | 当前账号状态不允许登录或继续操作 |
| 首登设密参数非法 | `400` | staffId 不存在、密码不符合规则等 |

## `me` 返回约定

`/api/auth/me` 应至少返回：

- `accountId`
- `staffId`
- `staffName`
- `role`
- `status`
- `editableTeamIds`
- `editableTeams`
- `permissions`
- `authSource`

## 前后端协作要求

- 前端仅通过 `/api/auth/me` 建立当前用户视图，不自行拼接权限。
- 页面级按钮禁用与路由访问控制应基于 `role + permissions + editableTeamIds`。
- 后端 Service 层必须再次校验，禁止只在 Controller 或前端做授权判断。
