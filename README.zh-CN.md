# Support Roster Server

[English](./README.md) · [父工作区](https://github.com/yachi666/support-platform)

![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.3-6db33f?style=flat-square)
![Java](https://img.shields.io/badge/Java-25-e76f00?style=flat-square)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Primary_Storage-336791?style=flat-square)
![MyBatis Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.16-1f6feb?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)

> 🎯 现代化 Spring Boot 值班排班管理后端，支持公开看板、工作台管理、认证、校验流程与 Excel 导入导出。

**Support Roster Server** 提供团队、班次、人员、角色组和排班管理的 RESTful API。为公开排班看板提供只读访问能力，为认证用户提供受保护的工作台进行排班管理、校验与数据操作。

本仓库作为 Git 子模块运行在 [`support-platform`](https://github.com/yachi666/support-platform) 中，前端界面、自动化测试、本地开发脚本和文档在总仓库中统一协调。

## ✨ 核心亮点

- **🔐 JWT 认证**：基于 Sa-Token 的登录系统，支持员工账号激活与工作台访问控制
- **📊 PostgreSQL + Flyway**：生产级数据库迁移与结构化运行时数据模型
- **🗂️ MyBatis-Plus ORM**：类型安全的持久化层，为工作台资源提供灵活查询能力
- **📝 OpenAPI 优先契约**：在 `api/` 下组织拆分的 API 定义，明确区分 viewer 与 workspace 边界
- **✅ 校验中心**：排班一致性检查、冲突检测与自动化清理流程
- **📥 Excel 导入导出**：向后兼容的 Excel 解析与批量排班操作
- **📚 规范驱动维护**：在 `.specs/` 下维护 API、领域、数据库与功能的技术规范

## 🎯 核心能力

| 接口面 | 基础路径 | 说明 |
|-------|---------|------|
| 🌐 **公开查看接口** | `/api/**` | 面向公开页面的只读排班与联系信息数据 |
| 🔧 **工作台接口** | `/api/workspace/**` | 认证后的排班管理、校验、导入与受保护工具 |
| 🔑 **认证接口** | `/api/auth/**` | 员工 ID 登录、账号激活、JWT 会话与访问策略检查 |
| 💚 **健康检查** | `/actuator/health`, `/actuator/info` | Spring Boot Actuator 端点，用于部署监控 |

## 🛠️ 技术栈

| 类别 | 技术选型 |
|-----|---------|
| **语言** | Java 25 |
| **框架** | Spring Boot 4.0.3 |
| **ORM** | MyBatis-Plus 3.5.16 |
| **数据库** | PostgreSQL |
| **迁移** | Flyway |
| **认证** | Sa-Token 1.45.0 + JWT |
| **连接池** | Druid 1.2.27 |
| **Excel 处理** | fesod-sheet 2.0.1-incubating |
| **日志** | Log4j2 |
| **构建工具** | Maven |

## 🚀 快速开始

### 前置条件

- **JDK 25**（必需）
- **Maven**（构建工具）
- **PostgreSQL**（数据库）

### 1️⃣ 配置

应用默认使用 `local` Spring profile。通过环境变量配置：

| 变量 | 说明 | 默认值 |
|-----|------|--------|
| `DB_URL` | PostgreSQL JDBC 连接地址 | `jdbc:postgresql://localhost:5432/support` |
| `DB_USERNAME` | 数据库用户名 | `lzn`（开发脚本会覆盖为 `$USER`） |
| `DB_PASSWORD` | 数据库密码 | `123456` |
| `SA_TOKEN_JWT_SECRET_KEY` | JWT 签名密钥 | ⚠️ 非 local profile 时必需 |
| `SUPPORT_BOOTSTRAP_ADMIN_STAFF_ID` | 初始管理员员工 ID（可选） | — |
| `SUPPORT_EMPLOYEE_BASE_URL` | 外部员工目录 API | `https://api.heet.uk` |
| `LOG_PATH` | 日志文件输出目录 | `logs` |

> **💡 提示**：生产环境部署时，务必设置强 `SA_TOKEN_JWT_SECRET_KEY` 并覆盖数据库凭证。

### 2️⃣ 启动

使用 Maven 启动服务：

```bash
mvn spring-boot:run
```

服务将启动在：

```
http://localhost:8080
```

### 3️⃣ 测试

运行测试套件：

```bash
mvn test
```

## 🏗️ 架构概览

服务采用分层架构，明确区分公开 viewer API 与认证 workspace API：

```
┌─────────────────────────────────────────────┐
│             客户端层                        │
│  • 公开看板（只读）                        │
│  • 管理工作台（认证）                      │
│  • 自动化测试（Playwright）                │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│        Support Roster Server                │
│                                             │
│  controller/              REST 端点         │
│  controller/workspace/    管理接口         │
│  service/                 业务逻辑         │
│  service/workspace/       领域服务         │
│  mapper/                  MyBatis-Plus DAL │
│  dto/                     API 契约         │
│  entity/                  领域模型         │
│  repository/              Excel 适配器     │
│  db/migration/            Flyway 迁移      │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│            PostgreSQL                       │
│  • 运行时数据存储                          │
│  • Flyway 管理的 schema                   │
└─────────────────────────────────────────────┘
```

## 📂 项目结构

```
support-roster-server/
├── .specs/                          # 📚 技术规范（API、领域、数据库、功能）
│   ├── _index.md                    # 规范导航入口
│   ├── api/                         # API 契约规范
│   ├── constraints/                 # 系统约束与约定
│   ├── data/                        # 数据模型与结构说明
│   ├── db/                          # 数据库 schema 与迁移规范
│   ├── domain/                      # 业务领域模型与规则
│   └── features/                    # 功能模块文档
├── api/                             # 🔗 OpenAPI 定义
│   ├── openapi.yaml                 # OpenAPI 主入口
│   ├── components/                  # 可复用的 schema 和参数
│   └── paths/                       # API 路径定义（viewer/ 和 workspace/）
├── spec/                            # 📝 历史 Excel 与源数据说明
├── src/main/java/                   # ☕ 应用源代码
│   └── com/support/server/
│       ├── controller/              # REST 控制器
│       ├── service/                 # 业务逻辑服务
│       ├── mapper/                  # MyBatis-Plus mapper
│       ├── dto/                     # 数据传输对象
│       ├── entity/                  # 持久化实体与 Excel 模型
│       └── repository/              # Excel 读取器与适配器
├── src/main/resources/
│   ├── application.yml              # Spring Boot 基础配置
│   ├── application-local.yml        # 本地开发 profile
│   └── db/migration/                # 🗄️ Flyway 迁移脚本
├── src/test/                        # ✅ 测试代码
├── pom.xml                          # Maven 项目配置
└── README.md                        # 本文件
```

> 📌 如需查看完整 spec 导航，请从 [`.specs/_index.md`](./.specs/_index.md) 开始。

## 🗄️ 数据库与迁移

本项目使用 **PostgreSQL** 作为主运行时数据存储，通过 **Flyway** 进行 schema 版本控制。

### 迁移策略

- **Flyway 脚本**：所有 schema 变更在 `src/main/resources/db/migration/` 中版本化管理
- **禁用自动初始化**：`spring.sql.init.mode=never` 确保迁移是显式和可控的
- **兼容支持**：保留 `schema.sql` 用于兼容性和本地快速启动场景
- **文档化**：数据库 schema、约束与迁移指南记录在 [`.specs/db/`](./.specs/db/) 中

### 关键配置

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
  sql:
    init:
      mode: never  # 仅允许显式迁移
```

## 🔗 API 文档

### OpenAPI 规范

- **OpenAPI 主入口**：[`api/openapi.yaml`](./api/openapi.yaml) — 聚合的 API 契约，包含 viewer 和 workspace 路径
- **组件 schema**：`api/components/` — 可复用的请求/响应 schema
- **路径定义**：`api/paths/` — 按 viewer 和 workspace 领域组织

### 技术规范

完整的技术文档维护在 [`.specs/`](./.specs/) 中：

| 章节 | 路径 | 说明 |
|-----|------|------|
| 📖 **总入口** | [`.specs/_index.md`](./.specs/_index.md) | 所有规范的导航中心 |
| 🔌 **API 规范** | [`.specs/api/`](./.specs/api/_index.md) | API 契约、路由与标准 |
| 🧩 **领域模型** | [`.specs/domain/`](./.specs/domain/_index.md) | 业务实体、规则与工作流 |
| 🗄️ **数据库** | [`.specs/db/`](./.specs/db/_index.md) | Schema 设计、迁移与约束 |
| 🎯 **功能模块** | [`.specs/features/`](./.specs/features/_index.md) | 功能专项文档（workspace、viewer、auth） |
| 📝 **历史说明** | [`spec/source_excel.md`](./spec/source_excel.md) | 历史 Excel 格式文档 |

## 🌐 相关项目

本后端是更大工作区生态的一部分：

| 项目 | 角色 | 链接 |
|-----|------|------|
| **support-roster-ui** | 前端界面（Vue 3） | `../support-roster-ui` |
| **automationtest** | 端到端测试（Playwright） | `../automationtest` |
| **scripts/dev** | 本地开发工具 | `../scripts/dev` |
| **support-platform** | 总仓库 | [GitHub](https://github.com/yachi666/support-platform) |

## 🤝 贡献指南

欢迎贡献！为保持代码质量与一致性，请遵循以下准则：

### 修改前

1. **阅读规范**：查看 [`.specs/`](./.specs/) 中的相关文档和 [`api/openapi.yaml`](./api/openapi.yaml) 中的 OpenAPI 契约
2. **理解边界**：
   - `/api/**` — 公开 viewer API（只读）
   - `/api/workspace/**` — 认证后的工作台管理
   - 数据库变更需要 Flyway 迁移

### 开发流程

1. **代码 + 规范同步**：修改 API、领域逻辑、数据模型或关键流程时，必须在同一提交中更新对应的 `.specs/` 文档
2. **API 优先**：实现新端点前先更新 OpenAPI 定义
3. **迁移驱动**：数据库 schema 变更必须通过 Flyway 迁移体现
4. **测试覆盖**：为行为变更添加或更新测试

### 规范维护

- **位置**：所有正式技术规范位于 `.specs/`（不要散落在代码库中）
- **导航**：新增 spec 文件时，更新最近的 `_index.md` 导航文件
- **同步要求**：任何 API、领域、数据库或配置变更，spec 更新都是**强制的**

详细贡献指南请参阅 [`AGENTS.md`](./AGENTS.md)。

## 📄 许可证

本项目采用 [MIT License](./LICENSE) 许可。
