# 第 2 章 领域模型目录

## 文档定位

本章聚焦业务实体、排班语义、团队与角色组关系，以及影响 viewer / workspace 两侧行为的一致性规则。

## 阅读建议

| 阅读顺序 | 适用场景 |
|---|---|
| [../domain-logic.md](../domain-logic.md) | 需要理解实体关系、班次判定、时区转换与头像生成规则 |
| [../domain-logic.md](../domain-logic.md) → [../features/workspace-admin/roster.md](../features/workspace-admin/roster.md) | 需要把领域规则映射到月度排班保存行为 |
| [../domain-logic.md](../domain-logic.md) → [../features/viewer/shifts.md](../features/viewer/shifts.md) | 需要把领域规则映射到只读排班输出 |

## 本章内容

| 文档 | 角色 | 重点内容 |
|---|---|---|
| [../domain-logic.md](../domain-logic.md) | 领域主文档 | 核心实体、业务流程、班次规则、团队映射与边界条件 |

## 维护提示

- 若新增团队分组规则、主要班次判定或时区映射，应先更新本章，再同步接口与数据文档。
- 领域层的历史兼容说明应保留，例如 `roleGroup -> team` 的迁移事实，不宜在重构时被抹平。
