# DDL 目录说明

- 本目录统一存放数据库建表语句。
- 所有新表的 `CREATE TABLE` 语句必须维护在本目录中。
- 建议按“初始化脚本 / 增量脚本”进行命名，例如：
  - `001_init_tables.sql`
  - `010_create_staff_table.sql`
  - `020_create_shift_table.sql`
- 若某次变更涉及多张表，可按变更批次组织，但文件仍需位于本目录。
- 目录中的 SQL 应与 `.specs/db/db-spec.md` 保持一致。

