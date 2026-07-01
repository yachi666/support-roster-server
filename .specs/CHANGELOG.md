# 规范变更记录

## 2026-07-01 - 员工自助注册与自动团队授权

### 变更背景

为降低管理员维护成本，员工可直接使用 `staffId` 自行注册登录，无需管理员预创建账号。自助注册的账号自动获得 `editor` 角色并授予其所在团队的编辑权限，使员工能自行调整本团队排班。

### 变更文件

1. `src/main/java/.../service/auth/AuthService.java` — 增强 `activate()` 方法
2. `.specs/features/auth/overview.md`
3. `.specs/features/auth/api-and-permissions.md`
4. `.specs/features/auth/data-model.md`
5. `.specs/CHANGELOG.md`

### 详细变更记录

#### 后端 — AuthService.java

- 重写 `activate()` 方法：
  - 先查 `workspace_account` 是否有已有账号。若存在且为 `PENDING_ACTIVATION`，走原有激活流程。
  - 若不存在，查 `workspace_staff`。找到员工记录后，自动创建 `editor` 角色、`ACTIVE` 状态的新账号。
  - 自动写入 `workspace_account_team_scope`，授权范围 = 员工的 `teamId`。
  - `authSource` 设为 `"self-registered"`，与管理员创建的 `"LOCAL_PASSWORD"` 区分。
- 注入 `WorkspaceAccountTeamScopeMapper` 以支持 team scope 写入。

#### 前端 — LoginPage.vue & i18n

- 更新激活页面提示文案，说明新员工可直接用 staffId 自助注册。
- 激活 Tab 标签从"首次激活"改为"首次登录"。
- i18n 中英文文案同步更新。

#### Spec 文档

- `overview.md`：新增"自助注册"章节，详细描述注册流程与约束。
- `api-and-permissions.md`：更新 `/api/auth/activate` 接口说明，补充自助注册场景下的错误语义。
- `data-model.md`：状态机新增 `[*] → ACTIVE` 路径，`authSource` 补充 `self-registered` 可取值。

### 影响评估

- 自助注册仅依赖于 `workspace_staff` 中已有员工记录，不要求任何在先管理员操作。
- 管理员预创建的 `PENDING_ACTIVATION` 流程完全保留，不受影响。
- 已存在的 `ACTIVE` 账号不受影响，自助注册仅对"无账号 + 有 staff 记录"的场景生效。
- 若员工无团队（`teamId = null`），自助注册后仍可登录，但无任何可写团队（等价于 readonly 能力）。
- 审计日志新增 `"Self-register workspace account"` 操作类型。

### 代码审查修复（2026-07-01 追加）

代码审查发现三项安全问题并修复：

1. **软删除绕过（Fix 1）**：`@TableLogic` 配合部分唯一索引导致已软删除的账号对 `selectOne` 不可见，自助注册可绕过下线状态。已改为使用 `selectAnyByStaffId()` 绕过 `@TableLogic` 查询全部账号，检测到 `deleted = 1` 时拒绝注册。
2. **并发竞态（Fix 2）**：并发请求通过账号存在性检查后同时执行 insert，违反唯一约束返回 500。已通过 `catch DataIntegrityViolationException` 转为友好错误提示。
3. **员工状态校验（Fix 3）**：未检查 `staff.status`，非激活员工也可注册。已补充 `staff.getStatus()` 校验。

#### 变更文件

1. `src/main/java/.../mapper/WorkspaceAccountMapper.java` — 新增 `selectAnyByStaffId()` 绕过 @TableLogic 查询
2. `src/main/java/.../service/auth/AuthService.java` — 三项安全修复
3. `.specs/features/auth/overview.md`
4. `.specs/features/auth/api-and-permissions.md`
5. `.specs/CHANGELOG.md`

## 2026-05-01 - 0.0.2 发版准备

### 变更背景

当前 `main` 分支已准备作为 `0.0.2` 发布基线，需要先将 Maven 项目版本从开发态 `0.0.1-SNAPSHOT` 切换为正式发布版本。

