# DDL Directory

[中文](./README.zh-CN.md)

## Purpose

This directory is the reviewed SQL baseline for database table definitions and incremental schema scripts. It complements the runtime Flyway migrations under `src/main/resources/db/migration/`.

## Usage Rules

- Keep `CREATE TABLE` statements for new tables in this directory.
- Prefer ordered, descriptive filenames, for example:
  - `001_init_tables.sql`
  - `010_create_staff_table.sql`
  - `020_create_shift_table.sql`
- SQL templates that are deployment-related or used for data bootstrap should also live here, for example `006_auth_bootstrap_admin_seed.sql`.
- When one change affects several tables, group it by the same batch number when that makes review easier.
- Keep SQL in this directory consistent with [`../db-spec.md`](../db-spec.md).
- Runtime migrations must also be represented under `src/main/resources/db/migration/`; this directory is a design and review baseline, not the application migration entry point.

## Maintenance Notes

- Put database rules in `db-spec.md` and concrete SQL in this directory.
- If a script changes table relationships, initialization assumptions, or migration behavior, update the upper-level database spec at the same time.
- Keep filenames stable once referenced by specs or reviews.
