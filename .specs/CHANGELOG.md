# Spec Change Log

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
