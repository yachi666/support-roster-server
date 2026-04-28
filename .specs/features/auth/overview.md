# 登录认证与访问控制总览

## 目标

为 Support Roster 增加一套独立于排班主数据的认证与授权系统，同时保持 `/viewer` 匿名可访问、`/workspace/**` 必须登录，并为未来接入公司单点登录保留扩展空间。

## 范围边界

### 匿名可访问

- `GET /api/teams`
- `GET /api/shifts`
- `GET /api/staff`
- `GET /api/shift-codes`
- 前端 `/viewer`

### 需要登录

- 全部 `/api/workspace/**`
- 全部账号管理接口
- 前端 `/workspace/**`

## 认证方式

### 本期实现

- 使用 `Sa-Token` 作为登录态框架。
- 前后端通过 `Authorization` Header 传递 token。
- 后端统一从登录态解析当前账号，再驱动角色与 team 范围判断。

### 未来扩展

- 当前期仅启用 `LOCAL_PASSWORD` 认证来源。
- 账号模型必须预留 `auth_source`、`external_subject` 等字段，允许后续接入公司 SSO。
- 业务服务不得直接依赖“密码登录”本身，而应依赖统一的当前账号上下文。

## 登录标识与首登设密

- 登录标识使用 `workspace_staff.staff_id`，在产品语义上对应 `staffid`。
- 账号由管理员预创建，并关联到唯一 staff 记录。
- 员工首登时，如果账号尚未设置密码，则允许以 `staffid + 新密码` 完成激活。
- 该首登方案仅适用于内网/测试环境；正式上线前必须补充激活校验（例如激活码、邮箱验证或企业身份验证）。

## 初始管理员引导

- 为避免“系统已启用但还没有任何 admin 可登录”的死锁，后端允许通过配置项 `support.auth.bootstrap-admin-staff-id` 注入首个管理员 staff_id。
- 推荐通过环境变量 `SUPPORT_BOOTSTRAP_ADMIN_STAFF_ID` 在部署时显式设置。
- 当配置存在时，系统启动会执行以下幂等逻辑：
  - 若对应 staff 尚无账号，则创建一个 `admin + PENDING_ACTIVATION + LOCAL_PASSWORD` 的待激活账号；
  - 若已存在账号，则将其提升为 `admin`，并清理 editor team 授权残留；
  - 若 staffid 不存在于 `workspace_staff`，启动必须失败并暴露配置错误。
- 该配置建议仅用于首轮上线或灾备恢复；完成首个管理员登录与接管后，应移除该配置，避免后续启动持续覆盖账号角色。

## 角色模型

| 角色 | 说明 | 默认数据范围 |
|---|---|---|
| `admin` | 全局管理者 | 全量 workspace 数据 |
| `editor` | 可编辑受权团队数据 | 仅被授予的 team |
| `readonly` | 已登录只读用户 | 默认可查看全部 team |

## 授权原则

- 权限校验以后端为准，前端仅做导航隐藏、按钮禁用和提示优化。
- `editor` 的 team 范围控制属于业务数据权限，不能只靠角色字符串解决。
- `team` 主数据维护、账号管理、角色分配、team 授权只允许 `admin`。
- `editor` 对 staff、shift、roster、validation、import/export 的可写能力必须受 team 范围约束。

## 登录态生命周期

- 登录成功后返回 token 与当前用户快照。
- 前端保存 token，并在后续请求的 `Authorization` Header 中传递。
- `logout` 负责主动失效当前 token。
- 管理员禁用账号后，该账号应无法继续建立新会话；已登录会话可在后续增强中加入即时踢出能力。

## 审计要求

以下操作应写入操作日志：

- 登录成功
- 首登设密
- 管理员创建账号
- 管理员重置密码
- 管理员禁用/启用账号
- 角色变更
- editor team 授权变更

## 非目标

- 本期不实现公司 SSO 本身。
- 本期不支持匿名访问 workspace。
- 本期不支持 readonly 的按 team 细粒度限制；readonly 默认看全量。
