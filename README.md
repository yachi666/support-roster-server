# Support Roster Server

[中文](./README.zh-CN.md) · [Parent workspace](https://github.com/yachi666/support-platform)

![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.3-6db33f?style=flat-square)
![Java](https://img.shields.io/badge/Java-25-e76f00?style=flat-square)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Primary_Storage-336791?style=flat-square)
![MyBatis Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.16-1f6feb?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)

`support-roster-server` is the Spring Boot backend for the support roster platform. It serves the public roster viewer, the authenticated admin workspace, contact information pages, Linux password vault metadata, authentication, validation, and import/export workflows.

This repository is designed to run as a submodule of [`support-platform`](https://github.com/yachi666/support-platform), where the frontend, automation tests, local scripts, and screenshots are coordinated.

## What It Provides

| Surface | Base Path | Purpose |
|---------|-----------|---------|
| Public viewer APIs | `/api/**` | Read-only roster and contact information data for public-facing pages. |
| Workspace APIs | `/api/workspace/**` | Authenticated administration for teams, staff, shifts, rosters, validation, accounts, imports, and protected tools. |
| Authentication | `/api/auth/**` | Staff ID login, account activation, JWT-backed sessions, and workspace access policy checks. |
| Actuator | `/actuator/health`, `/actuator/info` | Local and deployment health checks. |

## Highlights

- PostgreSQL-first runtime data model with Flyway migrations.
- MyBatis-Plus persistence for workspace resources.
- Split OpenAPI contracts under `api/`.
- Validation center support for roster consistency issues and cleanup flows.
- Excel import/export compatibility for roster workflows.
- Sa-Token JWT authentication with staff account activation.
- Specification-driven maintenance under `.specs/`.

## Tech Stack

| Category | Choice |
|----------|--------|
| Language | `Java 25` |
| Framework | `Spring Boot 4.0.3` |
| Persistence | `MyBatis-Plus 3.5.16` |
| Database | `PostgreSQL` |
| Migration | `Flyway` |
| Authentication | `Sa-Token 1.45.0` with JWT |
| Connection Pool | `Druid 1.2.27` |
| Excel Parsing | `fesod-sheet 2.0.1-incubating` |
| Logging | `Log4j2` |
| Build Tool | `Maven` |

## Quick Start

### Prerequisites

- JDK `25`
- Maven
- PostgreSQL

### Configure

The application defaults to the `local` Spring profile. Common environment variables:

| Variable | Purpose | Default |
|----------|---------|---------|
| `DB_URL` | JDBC PostgreSQL URL | `jdbc:postgresql://localhost:5432/support` |
| `DB_USERNAME` | Database username | `lzn` in app config, current user in dev scripts |
| `DB_PASSWORD` | Database password | `123456` |
| `SA_TOKEN_JWT_SECRET_KEY` | JWT signing secret | Required outside local profile |
| `SUPPORT_BOOTSTRAP_ADMIN_STAFF_CODE` | Optional bootstrap admin staff code | empty |
| `SUPPORT_EMPLOYEE_BASE_URL` | Employee directory service base URL | `https://api.heet.uk` |
| `LOG_PATH` | Log output directory | `logs` |

### Run

```bash
mvn spring-boot:run
```

The service listens on:

```text
http://localhost:8080
```

### Test

```bash
mvn test
```

## Architecture

```text
Clients
  ├── Public Viewer
  ├── Admin Workspace
  └── Automation Tests
        ↓
support-roster-server
  ├── controller/             # REST controllers
  ├── controller/workspace/   # Workspace administration APIs
  ├── service/                # Business orchestration
  ├── service/workspace/      # Workspace domain services
  ├── mapper/                 # MyBatis-Plus persistence
  ├── dto/                    # API contracts
  ├── entity/                 # Database and Excel row models
  ├── repository/             # Excel parsing and compatibility readers
  └── db/migration/           # Flyway migrations
        ↓
     PostgreSQL
```

## Directory Guide

```text
support-roster-server/
├── .specs/                    # Maintained backend specifications
├── api/                       # Split OpenAPI contract files
├── spec/                      # Historical Excel/source-data notes
├── src/main/java/...          # Application code
├── src/main/resources/
│   ├── application.yml        # Base configuration
│   ├── application-local.yml  # Local profile overrides
│   ├── db/migration/          # Flyway migrations
│   └── schema.sql             # Compatibility initialization script
├── src/test/                  # Tests
├── pom.xml
└── README.md
```

## Data and Migration Notes

- PostgreSQL is the primary runtime store.
- Flyway migrations live in `src/main/resources/db/migration`.
- `spring.sql.init.mode=never`; migrations, seed SQL, or manual setup should be explicit.
- `schema.sql` remains for compatibility and local initialization scenarios.
- Database behavior and migration constraints are documented under `.specs/db/`.

## Related Projects

| Project | Relationship |
|---------|--------------|
| `../support-roster-ui` | Consumes viewer, workspace, auth, contact information, and protected-tool APIs. |
| `../automationtest` | Runs Playwright smoke and regression coverage against this service and the UI. |
| `../scripts/dev` | Provides local start/restart scripts that health-check this service. |

## Documentation

- Backend specs: [`./.specs/_index.md`](./.specs/_index.md)
- API specs: [`./.specs/api/_index.md`](./.specs/api/_index.md)
- Database specs: [`./.specs/db/_index.md`](./.specs/db/_index.md)
- Feature specs: [`./.specs/features/_index.md`](./.specs/features/_index.md)
- OpenAPI entry: [`./api/openapi.yaml`](./api/openapi.yaml)
- Historical Excel notes: [`./spec/source_excel.md`](./spec/source_excel.md)

## Contributing

Before changing behavior, read the relevant `.specs/` document and OpenAPI contract. When an API, domain rule, data model, configuration constraint, integration, or critical flow changes, update the matching spec in the same change.

Keep these boundaries clear:

- `/api/**` is for public or viewer-oriented read flows.
- `/api/workspace/**` is for authenticated workspace administration.
- Database changes should be represented by Flyway migrations and matching specs.

## License

This project is released under the [MIT License](./LICENSE).
