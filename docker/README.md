# Docker Configuration

This directory contains all Docker-related configuration for the Distributed Secrets Vault.

## Structure

```
docker/
├── dsv/
│   ├── docker-compose.dsv.yml                    # App only
│   ├── docker-compose.dsv-redis.yml              # App + Redis
│   ├── docker-compose.dsv-postgresql.yml         # App + PostgreSQL
│   └── docker-compose.dsv-redis-postgresql.yml   # App + Redis + PostgreSQL (full dev stack)
├── redis/
│   ├── docker-compose.redis.yml                  # Redis only
│   └── redis.conf                                # Redis persistence and security config
├── kafka/
│   └── docker-compose.kafka.yml                  # Kafka only (KRaft mode)
├── postgresql/
│   ├── docker-compose.postgresql.yml             # PostgreSQL only (development, single node)
│   ├── docker-compose.postgresql-production.yml  # Production: primary + 2 standbys
│   ├── postgresql.conf                           # Production replication config (used by production compose)
│   └── scripts/
│       ├── init-primary.sh                       # Creates replication user (production primary)
│       └── replica-entrypoint.sh                 # Bootstrap standbys from primary (production)
└── README.md                                     # This file

Project root:
├── .env.example                                  # Environment variable template
└── .env                                          # Your local config (gitignored)
```

## Setup

1. **Create environment file in project root:**

   ```bash
   cp .env.example .env
   ```

2. **Set required values in `.env`** (recommended; otherwise dev compose uses defaults):

   ```env
   REDIS_PASSWORD=your-secure-password-here
   POSTGRES_PASSWORD=your-postgres-password-here
   ```

   If you skip `.env`, the **development** compose files use the same defaults as `.env.example` (`REDIS_PASSWORD`, `POSTGRES_PASSWORD`). Set stronger values in `.env` for any real use. Optionally set `POSTGRES_USER` and `POSTGRES_DB` (defaults: `dsv`).

3. **Build and start from project root:**

   ```bash
   ./mvnw clean package
   mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)
   docker compose -f docker/dsv/docker-compose.dsv-redis-postgresql.yml up --build
   ```

   When you run `docker compose` from the project root (as in the commands below), Compose loads `.env` from the project root.

   **Other compose files:**
   - App only: `docker compose -f docker/dsv/docker-compose.dsv.yml up --build`
   - App + Redis: `docker compose -f docker/dsv/docker-compose.dsv-redis.yml up --build`
   - App + PostgreSQL: `docker compose -f docker/dsv/docker-compose.dsv-postgresql.yml up --build`

## Environment Variables

| Variable                        | Description                       | Default                                                   |
| ------------------------------- | --------------------------------- | --------------------------------------------------------- |
| `REDIS_PASSWORD`                | Redis authentication password     | Placeholder in `.env.example`; set in `.env` for real use |
| `POSTGRES_USER`                 | PostgreSQL user                   | `dsv`                                                     |
| `POSTGRES_PASSWORD`             | PostgreSQL password (required)    | Placeholder in `.env.example`; set in `.env` for real use |
| `POSTGRES_DB`                   | PostgreSQL database name          | `dsv`                                                     |
| `POSTGRES_REPLICATION_USER`     | Replication user (production)     | `replicator`                                              |
| `POSTGRES_REPLICATION_PASSWORD` | Replication password (production) | —                                                         |
| `SPRING_PROFILES_ACTIVE`        | Spring Boot profile               | `dev`                                                     |

## Redis Configuration

Redis is configured for durable secret storage with:

- **AOF persistence**: `appendfsync everysec` (max 1 second data loss)
- **RDB snapshots**: Every 15 minutes if keys changed
- **No eviction**: Secrets are never auto-deleted
- **Password auth**: Required for all connections

See `redis/redis.conf` for full configuration.

## Services

### `redis`

- **Image**: redis:8.6-alpine
- **Ports**: 6379
- **Volumes**: Persistent data in `redis-data` volume

### `postgres`

- **Image**: postgres:18.2-alpine
- **Ports**: 5432
- **Volumes**: Persistent data in `postgres-data` volume
- **Healthcheck**: `pg_isready` before app starts
- **Purpose**: User accounts; development uses a single node. For production redundancy, use the production compose (see below).

### `kafka`

- **Image**: apache/kafka:3.7.0
- **Ports**: 9092
- **Volumes**: Persistent data in `kafka-data` volume
- **Purpose**: Message broker for request sequencing. Uses KRaft (ZooKeeper-less) mode.

### `app`

- **Build**: From project root (build context `../..`); uses the Dockerfile in the project root.
- **Ports**: 8080
- **Depends on**: Redis and/or PostgreSQL (waits for health checks when present)

## Commands

All commands assume you are in the **project root**.

```bash
# Full dev stack (app + Redis + PostgreSQL)
./mvnw clean package
mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)
docker compose -f docker/dsv/docker-compose.dsv-redis-postgresql.yml up --build

# Start in background
docker compose -f docker/dsv/docker-compose.dsv-redis-postgresql.yml up -d

# View logs
docker compose -f docker/dsv/docker-compose.dsv-redis-postgresql.yml logs -f app
docker compose -f docker/dsv/docker-compose.dsv-redis-postgresql.yml logs -f redis
docker compose -f docker/dsv/docker-compose.dsv-redis-postgresql.yml logs -f postgres

# Stop services
docker compose -f docker/dsv/docker-compose.dsv-redis-postgresql.yml down

# Clean slate (removes volumes)
docker compose -f docker/dsv/docker-compose.dsv-redis-postgresql.yml down -v

# Rebuild after code changes
./mvnw clean package && mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)
docker compose -f docker/dsv/docker-compose.dsv-redis-postgresql.yml up --build
```

## Production PostgreSQL (multi-node)

For production, run one primary and two synchronous standbys for redundancy:

```bash
# From project root. Set in .env: POSTGRES_PASSWORD, POSTGRES_REPLICATION_USER (default: replicator), POSTGRES_REPLICATION_PASSWORD
docker compose -f docker/postgresql/docker-compose.postgresql-production.yml up -d
```

- **postgres-primary**: Read-write; uses `postgresql/postgresql.conf` (WAL archiving, synchronous replication). Port 5432.
- **postgres-1**, **postgres-2**: Read-only standbys; stream from primary. Application names match `synchronous_standby_names` in `postgresql.conf` so commits wait for at least one standby.
- Scripts: `scripts/init-primary.sh` creates the replication user on first start; `scripts/replica-entrypoint.sh` bootstraps each standby with `pg_basebackup` then starts streaming.

Applications should connect to the primary (hostname `postgres-primary`) for read-write; standbys can be used for read scaling if desired.

## Network

All services communicate on the `dsv-network` bridge network. The app connects to Redis using the hostname `redis` and to PostgreSQL using the hostname `postgres` (dev) or `postgres-primary` (production).
