# Support Roster Server

![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.3-6db33f?style=flat-square)
![Java](https://img.shields.io/badge/Java-25-e76f00?style=flat-square)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Primary_Storage-336791?style=flat-square)
![MyBatis Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.16-1f6feb?style=flat-square)
![OpenAPI](https://img.shields.io/badge/OpenAPI-Split_Contracts-85ea2d?style=flat-square)

`support-roster-server` 是一个基于 `Spring Boot 4` 的排班管理后端服务，用于同时支撑公开查看页与后台管理工作台。

项目当前提供两条主要能力线：

- `Viewer APIs`：面向公开查看页的只读接口，统一挂载在 `/api/**`
- `Workspace APIs`：面向后台工作台的管理接口，统一挂载在 `/api/workspace/**`

## Features

- 团队、人员、班次、排班等只读查看接口
- 工作台后台能力：总览、人员目录、班次定义、团队管理、月排班、校验中心、导入导出
- PostgreSQL + MyBatis-Plus 持久化
- Excel 模板导入、预览批次、问题校验与 CSV 导出
- OpenAPI 契约拆分维护，便于接口评审与文档同步

## Tech Stack

| 类别 | 选型 |
|------|------|
| Language | `Java 25` |
| Framework | `Spring Boot 4.0.3` |
| Persistence | `MyBatis-Plus 3.5.16` |
| Database | `PostgreSQL` |
| Connection Pool | `Druid 1.2.27` |
| Excel Parsing | `fesod-sheet 2.0.1-incubating` |
| Logging | `Log4j2` |
| Build Tool | `Maven` |

## Quick Start

### Prerequisites

- JDK `25`
- Maven
- PostgreSQL

### Configure Database

服务默认从以下环境变量读取数据库配置：

| Variable | Purpose |
|----------|---------|
| `DB_URL` | PostgreSQL 连接地址 |
| `DB_USERNAME` | 数据库用户名 |
| `DB_PASSWORD` | 数据库密码 |
| `LOG_PATH` | 日志输出目录 |

默认端口：

```text
http://localhost:8080
```

### Run Application

```bash
mvn spring-boot:run
```

### Run Tests

```bash
mvn test
```

## API Surfaces

| Surface | Base Path | Description |
|---------|-----------|-------------|
| Viewer APIs | `/api/**` | 公开查看页只读接口 |
| Workspace APIs | `/api/workspace/**` | 后台管理与写入接口 |
| Actuator | `/actuator/health`、`/actuator/info` | 健康检查与基础信息 |

## Architecture Overview

```text
Clients
  ├── Public Viewer UI
  └── Admin Workspace UI
          ↓
support-roster-server
  ├── /api/**                -> viewer read-only controllers
  ├── /api/workspace/**      -> workspace controllers
  ├── service/               -> business orchestration
  ├── mapper/                -> MyBatis-Plus persistence
  ├── repository/            -> Excel parsing / compatibility
  └── PostgreSQL             -> primary runtime storage
```

- Viewer 接口保持只读边界
- Workspace 接口承载后台主数据维护、排班写入与导入导出
- Excel 相关链路作为兼容与导入能力保留，但运行时主存储已经转向 PostgreSQL

## Directory Structure

```text
support-roster-server/
├── .specs/                                  # 后端技术手册与专题规范
├── api/                                     # OpenAPI 契约
│   ├── components/                          # 公共 schema / 参数 / 响应
│   ├── paths/                               # 按 viewer / workspace 拆分的路径文件
│   └── openapi.yaml                         # OpenAPI 聚合入口
├── spec/                                    # 历史 Excel 数据结构说明
├── src/
│   ├── main/
│   │   ├── java/com/support/server/supportrosterserver/
│   │   │   ├── config/                      # 配置类
│   │   │   ├── controller/                  # REST 控制器
│   │   │   │   └── workspace/               # 工作台后台控制器
│   │   │   ├── dto/                         # DTO 定义
│   │   │   │   └── workspace/               # 工作台 DTO
│   │   │   ├── entity/                      # 实体与 Excel 行模型
│   │   │   │   └── workspace/               # 工作台实体
│   │   │   ├── exception/                   # 全局异常与业务异常
│   │   │   ├── mapper/                      # MyBatis-Plus Mapper
│   │   │   ├── repository/                  # Excel 解析与历史兼容读取
│   │   │   ├── service/                     # 业务服务
│   │   │   │   └── workspace/               # 工作台后台服务
│   │   │   ├── typehandler/                 # MyBatis 类型处理器
│   │   │   └── SupportRosterServerApplication.java
│   │   └── resources/
│   │       ├── application.yml              # 主配置
│   │       ├── application-local.yml        # 本地环境配置
│   │       ├── application-prod.yml         # 生产环境配置
│   │       ├── schema.sql                   # 数据库初始化兼容脚本
│   │       └── roster.xlsx                  # 导入模板文件
│   └── test/                                # 测试代码
├── generate_template.py                     # 模板生成辅助脚本
├── pom.xml                                  # Maven 配置
└── README.md                                # 项目说明
```

### Directory Notes

- `api/` 用于维护 OpenAPI 契约，便于代码与文档同步
- `src/main/java/.../controller/workspace/` 是后台管理接口入口
- `src/main/java/.../service/workspace/` 承载工作台核心业务逻辑
- `repository/` 仍保留 Excel 解析与兼容链路
- `.specs/` 记录接口、领域、数据库、约束与功能专题文档

## Data and Initialization

- 主存储为 PostgreSQL
- `spring.sql.init.mode=never`，实际联调通常需要手动执行迁移 SQL
- `schema.sql` 用于本地初始化兼容场景
- 详细建表与迁移约束见 `.specs/db/`

## Documentation

- 后端技术手册：[`./.specs/_index.md`](./.specs/_index.md)
- 接口规范目录：[`./.specs/api/_index.md`](./.specs/api/_index.md)
- 功能专题目录：[`./.specs/features/_index.md`](./.specs/features/_index.md)
- 数据库规范目录：[`./.specs/db/_index.md`](./.specs/db/_index.md)
- Excel 结构说明：[`./spec/source_excel.md`](./spec/source_excel.md)

## Related Project

前端项目位于同级目录：

- `../support-roster-ui`

本地联调时，通常先启动 PostgreSQL 与本服务，再启动前端 Vite 开发服务器。

## Roadmap

- 持续完善 workspace 资源的领域约束与接口一致性
- 继续收敛 viewer 只读适配层与后台主数据模型的边界
- 补充更稳定的测试、初始化与迁移说明
- 持续维护 OpenAPI 与 `.specs/`，保证契约、实现、文档同步

## Contributing

如果后续继续扩展本服务，建议遵循以下约定：

1. 先阅读 `.specs/` 与 `api/` 下的相关文档，再修改实现。
2. 新增接口时，同时更新 OpenAPI、资源级 spec 与 README 中的入口说明。
3. 保持 `/api/**` 与 `/api/workspace/**` 的语义边界清晰。
4. 涉及表结构变更时，同步更新 `.specs/db/` 与相关功能文档。

## License

当前仓库尚未补充正式 License 文件。

如果后续要按开源项目方式发布，建议在仓库根目录补充明确的许可证文件，并同步更新前后端 README。