### 变更文件

1. `pom.xml`
2. `.specs/CHANGELOG.md`

### 详细变更记录

- 将 Maven 项目版本从 `0.0.1-SNAPSHOT` 更新为 `0.0.2`。
- 保持其余运行时依赖、框架基线与接口/数据库规范不变，本次仅执行发布前版本固化。

### 影响评估

- 后续基于当前 `main` 提交创建 tag / release 时，仓库中的 Maven 坐标与目标发布版本保持一致。
- 本次变更不引入业务行为差异。

## 2026-03-21 - .specs 技术手册化重排

### 变更背景

为提升 `.specs/` 的目录感与可读性，本次将 server 侧规范进一步整理为“技术手册 / 规格书”风格，统一入口页、专题页的阅读路径，并为关键资源补充 Mermaid 图示。

### 变更文件

1. `.specs/_index.md`
2. `.specs/api/_index.md`
3. `.specs/domain/_index.md`
4. `.specs/data/_index.md`
5. `.specs/constraints/_index.md`
6. `.specs/db/_index.md`
7. `.specs/features/_index.md`
8. `.specs/features/workspace-admin/_index.md`
9. `.specs/features/viewer/_index.md`
10. `.specs/features/workspace-admin/overview.md`
11. `.specs/features/workspace-admin/roster.md`
12. `.specs/features/workspace-admin/import-export.md`
13. `.specs/features/viewer/overview.md`
14. `.specs/features/workspace-admin-backend.md`
15. `.specs/db/db-spec.md`
16. `.specs/CHANGELOG.md`

### 详细变更记录

- 根入口与各级 `_index.md` 统一改为章节目录式导航，强化“先总览、再分册、再资源页”的阅读路径。
- `workspace-admin/overview.md` 增加资源关系图，明确后台资源之间的依赖与协作。
- `workspace-admin/roster.md` 增加查询、编辑、保存、校验与回包流程图。
- `workspace-admin/import-export.md` 增加导入预览、应用与导出链路图。
- `viewer/overview.md` 增加只读边界图，明确 viewer 与 workspace 的接口边界。
- `db/db-spec.md` 保留为数据库主规范，并以图示说明规则、DDL 与初始化脚本的关系。

### 影响评估

- 目录导航更接近“规范书目录”，降低首次阅读成本。
- 关键跨资源流程更直观，适合新成员快速建立系统心智模型。
- 文档语言与章节结构更统一，后续维护成本更低。

## 2026-03-13 - Spring Boot 4 全局 CORS 配置收敛

### 变更背景

原有跨域实现使用手工注册的 `CorsFilter`，并允许任意来源携带凭证访问，配置过宽且不符合当前 Spring Boot 4 / Spring MVC 场景下更推荐的全局配置方式。

### 变更文件

1. `src/main/java/com/support/server/supportrosterserver/config/CorsConfig.java`
2. `src/test/java/com/support/server/supportrosterserver/config/CorsConfigTest.java`
3. `.specs/constraints-and-conventions.md`
4. `.specs/CHANGELOG.md`

### 详细变更记录

- 将 `CorsConfig` 从手工 `CorsFilter` 切换为 `WebMvcConfigurer#addCorsMappings`，与 Spring Boot 4 的 Spring MVC 全局配置方式保持一致。
- 跨域范围收敛为 `/api/**`，仅对业务 API 开放，而非对全部路径开放。
- 允许任意来源访问 API，但显式关闭 `allowCredentials`，避免“任意来源 + 凭证”带来的安全风险。
- 新增基于 `MockMvc` 的预检请求与实际请求测试，验证任意前端来源均可跨域访问 `/api/**`。

### 影响评估

- 所有前端站点均可直接访问当前 API。
- 跨域策略更符合 Spring Boot 4 的推荐实践，且安全边界比原实现更清晰。
- 如后续需要跨域 Cookie / Session，必须改为显式来源白名单策略。

