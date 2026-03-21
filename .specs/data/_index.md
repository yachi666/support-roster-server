# 第 3 章 数据架构目录

## 文档定位

本章描述 Excel 数据来源、加载链路、运行时内存索引，以及当前仓库中仍需保留的历史兼容约束。

## 阅读建议

| 阅读顺序 | 适用场景 |
|---|---|
| [../data-architecture.md](../data-architecture.md) | 查看 Excel sheet 结构、监听器、索引键与数据质量约束 |
| [../data-architecture.md](../data-architecture.md) → [../features/workspace-admin/import-export.md](../features/workspace-admin/import-export.md) | 理解导入模板与预览/应用链路 |

## 本章内容

| 文档 | 角色 | 重点内容 |
|---|---|---|
| [../data-architecture.md](../data-architecture.md) | 数据主文档 | Excel 结构、加载流程、内存结构、约束与迁移备注 |

## 维护提示

- 本章主要记录**数据来源与兼容事实**，不是数据库建模规则；数据库规则请转到 [../db/_index.md](../db/_index.md)。
- 如果某个 Excel 字段不再参与运行时链路，也应保留清晰的“未使用 / 仅兼容”说明。
