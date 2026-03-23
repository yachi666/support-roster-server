# Auth 上线部署说明

## 目标

本说明用于指导 Support Roster 认证与权限系统的首次上线，覆盖数据库准备、首个管理员引导、前后端发布以及上线后的收尾动作。

## 适用前提

- 后端已包含 `workspace_account`、`workspace_account_team_scope` 等认证表结构。
- 前端已包含 `/login`、路由守卫、账号管理页与按钮级权限限制。
- 本次方案使用本地密码登录 + `Sa-Token` `Authorization` Header。

## 推荐上线顺序

1. 备份数据库并确认当前 `workspace_staff` 主数据可用。
2. 执行初始化 SQL，补齐或校验首个管理员对应的 `workspace_staff` 记录。
3. 在后端配置 `SUPPORT_BOOTSTRAP_ADMIN_STAFF_CODE`，发布服务端。
4. 发布前端静态资源，并确认 `/login` 可访问。
5. 使用首个管理员 staffid 完成首次设密并登录。
6. 由该管理员创建其他 `admin / editor / readonly` 账号。
7. 删除 `SUPPORT_BOOTSTRAP_ADMIN_STAFF_CODE`，重新部署或重启后端。
8. 按首次验收清单完成回归。

## 数据库准备

### 1. 认证表结构

- 若当前环境尚未包含认证表，请先确认应用启动时已执行 Flyway 迁移，或手动执行与 `005_workspace_auth_tables.sql` 对齐的正式变更。
- 若环境已运行新版 schema，可跳过结构创建，只做数据核验。

### 2. 首个管理员 staff 准备

- 推荐使用 `db/ddl/006_auth_bootstrap_admin_seed.sql`。
- 该 SQL 的职责是确保 `workspace_staff.staff_code` 已存在，并为首个管理员提供稳定的 staff 主数据。
- **不要**在 SQL 中直接写入密码哈希；首登设密仍由应用完成。

## 后端部署配置

### 必要配置

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `SUPPORT_BOOTSTRAP_ADMIN_STAFF_CODE`

### 首次上线示例

```bash
export SUPPORT_BOOTSTRAP_ADMIN_STAFF_CODE=BOOTSTRAP_ADMIN_001
```

### 启动行为

当 `SUPPORT_BOOTSTRAP_ADMIN_STAFF_CODE` 存在时，后端启动会执行幂等引导：

- 若 staff 存在但还没有账号，则创建一个 `admin + PENDING_ACTIVATION` 账号。
- 若 staff 已有账号，则提升为 `admin`，并清理 editor 的 team 授权残留。
- 若 staffid 在 `workspace_staff` 中不存在，则启动失败，要求先修正主数据。

## 前端部署要点

- 确保 `VITE_API_BASE_URL` 指向正确的后端 API 根地址。
- 发布后至少验证以下入口：
  - `/viewer`
  - `/login`
  - `/workspace`

若使用容器部署，继续遵循 `support-roster-ui/.specs/deployment.md` 中的构建、Nginx 与 SPA 回退约束。

## 首个管理员交接步骤

1. 打开 `/login`。
2. 输入 `staffid`，即 `SUPPORT_BOOTSTRAP_ADMIN_STAFF_CODE` 对应值。
3. 按首登流程设置密码。
4. 进入 `/workspace/accounts`。
5. 创建至少一个备用管理员账号，避免单点人员风险。
6. 根据团队范围创建 `editor` 账号。
7. 按需创建 `readonly` 账号用于业务查看。

## 上线后清理

完成首个管理员接管后，必须移除：

- 环境变量 `SUPPORT_BOOTSTRAP_ADMIN_STAFF_CODE`

原因：

- 该配置用于首次引导或灾备恢复，不应作为长期常驻配置。
- 若长期保留，应用每次启动都会继续校正该 staff 对应账号为 `admin`。

## 回滚建议

若上线后需要临时回滚：

1. 先保留数据库中新增的账号表结构，不建议回删。
2. 回滚应用版本前，确认旧版本不会误读新增表。
3. 若仅需停止首个管理员引导，移除 `SUPPORT_BOOTSTRAP_ADMIN_STAFF_CODE` 后重启即可。

## 风险提示

- 当前首登设密方案仅适用于内网/测试阶段，正式生产前应补强激活校验。
- `readonly` 只读限制同时依赖后端权限和前端交互收口；上线后应按验收清单逐项验证。
- `editor` 的 team 范围是业务数据权限，不应通过前端隐藏按钮替代后端校验。