## 2026-03-12 - 导入模版下载与验证规则优化

### 变更背景

为提升导入体验，新增模版下载接口，并优化导入验证规则，将 "Missing Primary Coverage" 从阻止性问题改为警告。

### 变更文件

1. `src/main/java/com/support/server/supportrosterserver/controller/workspace/WorkspaceImportExportController.java`
2. `src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceImportService.java`
3. `src/main/resources/roster.xlsx`
4. `.specs/features/workspace-admin/import-export.md`
5. `.specs/CHANGELOG.md`

### 详细变更记录

- 新增 `GET /api/workspace/import-export/template` 接口，返回预生成的 Excel 模版文件。
- 重新生成 `roster.xlsx` 模版，包含 3 个独立 sheet（Shift Definitions、Staff Shifts、Color Definitions）。
- 优化导入验证规则：
  - 将 "Missing Primary Coverage" 问题严重级别从 `high` 改为 `low`。
  - 批次状态判定：仅 `high` 或 `medium` 级别问题阻止导入，`low` 级别允许导入。
- 新增数据过滤规则，自动跳过无效行（标题行、空行、非班次定义行）。
- 容错处理：Color Definitions sheet 缺失时继续处理其他 sheet。

### 影响评估

- 导入模版格式规范，减少用户填写错误。
- "Missing Primary Coverage" 不再阻止导入，提升用户体验。
- 模版下载接口为前端提供标准导入格式。

## 2026-03-11 - schema.sql 启动初始化兼容性修复

### 变更背景

应用在连接 PostgreSQL 后，`src/main/resources/schema.sql` 中的触发器函数使用 `$$ ... $$` 过程体，导致 Spring Boot 自带 SQL 初始化器在启动阶段按分号错误切分语句，服务无法完成数据库初始化。

### 变更文件

1. `src/main/resources/schema.sql`
2. `.specs/db/db-spec.md`
3. `.specs/CHANGELOG.md`

### 详细变更记录

- 将 `schema.sql` 中 `set_update_time()` 的 PostgreSQL 函数体改为 Spring 初始化器可正确执行的单引号形式，避免过程体内分号被误切分。
- 在 `.specs/db/db-spec.md` 中新增“Spring Boot 运行时初始化脚本兼容性”规则，明确运行时 `schema.sql` 中 PostgreSQL `FUNCTION` / `TRIGGER` 的写法约束。

### 影响评估

- 本地空库初始化流程可继续保留在 Spring Boot 启动阶段执行。
- 后续新增 PostgreSQL 触发器或函数时，有明确规范避免再次引入同类启动失败。

## 2026-03-11 - 资源级接口评审映射补全

### 变更背景

为让资源规范更适合接口评审，需要把 spec 从“只描述接口范围”进一步提升为“可直接对照 OpenAPI、controller、service、DTO 和请求字段”的结构化文档。

### 变更文件

1. `.specs/features/viewer/_index.md`
2. `.specs/features/viewer/overview.md`
3. `.specs/features/viewer/teams.md`
4. `.specs/features/viewer/shifts.md`
5. `.specs/features/viewer/staff.md`
6. `.specs/features/viewer/role-groups.md`
7. `.specs/features/viewer/shift-codes.md`
8. `.specs/features/workspace-admin/dashboard-overview.md`
9. `.specs/features/workspace-admin/role-groups.md`
10. `.specs/features/workspace-admin/staff.md`
11. `.specs/features/workspace-admin/shift-definitions.md`
12. `.specs/features/workspace-admin/teams.md`
13. `.specs/features/workspace-admin/roster.md`
14. `.specs/features/workspace-admin/validation.md`
15. `.specs/features/workspace-admin/import-export.md`
16. `.specs/features/_index.md`
17. `.specs/_index.md`
18. `.specs/CHANGELOG.md`

### 详细变更记录

