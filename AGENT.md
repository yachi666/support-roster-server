# Support Roster Server Agent Instructions

## Spec Maintenance Rules

- 每次完成代码更新后，必须同步检查并更新对应的 spec 文档，spec 更新是代码变更的一部分，不能遗漏。
- 所有正式技术规范统一维护在 `support-roster-server/.specs` 目录下，不要将新的规范文档分散到其他目录。
- 如果代码变更涉及接口、领域逻辑、数据结构、配置约束、异常处理、集成方式或关键流程，必须在同一次任务中把对应 spec 同步完成。
- 如果现有 spec 没有合适承载位置，应在 `.specs` 下新增对应文档或子目录，而不是把不同主题混写进单个文件。

## Spec Directory Conventions

- `.specs/_index.md` 是规范总入口，新增任何 spec 文件或子目录后，必须在对应 index 中补充导航。
- spec 目录结构要保持清晰，按主题拆分，例如：`api/`、`domain/`、`data/`、`constraints/`、`features/`。
- 当某一主题内容开始变复杂时，应拆为子目录，并在该目录下增加 `_index.md` 作为局部导航。
- 单个 spec 文件应尽量聚焦单一主题，避免把接口、数据结构、业务规则、实现约束混在同一文件中。
- 文件命名保持语义化和可读性，优先使用 kebab-case。

## Update Workflow

- 修改代码前，先判断本次变更会影响哪些 spec。
- 修改代码后，立即同步更新 `.specs` 中对应文档。
- 新增 spec 文件时，同时更新最近一级 `_index.md`；如果新增的是一级主题，也要更新根入口 `.specs/_index.md`。
- 提交结果前，确认代码实现、spec 内容、index 导航三者一致。

## Scope Notes

- `spec/` 目录下已有内容如果属于原始资料、来源说明或历史文档，可以保留；新增和持续维护的正式规范统一收敛到 `.specs/`。
- 若变更较小，也不能跳过 spec 同步；至少要确认并更新受影响的对应文档。
- 先和我对其需求再修改 有任何想法向我确认