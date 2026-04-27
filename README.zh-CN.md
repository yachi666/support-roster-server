# Support Roster Server

[English](./README.md) · [总仓库](https://github.com/yachi666/support-platform)

![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.3-6db33f?style=flat-square)
![Java](https://img.shields.io/badge/Java-25-e76f00?style=flat-square)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Primary_Storage-336791?style=flat-square)
![MyBatis Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.16-1f6feb?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)

`support-roster-server` 是支持排班平台的 Spring Boot 后端服务，支撑公开排班看板、认证后的管理工作台、联系信息、Linux 密码库元数据、认证、校验以及导入导出流程。

本仓库作为 [`support-platform`](https://github.com/yachi666/support-platform) 的子模块运行，前端、自动化测试、本地脚本和截图资源都在总仓库中统一协调。

## 核心能力

| 接口面 | 基础路径 | 说明 |
|--------|----------|------|
| 公开查看接口 | `/api/**` | 面向公开页面的只读排班与联系信息数据。 |
| 工作台接口 | `/api/workspace/**` | 面向团队、人员、班次、排班、校验、账号、导入和受保护工具的后台管理接口。 |
| 认证接口 | `/api/auth/**` | 员工 ID 登录、账号激活、JWT 会话和工作台访问策略检查。 |
| 健康检查 | `/actuator/health`, `/actuator/info` | 本地与部署环境健康检查。 |

## 功能亮点

- 以 PostgreSQL 为主运行时数据模型，并使用 Flyway 维护迁移。
- 使用 MyBatis-Plus 管理工作台资源持久化。
- `api/` 下维护拆分后的 OpenAPI 契约。
- 支持排班一致性校验和清理回归流程。
- 保留 Excel 导入导出兼容能力。
- 基于 Sa-Token JWT 的员工账号认证和激活流程。
- 通过 `.specs/` 维护正式后端规范。

## 技术栈

| 类别 | 选型 |
|------|------|
| 语言 | `Java 25` |
| 框架 | `Spring Boot 4.0.3` |
| 持久化 | `MyBatis-Plus 3.5.16` |
| 数据库 | `PostgreSQL` |
| 迁移 | `Flyway` |
| 认证 | `Sa-Token 1.45.0` + JWT |
| 连接池 | `Druid 1.2.27` |
| Excel 解析 | `fesod-sheet 2.0.1-incubating` |
| 日志 | `Log4j2` |
| 构建工具 | `Maven` |

## 快速开始

### 前置条件

- JDK `25`
- Maven
- PostgreSQL

### 配置

应用默认使用 `local` Spring profile。常用环境变量：

| 变量 | 用途 | 默认值 |
|------|------|--------|
| `DB_URL` | PostgreSQL JDBC 地址 | `jdbc:postgresql://localhost:5432/support` |
| `DB_USERNAME` | 数据库用户名 | 应用配置为 `lzn`，开发脚本使用当前系统用户 |
| `DB_PASSWORD` | 数据库密码 | `123456` |
| `SA_TOKEN_JWT_SECRET_KEY` | JWT 签名密钥 | 非 local profile 需显式提供 |
| `SUPPORT_BOOTSTRAP_ADMIN_STAFF_CODE` | 可选的初始管理员员工号 | 空 |
| `SUPPORT_EMPLOYEE_BASE_URL` | 员工目录服务地址 | `https://api.heet.uk` |
| `LOG_PATH` | 日志输出目录 | `logs` |

### 启动

```bash
mvn spring-boot:run
```

默认监听地址：

```text
http://localhost:8080
```

### 测试

```bash
mvn test
```

## 架构概览

```text
Clients
  ├── Public Viewer
  ├── Admin Workspace
  └── Automation Tests
        ↓
support-roster-server
  ├── controller/             # REST 控制器
  ├── controller/workspace/   # 工作台后台接口
  ├── service/                # 业务编排
  ├── service/workspace/      # 工作台领域服务
  ├── mapper/                 # MyBatis-Plus 持久化
  ├── dto/                    # API 契约对象
  ├── entity/                 # 数据库实体与 Excel 行模型
  ├── repository/             # Excel 解析和兼容读取
  └── db/migration/           # Flyway 迁移
        ↓
     PostgreSQL
```

## 目录说明

```text
support-roster-server/
├── .specs/                    # 持续维护的后端规范
├── api/                       # 拆分维护的 OpenAPI 契约
├── spec/                      # 历史 Excel/来源数据说明
├── src/main/java/...          # 应用代码
├── src/main/resources/
│   ├── application.yml        # 基础配置
│   ├── application-local.yml  # 本地 profile 配置
│   ├── db/migration/          # Flyway 迁移
│   └── schema.sql             # 兼容初始化脚本
├── src/test/                  # 测试代码
├── pom.xml
└── README.md
```

## 数据与迁移说明

- PostgreSQL 是主运行时存储。
- Flyway 迁移位于 `src/main/resources/db/migration`。
- `spring.sql.init.mode=never`，迁移、种子数据或手动初始化应显式执行。
- `schema.sql` 保留用于兼容和本地初始化场景。
- 数据库行为和迁移约束见 `.specs/db/`。

## 相关项目

| 项目 | 关系 |
|------|------|
| `../support-roster-ui` | 消费 viewer、workspace、auth、联系信息和受保护工具接口。 |
| `../automationtest` | 针对此服务和前端运行 Playwright 冒烟与回归测试。 |
| `../scripts/dev` | 提供本地启动/重启脚本，并检查服务健康状态。 |

## 文档入口

- 后端规范总入口：[`./.specs/_index.md`](./.specs/_index.md)
- API 规范：[`./.specs/api/_index.md`](./.specs/api/_index.md)
- 数据库规范：[`./.specs/db/_index.md`](./.specs/db/_index.md)
- 功能规范：[`./.specs/features/_index.md`](./.specs/features/_index.md)
- OpenAPI 入口：[`./api/openapi.yaml`](./api/openapi.yaml)
- 历史 Excel 说明：[`./spec/source_excel.md`](./spec/source_excel.md)

## 贡献约定

修改行为前，请先阅读相关 `.specs/` 文档和 OpenAPI 契约。凡涉及接口、领域规则、数据模型、配置约束、集成方式或关键流程变更，都需要在同一次变更中同步更新对应 spec。

请保持以下边界清晰：

- `/api/**` 面向公开或 viewer 只读流程。
- `/api/workspace/**` 面向认证后的工作台管理。
- 数据库变更应通过 Flyway 迁移体现，并同步维护对应规范。

## 许可证

本项目采用 [MIT License](./LICENSE)。