- 新增 `features/viewer/` 目录，按 viewer 资源拆分团队、排班、人员、角色组与班次编码规范。
- 为 viewer 资源文档补充对应 OpenAPI path 文件、controller、service、DTO 交叉链接。
- 为 viewer 与 workspace 资源文档补充“请求字段与 DTO 字段映射”表。
- 对聚合型资源额外补充子 DTO 字段映射，便于接口评审直接核对响应结构。
- 更新功能专题索引与根入口导航，使 viewer 与 workspace 资源级规范并列可见。

### 影响评估

- 资源 spec 已可直接用于接口评审和契约核对。
- OpenAPI、代码与规范之间的差异更容易被逐项发现。
- 后续 DTO 字段变更可以更精确地回溯到受影响资源文档。

## 2026-03-11 - 新增 Viewer 资源级规范

### 变更背景

viewer 接口虽然已在 `api-standard.md` 中有总体说明，但缺少按资源拆分的规范文档，无法像 workspace 一样直接对照 OpenAPI 契约与 Java 实现。

### 变更文件

1. `.specs/features/viewer/_index.md`
2. `.specs/features/viewer/overview.md`
3. `.specs/features/viewer/teams.md`
4. `.specs/features/viewer/shifts.md`
5. `.specs/features/viewer/staff.md`
6. `.specs/features/viewer/role-groups.md`
7. `.specs/features/viewer/shift-codes.md`
8. `.specs/features/_index.md`
9. `.specs/_index.md`

### 详细变更记录

- 新增 `features/viewer/` 目录，按 viewer controller 拆分团队、排班、人员、角色组与班次编码规范。
- 为每个 viewer 资源文档补充对应 OpenAPI path 文件和 controller/service/DTO 链接。
- 更新功能专题索引与根入口，使 viewer 与 workspace 的资源级规范并列可导航。

### 影响评估

- viewer 契约、代码与 spec 可以按资源逐项对照。
- 后续 viewer 行为变更不必继续堆叠到 `api-standard.md` 的长文中。

## 2026-03-11 - WorkspaceImportService 告警清理

### 变更背景

`WorkspaceImportService` 在修复 Jackson 编译问题后仍保留若干 IDE 告警，包括可推断泛型、未使用的中间映射、以及始终为 `null` 的 `importRecordId` 形参。这些代码没有实际业务价值，增加了维护噪音。

### 变更文件

1. `src/main/java/com/support/server/supportrosterserver/service/workspace/WorkspaceImportService.java`
2. `.specs/CHANGELOG.md`

### 详细变更记录

- 移除 `Wrappers.lambdaQuery()` 上可省略的显式类型实参。
- 删除预览流程中未被使用的 `recordIdByPayload` 映射。
- 删除应用流程中未被使用的 `COLOR` 预解析与 `colorHexByCode` 中间映射，继续直接使用 `SHIFT_DEFINITION` 记录中的 `colorHex` 载荷。
- 删除 `buildIssue(...)` 中始终传入 `null` 的 `importRecordId` 形参及对应赋值。

### 影响评估

- `WorkspaceImportService` 文件级编译告警已清空。
- 导入预览与应用行为保持不变，代码路径更直接。

## 2026-03-11 - StaffService 遗留映射清理

### 变更背景

`StaffService` 已切换为直接委托 `WorkspaceStaffService` 返回 `StaffDto`，但类中仍残留旧版 `convertToDto(Staff)` 私有方法，引用了当前工程中未使用的 `Staff` 类型，导致编译失败。

### 变更文件

1. `src/main/java/com/support/server/supportrosterserver/service/StaffService.java`
2. `.specs/CHANGELOG.md`

### 详细变更记录

- 删除 `StaffService` 中未被调用的 `convertToDto(Staff)` 遗留辅助方法。
- 删除随之失效的 `Collectors` import。
- 保持 `StaffService` 仅作为 viewer 入口，对外继续委托 `WorkspaceStaffService` 提供数据。

### 影响评估

