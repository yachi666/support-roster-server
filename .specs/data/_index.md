# Data Spec Index

## 范围

- 本目录承载 Excel 数据源结构、加载机制、内存索引、数据质量约束与资源加载限制。
- 任何涉及数据来源、字段结构、解析逻辑或索引规则的变化，都应同步更新此处。

## 当前文档

| 文档 | 说明 |
|------|------|
| `../data-architecture.md` | Excel 存储结构、数据监听器、内存数据结构、索引逻辑与运行时约束 |

## 后续拆分建议

- 当数据规则增多后，可拆分为 `excel-schema.md`、`loading-pipeline.md`、`in-memory-indexes.md`、`data-quality.md`。