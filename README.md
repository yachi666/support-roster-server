# Support Roster Server

[中文](./README.zh-CN.md) · [Parent workspace](https://github.com/yachi666/support-platform)

![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.3-6db33f?style=flat-square)
![Java](https://img.shields.io/badge/Java-25-e76f00?style=flat-square)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Primary_Storage-336791?style=flat-square)
![MyBatis Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.16-1f6feb?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)

> 🎯 A modern Spring Boot backend for on-call roster management, supporting public viewing, workspace administration, authentication, validation workflows, and Excel import/export.

**Support Roster Server** provides RESTful APIs for teams, shifts, staff, role groups, and roster management. It powers a public roster viewer for read-only access and a protected workspace for authenticated roster administration, validation, and data operations.

This repository runs as a Git submodule within [`support-platform`](https://github.com/yachi666/support-platform), where the frontend UI, automation tests, local development scripts, and documentation are coordinated.

## ✨ Highlights

- **🔐 JWT Authentication**: Sa-Token-based login with staff account activation and workspace access control
- **📊 PostgreSQL + Flyway**: Production-ready migrations and structured runtime data model
- **🗂️ MyBatis-Plus ORM**: Type-safe persistence with flexible querying for workspace resources
- **📝 OpenAPI-First Contracts**: Split API definitions organized under `api/` with clear viewer/workspace boundaries
- **✅ Validation Center**: Roster consistency checks, conflict detection, and automated cleanup flows
- **📥 Excel Import/Export**: Backward-compatible Excel parsing and batch roster operations
- **📚 Spec-Driven Maintenance**: Technical specifications maintained under `.specs/` for API, domain, database, and features

## 🎯 What It Provides

| Surface | Base Path | Purpose |
|---------|-----------|---------|
| 🌐 **Public Viewer APIs** | `/api/**` | Read-only roster and contact information for public pages |
| 🔧 **Workspace APIs** | `/api/workspace/**` | Authenticated roster administration, validation, imports, and protected tools |
| 🔑 **Authentication** | `/api/auth/**` | Staff ID login, account activation, JWT sessions, and access policy checks |
| 💚 **Health & Metrics** | `/actuator/health`, `/actuator/info` | Spring Boot Actuator endpoints for deployment monitoring |

## 🛠️ Tech Stack

| Category | Technology |
|----------|-----------|
| **Language** | Java 25 |
| **Framework** | Spring Boot 4.0.3 |
| **ORM** | MyBatis-Plus 3.5.16 |
| **Database** | PostgreSQL |
| **Migration** | Flyway |
| **Authentication** | Sa-Token 1.45.0 + JWT |
| **Connection Pool** | Druid 1.2.27 |
| **Excel Processing** | fesod-sheet 2.0.1-incubating |
| **Logging** | Log4j2 |
| **Build Tool** | Maven |

## 🚀 Quick Start

### Prerequisites

- **JDK 25** (required)
- **Maven** (build tool)
- **PostgreSQL** (database)

### 1️⃣ Configure

The application uses the `local` Spring profile by default. Configure via environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_URL` | PostgreSQL JDBC connection URL | `jdbc:postgresql://localhost:5432/support` |
| `DB_USERNAME` | Database username | `lzn` (override in dev scripts with `$USER`) |
| `DB_PASSWORD` | Database password | `123456` |
| `SA_TOKEN_JWT_SECRET_KEY` | JWT signing secret key | ⚠️ Required in non-local profiles |
| `SUPPORT_BOOTSTRAP_ADMIN_STAFF_ID` | Bootstrap admin staff ID (optional) | — |
| `SUPPORT_EMPLOYEE_BASE_URL` | External employee directory API | `https://api.heet.uk` |
| `LOG_PATH` | Log file output directory | `logs` |

> **💡 Tip**: For production deployments, always set a strong `SA_TOKEN_JWT_SECRET_KEY` and override database credentials.

### 2️⃣ Run

Start the server with Maven:

```bash
mvn spring-boot:run
```

The service will start on:

```
http://localhost:8080
```

### 3️⃣ Test

Run the test suite:

```bash
mvn test
```

## 🏗️ Architecture

The server follows a layered architecture with clear separation between public viewer APIs and authenticated workspace APIs:

```
┌─────────────────────────────────────────────┐
│             Client Layer                    │
│  • Public Viewer (read-only)               │
│  • Admin Workspace (authenticated)         │
│  • Automation Tests (Playwright)           │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│        Support Roster Server                │
│                                             │
│  controller/              REST endpoints    │
│  controller/workspace/    Admin APIs        │
│  service/                 Business logic    │
│  service/workspace/       Domain services   │
│  mapper/                  MyBatis-Plus DAL  │
│  dto/                     API contracts     │
│  entity/                  Domain models     │
│  repository/              Excel adapters    │
│  db/migration/            Flyway migrations │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│            PostgreSQL                       │
│  • Runtime data store                      │
│  • Flyway-managed schema                   │
└─────────────────────────────────────────────┘
```

## 📂 Project Structure

```
support-roster-server/
├── .specs/                          # 📚 Technical specifications (API, domain, database, features)
│   ├── _index.md                    # Main spec navigation
│   ├── api/                         # API contract specifications
│   ├── constraints/                 # System constraints and conventions
│   ├── data/                        # Data model and structure notes
│   ├── db/                          # Database schema and migration specs
│   ├── domain/                      # Business domain models and rules
│   └── features/                    # Feature-specific documentation
├── api/                             # 🔗 OpenAPI definitions
│   ├── openapi.yaml                 # Main OpenAPI entry point
│   ├── components/                  # Reusable schemas and parameters
│   └── paths/                       # API path definitions (viewer/ and workspace/)
├── spec/                            # 📝 Historical Excel and source data notes
├── src/main/java/                   # ☕ Application source code
│   └── com/support/server/
│       ├── controller/              # REST controllers
│       ├── service/                 # Business logic services
│       ├── mapper/                  # MyBatis-Plus mappers
│       ├── dto/                     # Data transfer objects
│       ├── entity/                  # Persistence entities and Excel models
│       └── repository/              # Excel readers and adapters
├── src/main/resources/
│   ├── application.yml              # Base Spring Boot configuration
│   ├── application-local.yml        # Local development profile
│   └── db/migration/                # 🗄️ Flyway migration scripts
├── src/test/                        # ✅ Test code
├── pom.xml                          # Maven project configuration
└── README.md                        # This file
```

> 📌 For the complete spec map, start with [`.specs/_index.md`](./.specs/_index.md).

## 🗄️ Database & Migrations

This project uses **PostgreSQL** as the primary runtime data store with **Flyway** for schema version control.

### Migration Strategy

- **Flyway scripts**: All schema changes are versioned in `src/main/resources/db/migration/`
- **Auto-init disabled**: `spring.sql.init.mode=never` ensures migrations are explicit and controlled
- **Legacy support**: `schema.sql` is kept for compatibility and local quick-start scenarios
- **Documentation**: Database schemas, constraints, and migration guidelines are documented in [`.specs/db/`](./.specs/db/)

### Key Configuration

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
  sql:
    init:
      mode: never  # Explicit migrations only
```

## 🔗 API Documentation

### OpenAPI Specifications

- **Main OpenAPI entry**: [`api/openapi.yaml`](./api/openapi.yaml) — Aggregated API contract with viewer and workspace paths
- **Component schemas**: `api/components/` — Reusable request/response schemas
- **Path definitions**: `api/paths/` — Organized by viewer and workspace domains

### Technical Specifications

Comprehensive technical documentation is maintained in [`.specs/`](./.specs/):

| Section | Path | Description |
|---------|------|-------------|
| 📖 **Main Index** | [`.specs/_index.md`](./.specs/_index.md) | Navigation hub for all specifications |
| 🔌 **API Specs** | [`.specs/api/`](./.specs/api/_index.md) | API contracts, routing, and standards |
| 🧩 **Domain Models** | [`.specs/domain/`](./.specs/domain/_index.md) | Business entities, rules, and workflows |
| 🗄️ **Database** | [`.specs/db/`](./.specs/db/_index.md) | Schema design, migrations, and constraints |
| 🎯 **Features** | [`.specs/features/`](./.specs/features/_index.md) | Feature-specific documentation (workspace, viewer, auth) |
| 📝 **Historical Notes** | [`spec/source_excel.md`](./spec/source_excel.md) | Legacy Excel format documentation |

## 🌐 Related Projects

This backend is part of a larger workspace ecosystem:

| Project | Role | Link |
|---------|------|------|
| **support-roster-ui** | Frontend UI (Vue 3) | `../support-roster-ui` |
| **automationtest** | E2E testing (Playwright) | `../automationtest` |
| **scripts/dev** | Local development tooling | `../scripts/dev` |
| **support-platform** | Parent workspace | [GitHub](https://github.com/yachi666/support-platform) |

## 🤝 Contributing

Contributions are welcome! To maintain code quality and consistency, please follow these guidelines:

### Before Making Changes

1. **Read the specs**: Review relevant documentation in [`.specs/`](./.specs/) and the OpenAPI contract in [`api/openapi.yaml`](./api/openapi.yaml)
2. **Understand boundaries**:
   - `/api/**` — Public viewer APIs (read-only)
   - `/api/workspace/**` — Authenticated workspace administration
   - Database changes require Flyway migrations

### Development Workflow

1. **Code + Spec together**: When changing APIs, domain logic, data models, or critical flows, update the corresponding `.specs/` documentation in the same commit
2. **API-first**: Update OpenAPI definitions before implementing new endpoints
3. **Migration-driven**: Database schema changes must be captured in Flyway migrations
4. **Test coverage**: Add or update tests for behavioral changes

### Spec Maintenance

- **Location**: All formal technical specs live in `.specs/` (not scattered across the codebase)
- **Navigation**: When adding new spec files, update the nearest `_index.md` navigation file
- **Sync requirement**: Spec updates are **mandatory** for any API, domain, database, or configuration changes

For detailed contribution guidelines, see [`AGENTS.md`](./AGENTS.md).

## 📄 License

This project is licensed under the [MIT License](./LICENSE).