- 消除 `Staff` 符号无法解析的编译错误。
- 避免 viewer staff 映射逻辑在两个 service 中重复维护。

## 2026-03-11 - Workspace Spec 按资源拆分

### 变更背景

原 `.specs/features/workspace-admin-backend.md` 同时承载总览、数据模型、接口分组、导入流程和校验规则，信息密度过高，不利于按单个资源持续维护。

### 变更文件

1. `.specs/features/workspace-admin-backend.md`
2. `.specs/features/workspace-admin/_index.md`
3. `.specs/features/workspace-admin/overview.md`
4. `.specs/features/workspace-admin/dashboard-overview.md`
5. `.specs/features/workspace-admin/role-groups.md`
6. `.specs/features/workspace-admin/staff.md`
7. `.specs/features/workspace-admin/shift-definitions.md`
8. `.specs/features/workspace-admin/teams.md`
9. `.specs/features/workspace-admin/roster.md`
10. `.specs/features/workspace-admin/validation.md`
11. `.specs/features/workspace-admin/import-export.md`
12. `.specs/features/_index.md`
13. `.specs/_index.md`

### 详细变更记录

- 保留 `workspace-admin-backend.md` 作为总览入口，不再堆叠全部资源细节。
- 新增 `features/workspace-admin/` 目录，并按资源拆分后台规范。
- 将总览、角色组、人员、班次定义、团队、排班、校验、导入导出分别落到独立文档。
- 更新 `features/_index.md` 与根入口 `_index.md`，补充新的导航结构。

### 影响评估

- workspace 后台 spec 更容易按资源独立演进。
- 后续 controller 或接口变更时，变更范围能收敛到对应单文档。
- 旧入口文档仍然保留，避免已有引用失效。

## 2026-03-11 - WorkspaceImportService JSON 依赖修复

### 变更背景

`WorkspaceImportService` 新增 `ObjectMapper` 读写导入记录 JSON 后，编译期出现 `com.fasterxml.jackson.*` 包无法解析的问题。排查确认导入语句本身正确，根因是当前 `pom.xml` 未显式声明 JSON starter，导致 Jackson 不在编译类路径中。

### 变更文件

1. `pom.xml`
2. `.specs/_index.md`
3. `.specs/constraints-and-conventions.md`

### 详细变更记录

- 在 `pom.xml` 中新增 `spring-boot-starter-json`，为 `WorkspaceImportService` 提供 `ObjectMapper` 与 Jackson 相关类型。
- 在 `.specs/_index.md` 的关键依赖清单中补充 JSON starter。
- 在 `.specs/constraints-and-conventions.md` 中新增 Spring Boot 4 JSON 依赖约束，要求使用 Jackson 时显式检查并声明 JSON starter。

### 影响评估

- `WorkspaceImportService` 中的 Jackson 导入恢复可编译。
- 后续新增 JSON 序列化逻辑时，依赖约束更明确，可减少同类编译问题。

## 2026-03-11 - OpenAPI 按 Controller 拆分

### 变更背景

原 `api/openapi.yaml` 已包含全部 viewer 与 workspace 契约，文件体积持续增长，不利于按 controller 维护，也不利于在 workspace 管理接口扩展时控制修改范围。

### 变更文件

1. `api/openapi.yaml`
2. `api/components/common.yaml`
3. `api/paths/viewer/*.yaml`
4. `api/paths/workspace/*.yaml`
5. `.specs/api/openapi-layout.md`
6. `.specs/api/_index.md`
7. `.specs/api-standard.md`
8. `.specs/_index.md`

### 详细变更记录

- 将 `api/openapi.yaml` 调整为聚合入口，仅保留元信息、标签与路径引用。
- 新增 `api/components/common.yaml`，统一承载共享参数、响应与 schema。
- 按 controller 将 viewer 接口拆分到 `api/paths/viewer/`。
- 按 controller 将 workspace 接口拆分到 `api/paths/workspace/`，明确独立目录边界。
- 新增 `.specs/api/openapi-layout.md`，记录目录结构、controller 映射与维护规则。
- 更新 `.specs/api/_index.md`、`.specs/api-standard.md` 与 `.specs/_index.md`，使规范导航与当前契约文件结构保持一致。

