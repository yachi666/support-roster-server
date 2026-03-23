# Auth & Access Control 分册

## 文档定位

本分册描述 `Support Roster Server` 的登录认证、账号生命周期、角色权限与 team 级数据范围控制。它覆盖 `Sa-Token` 接入、本地密码认证、账号管理接口，以及 `editor` 的按团队编辑边界。

## 阅读路径

| 目标 | 建议顺序 |
|---|---|
| 先理解整体边界 | [overview.md](./overview.md) |
| 评审接口与权限矩阵 | [api-and-permissions.md](./api-and-permissions.md) |
| 评审表结构与 SSO 预留 | [data-model.md](./data-model.md) |

## 文档目录

| 文档 | 主题 | 说明 |
|---|---|---|
| [overview.md](./overview.md) | 认证总览 | 登录方式、角色、登录态、首登设密与上线约束 |
| [api-and-permissions.md](./api-and-permissions.md) | 接口与权限矩阵 | 登录、登出、当前用户、账号管理、workspace 资源授权规则 |
| [data-model.md](./data-model.md) | 数据模型 | 账号表、team 授权表、状态机与未来 SSO 扩展字段 |
| [deployment-rollout.md](./deployment-rollout.md) | 上线部署 | 首次发布步骤、bootstrap admin 配置、交接与清理要求 |

## 维护提示

- 账号生命周期、角色定义、token 传递方式变化时，必须同步更新本分册。
- 若新增认证来源（例如公司 SSO），优先补充 `overview.md` 与 `data-model.md`，再补接口细节。
