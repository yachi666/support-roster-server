# 第 5 章 数据库规范目录

## 文档定位

本章用于统一数据库设计规则、DDL 存放位置与初始化脚本兼容要求，面向所有未来表结构与增量脚本变更。

## 阅读建议

| 先读 | 再读 | 适用场景 |
|---|---|---|
| [db-spec.md](./db-spec.md) | [ddl/README.md](./ddl/README.md) | 设计新表、补充审计字段、确定 DDL 命名方式 |
| [db-spec.md](./db-spec.md) | `ddl/*.sql` | 需要核对现有建表或迁移脚本 |

## 目录清单

| 文档 / 目录 | 角色 | 重点内容 |
|---|---|---|
| [db-spec.md](./db-spec.md) | 数据库主规范 | 雪花主键、审计字段、DDL 存放与 Flyway 启动迁移约束 |
| [ddl/README.md](./ddl/README.md) / [中文](./ddl/README.zh-CN.md) | DDL 说明 | 目录用途、命名与维护方式 |
| `ddl/001_init_workspace_tables.sql` | 初始化脚本 | workspace 核心表初始结构 |
| `ddl/002_workspace_team_migration.sql` | 增量脚本 | team 维度迁移 |
| `ddl/003_workspace_shift_definition_team_rel.sql` | 增量脚本 | 班次定义与团队多对多关联 |
| `ddl/004_workspace_shift_definition_conflict_audit_cleanup.sql` | 增量脚本 | 冲突审计清理 |
| `ddl/005_workspace_auth_tables.sql` | 增量脚本 | 账号、team 授权与 SSO 预留字段 |
| `ddl/006_auth_bootstrap_admin_seed.sql` | 初始化模板 | 首个管理员 staff 数据准备与引导说明 |
| `ddl/007_workspace_linux_passwords.sql` | 增量脚本 | Linux 密码库主表、目录表与业务单元关联表 |
| `ddl/008_workspace_linux_password_credentials_audit.sql` | 增量脚本 | Linux 密码库多登录账户、密码密文与访问审计表（含 Task 2 快照列：hostname_snapshot、ip_snapshot、username_snapshot）|
| `ddl/009_workspace_staff_id_rename.sql` | 增量脚本 | 将员工业务标识统一为 staff_id，账号 FK 改为 staff_record_id |
| `ddl/010_workspace_shift_definition_team_display_order.sql` | 增量脚本 | 为班次定义-团队关联补充 TEAM 维度展示顺序 |

## 维护提示

- 表结构规则写在 `db-spec.md`，具体 SQL 放在 `ddl/`；不要把正式 DDL 散落到其他 spec 中。
- 若运行时迁移方式变化，必须同步更新 `db-spec.md` 中关于 Flyway 启动迁移的说明。