### 影响评估

- OpenAPI 修改将集中在对应 controller 文件，减少大文件冲突。
- workspace 接口契约目录边界清晰，便于后续继续扩展管理后台。
- 共享 schema 集中后，重复结构更容易统一维护。

## 2026-03-11 - 新增 Workspace 管理后台后端规范

### 变更背景

为匹配已完成的 workspace 管理端前端界面，后端从原 Excel / 内存读取模式扩展为 PostgreSQL + MyBatis-Plus 的可写管理后台，并补充对应 API 契约与功能专题规范。

### 变更文件

1. `api/openapi.yaml`
2. `.specs/features/workspace-admin-backend.md`
3. `.specs/features/_index.md`
4. `.specs/api/_index.md`
5. `.specs/api-standard.md`
6. `.specs/_index.md`

### 详细变更记录

- 重写 `api/openapi.yaml`：补充 `/api/workspace/**` 全量接口，并保留 viewer 相关接口契约。
- 新增 `.specs/features/workspace-admin-backend.md`：记录 workspace 管理后台的接口范围、数据库模型、导入预览/应用流程、校验规则与兼容策略。
- 更新 `.specs/features/_index.md`：登记新的功能专题文档。
- 更新 `.specs/api/_index.md`：将 workspace 管理接口规范纳入 API 索引。
- 更新 `.specs/api-standard.md`：补充管理接口命名空间和接口总览。
- 更新 `.specs/_index.md`：将项目概述从 Excel 驱动修正为 PostgreSQL + MyBatis-Plus + Excel 导入并存的现状。

### 影响评估

- 契约文档已覆盖当前后端主要实现范围。
- 管理后台接口与 viewer 接口的边界更清晰。
- 后续如果引入鉴权，可在 `/api/workspace/**` 维度继续演进而不影响 viewer 只读接口。

## 2026-03-11 - 新增数据库设计规范

### 变更背景

为统一后续数据库落地约束，补充数据库主键生成策略、审计时间字段要求，以及建表 DDL 的集中管理规则。

### 变更文件

1. `.specs/db/db-spec.md`
2. `.specs/db/_index.md`
3. `.specs/db/ddl/README.md`
4. `.specs/_index.md`

### 详细变更记录

- 在 `.specs/db/db-spec.md` 中新增数据库设计强制规则：
  - 所有表主键统一使用雪花算法生成。
  - MyBatis-Plus 实体统一使用内置雪花主键策略。
  - 所有表必须包含 `create_time`、`update_time` 且由数据库自动维护。
  - 所有建表 SQL 统一存放到 `.specs/db/ddl`。
- 新增 `.specs/db/_index.md`，作为数据库规范主题索引。
- 新增 `.specs/db/ddl/README.md`，明确 DDL 目录用途与命名建议。
- 在 `.specs/_index.md` 中补充数据库规范主题导航。

## 2026-03-11 - Spec 目录导航结构整理

### 变更背景

为落实仓库级规范，明确 `.specs/` 作为唯一正式 spec 目录，并为后续文档持续拆分提供清晰入口和层级导航。

### 变更文件

1. `.specs/_index.md`
2. `.specs/api/_index.md`
3. `.specs/domain/_index.md`
4. `.specs/data/_index.md`
5. `.specs/constraints/_index.md`
6. `.specs/features/_index.md`

### 详细变更记录

- 将根入口 `_index.md` 调整为“总览 + 一级目录导航”结构。
- 新增 `api/`、`domain/`、`data/`、`constraints/`、`features/` 五个主题目录及各自 `_index.md`。
- 在主题索引中统一引用现有根级 spec，避免直接搬迁文件带来的大范围链接失效。
- 明确后续新增 spec 应优先进入对应主题目录，并通过 `_index.md` 维护导航。

