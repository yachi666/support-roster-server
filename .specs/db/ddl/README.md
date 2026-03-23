# DDL 目录说明

## 文档定位

本目录统一存放数据库建表语句与增量迁移脚本，是数据库结构的正式 SQL 落点。

## 使用规则

- 所有新表的 `CREATE TABLE` 语句必须维护在本目录中。
- 建议按“初始化脚本 / 增量脚本”命名，例如：
  - `001_init_tables.sql`
  - `010_create_staff_table.sql`
  - `020_create_shift_table.sql`
- 与部署强相关、用于数据预置或上线引导的 SQL 模板，也应放在本目录统一维护，例如 `006_auth_bootstrap_admin_seed.sql`。
- 若单次变更涉及多张表，可按同一批次组织，但文件仍需位于本目录。
- 目录中的 SQL 应与 [../db-spec.md](../db-spec.md) 保持一致。

## 维护提示

- 规范写在 `db-spec.md`，SQL 落地写在本目录；两者缺一不可。
- 若脚本影响已有文档中的表关系或初始化方式，应同步更新上层 spec。
