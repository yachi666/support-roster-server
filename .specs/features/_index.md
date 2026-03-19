# Feature Spec Index

## 范围

- 本目录用于承载按功能拆分的专题规范，例如导入导出、校验中心、排班生成、团队映射等。
- 当某项功能的实现、约束或流程已经超出通用主题文档的合理范围时，应在本目录下新增独立 spec。

## 当前文档

| 文档 | 说明 |
|------|------|
| `workspace-admin-backend.md` | Workspace 后台管理能力总览与资源文档入口 |
| `workspace-admin/_index.md` | Workspace 后台资源级规范导航 |
| `viewer/_index.md` | 公开查看页资源级规范导航 |

## 当前专题结构

- `workspace-admin/overview.md`：跨资源共性约束与核心表
- `workspace-admin/dashboard-overview.md`：首页总览聚合
- `workspace-admin/role-groups.md`：历史兼容说明（已废弃）
- `workspace-admin/staff.md`：人员目录
- `workspace-admin/shift-definitions.md`：班次定义
- `workspace-admin/teams.md`：团队管理
- `workspace-admin/roster.md`：月度排班
- `workspace-admin/validation.md`：校验中心
- `workspace-admin/import-export.md`：导入导出流程
- `viewer/overview.md`：viewer 只读接口边界与兼容约束
- `viewer/teams.md`：团队列表
- `viewer/shifts.md`：排班列表与详情
- `viewer/staff.md`：人员列表与详情
- `viewer/role-groups.md`：历史兼容说明（已废弃）
- `viewer/shift-codes.md`：班次编码列表