### 影响评估

- `.specs/` 目录结构更适合持续演进。
- 后续 spec 可以按主题扩展，减少扁平堆积。
- 保留现有文档路径，避免一次性迁移带来的引用风险。

## 2026-03-05 - Spec 审计修复

### 变更背景
基于第三方审计结论，对 `.specs/` 文档进行“与当前代码实现对齐”的修复，重点覆盖：

- 业务逻辑边界条件
- API 行为与返回结构
- Excel 作为数据源时的数据质量与运行时约束
- OpenAPI 与 Controller 的契约漂移提示

---

### 变更文件

1. `.specs/domain-logic.md`
2. `.specs/api-standard.md`
3. `.specs/data-architecture.md`

---

### 详细变更记录

#### 1) `.specs/domain-logic.md`

- 修正 `Staff.avatar` 描述：
  - 旧：当前为占位图。
  - 新：可为空，当前仓库未在 `Staff` 实体填充默认头像。
- 修正并细化“按日期获取排班流程”中的 `teamId` 行为：
  - 明确后端仅取首个匹配 `roleGroup`。
  - 增加“未匹配到 roleGroup 时回退到查询全部排班”的分支。
- 补充角色组与团队映射的实现细节：
  - 明确同一 `teamId` 可能对应多个 `roleGroup`，当前实现不做聚合。
- 修正班次过滤规则：
  - 旧：要求 `ShiftDefinition.showOnRosterPage = true`。
  - 新：仅在 `ShiftDefinition` 存在时才判断该条件。
- 新增“边界条件（当前实现）”小节：
  - `teamId` 未命中回退为全量查询。
  - 非法 `timezone` 触发异常并返回 `500`。
  - `ShiftDefinition` 缺失时仍可能返回班次（使用默认逻辑）。

#### 2) `.specs/api-standard.md`

- 修正路由命名示例：
  - 旧示例使用 `/api/shift-codes`。
  - 新示例改为当前已实现的 `/api/role-groups`。
- 在 `GET /api/shifts` 参数说明中补充当前实现行为：
  - `teamId` 无映射时回退全量查询。
  - `timezone` 非法时返回 `500`。
- 修正 `GET /api/staff` 响应示例：
  - `roleGroups` 调整为 `null`（当前实现未在列表接口中填充）。
  - 补充说明：`GET /api/staff/{id}` 才会聚合并返回 `roleGroups`。
- 扩展状态码规范：
  - 补充 `400 Bad Request`（参数格式错误，如日期解析失败）。
- 在 OpenAPI 小节新增“文档漂移提醒”：
  - `openapi.yaml` 含 `/shift-codes`，但后端未实现该端点。
  - 明确后续需“实现端点”或“删除路径定义”二选一。

#### 3) `.specs/data-architecture.md`

- 在存储位置章节补充运行时说明：
  - 当前实现仅启动时加载 Excel，不支持运行时自动重载。
- 在“员工 ID 唯一性”章节补充数据质量边界：
  - 非数字 `staffId` 行会被静默跳过。
  - 重复 `staffId` 时 `staffMap` 采用首条记录。
- 新增“资源加载约束”章节：
  - 说明 `ClassPathResource(...).getFile()` 在某些打包形态下的潜在风险。

---

### 影响评估

- 文档行为定义与当前代码实现更加一致，减少误导性规范。
- 边界条件被显式记录，便于 QA 编写异常场景测试。
- OpenAPI 漂移问题被明确标注，便于后续契约治理。

---

### 后续建议

1. 对 `/api/shift-codes` 做契约收敛（实现或删除）。
2. 在后端增加 `timezone` 参数白名单校验并返回 `400`，避免把输入问题归类为 `500`。
3. 为 `teamId` 多 roleGroup 场景制定统一语义（首个匹配 vs 聚合）。
4. 为 Excel 解析过程增加告警日志与数据质量统计，避免静默跳过数据。
