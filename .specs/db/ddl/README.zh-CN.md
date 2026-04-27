# DDL 目录说明

[English](./README.md)

## 文档定位

本目录统一存放经过审阅的数据库建表语句与增量结构脚本，并与运行时 Flyway 迁移目录 `src/main/resources/db/migration/` 互相补充。

## 使用规则

- 所有新表的 `CREATE TABLE` 语句必须维护在本目录中。
- 建议按有序且语义化的方式命名，例如：
  - `001_init_tables.sql`
  - `010_create_staff_table.sql`
  - `020_create_shift_table.sql`
- 与部署强相关、用于数据预置或上线引导的 SQL 模板，也应放在本目录统一维护，例如 `006_auth_bootstrap_admin_seed.sql`。
- 若单次变更涉及多张表，可按同一批次组织，便于审阅。
- 目录中的 SQL 应与 [`../db-spec.md`](../db-spec.md) 保持一致。
- 运行时执行的正式迁移也必须同步落到 `src/main/resources/db/migration/`；本目录承担设计与审阅基线，不替代应用实际迁移入口。

## 维护提示

- 规则写在 `db-spec.md`，具体 SQL 落地写在本目录。
- 如果脚本影响表关系、初始化前提或迁移行为，应同步更新上层数据库规范。
- 文件一旦被规范或评审引用，应尽量保持命名稳定。
