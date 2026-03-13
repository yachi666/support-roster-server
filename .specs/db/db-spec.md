# DB 设计规则

## 适用范围

- 本文档适用于后续落地到数据库的所有业务表、关联表、字典表及其建表 DDL。
- 以下规则为强制规则；如需偏离，必须先更新本规范并说明例外原因。

## 强制规则

### 1. 主键生成策略

- 所有表的主键统一使用雪花算法生成。
- 在 Java/MyBatis-Plus 侧，统一使用框架内置主键策略，不再单独自研主键生成器。
- 实体主键字段应显式声明为 MyBatis-Plus 雪花 ID 策略，例如：`@TableId(type = IdType.ASSIGN_ID)`。
- 主键列类型统一使用可承载雪花 ID 的整型类型，推荐 `BIGINT`。
- 禁止把自增主键（如 `AUTO_INCREMENT`、`SERIAL`、序列自增）作为默认主键方案。

### 2. 审计时间字段

- 所有表必须包含以下两个审计字段：
  - `create_time`：创建时间
  - `update_time`：更新时间
- 以上字段由数据库负责维护，不依赖应用层手动赋值。
- 建表时应为 `create_time` 配置默认当前时间；`update_time` 应在插入时默认赋值，并在更新时由数据库自动刷新。
- 若目标数据库方言不支持 `ON UPDATE CURRENT_TIMESTAMP`，则应通过触发器或等效机制保证 `update_time` 自动更新。
- 任何新增表、历史表补建或结构迁移，均不得省略这两个字段。

### 3. DDL 存放位置

- 数据库建表语句统一维护在目录：`.specs/db/ddl`。
- 所有新表的 `CREATE TABLE` 语句必须落在该目录下，不要散落到其他 spec、注释或临时文档中。
- 建议按“初始化脚本 / 增量脚本”进行命名，保持可读性和可追踪性，例如：
  - `001_init_tables.sql`
  - `010_create_staff_table.sql`
  - `020_create_shift_table.sql`
- 若单次变更涉及多张表，可按变更批次组织，但仍需放在 `.specs/db/ddl` 目录下。

### 4. Spring Boot 运行时初始化脚本兼容性

- 若 `src/main/resources/schema.sql` 作为 Spring Boot 运行时初始化脚本执行，SQL 必须兼容 Spring JDBC `ScriptUtils` 的语句切分规则。
- PostgreSQL 的 `FUNCTION` / `TRIGGER` 定义若包含过程体内部分号，禁止直接在运行时初始化脚本中使用 `$$ ... $$` 形式并假定框架能自动识别完整函数体。
- 这类函数应改写为 Spring 初始化器可安全执行的形式，或拆分到专用迁移工具中执行，避免在应用启动阶段被错误切分。

## 落地检查清单

- [ ] 新表主键是否为雪花 ID。
- [ ] MyBatis-Plus 实体是否配置为内置雪花主键策略。
- [ ] 表结构是否包含 `create_time` / `update_time`。
- [ ] 时间字段是否由数据库自动维护。
- [ ] 对应建表 SQL 是否已存放到 `.specs/db/ddl`。
- [ ] 运行时 `schema.sql` 中的 PostgreSQL 函数/触发器定义是否兼容 Spring Boot 初始化器。

