# 第 6 章 功能专题目录

## 文档定位

本章按功能域组织专题规范，用于承接“已超出总体规范但又不属于单一领域主文档”的实现说明。当前分为 **workspace-admin**、**viewer** 与 **auth** 三个分册。

## 分册导航

| 分册 | 入口 | 适用场景 | 说明 |
|---|---|---|---|
| Workspace Admin | [workspace-admin/_index.md](./workspace-admin/_index.md) | 后台写能力、聚合页面、导入导出、校验中心 | `/api/workspace/**` |
| Viewer | [viewer/_index.md](./viewer/_index.md) | 公开查看页只读接口、只读 DTO 与兼容边界 | `/api/**` |
| Auth & Access Control | [auth/_index.md](./auth/_index.md) | 登录、账号生命周期、角色与 team 级授权 | `/api/auth/**` + `/api/workspace/**` |
| Workspace 总览旧入口 | [workspace-admin-backend.md](./workspace-admin-backend.md) | 快速建立后台专题上下文 | 作为总览页保留 |

## 当前专题结构

| 文档 | 角色 |
|---|---|
| [auth/_index.md](./auth/_index.md) | 登录、账号生命周期、角色与 team 级授权 |
| [workspace-admin/overview.md](./workspace-admin/overview.md) | workspace 跨资源总览 |
| [workspace-admin/dashboard-overview.md](./workspace-admin/dashboard-overview.md) | 工作台首页聚合接口 |
| [workspace-admin/staff.md](./workspace-admin/staff.md) | 人员目录 |
| [workspace-admin/shift-definitions.md](./workspace-admin/shift-definitions.md) | 班次定义 |
| [workspace-admin/teams.md](./workspace-admin/teams.md) | 团队管理 |
| [workspace-admin/roster.md](./workspace-admin/roster.md) | 月度排班 |
| [workspace-admin/validation.md](./workspace-admin/validation.md) | 校验中心 |
| [workspace-admin/import-export.md](./workspace-admin/import-export.md) | 导入导出 |
| [workspace-admin/linux-passwords.md](./workspace-admin/linux-passwords.md) | Linux 密码库凭据管理 |
| [workspace-admin/role-groups.md](./workspace-admin/role-groups.md) | 已废弃的历史兼容说明 |
| [viewer/overview.md](./viewer/overview.md) | viewer 总览 |
| [viewer/teams.md](./viewer/teams.md) | 团队接口 |
| [viewer/shifts.md](./viewer/shifts.md) | 排班接口 |
| [viewer/staff.md](./viewer/staff.md) | 人员接口 |
| [viewer/shift-codes.md](./viewer/shift-codes.md) | 班次编码接口 |
| [viewer/role-groups.md](./viewer/role-groups.md) | 已废弃的历史兼容说明 |

## 维护提示

- 同一能力若已拆到资源文档，不再把细节重新堆回总览页。
- 已废弃专题应短小、明确、可追溯，只保留迁移说明与替代入口。
