# Docker Deployment Guide

This guide covers building and running the Distributed Secrets Vault using Docker.

## Prerequisites

- Docker and Docker Compose installed
- Java 25+ (for local builds)

## Quick Start with Docker Compose (Recommended)

The easiest way to run the application with Redis and PostgreSQL:

```bash
# 1. Setup environment (from project root; optional for quick start)
cp .env.example .env
# Edit .env: set REDIS_PASSWORD and POSTGRES_PASSWORD (dev compose defaults match .env.example if unset)

# 2. Build and start all services
./mvnw clean package
mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)
docker compose -f docker/dsv/docker-compose.dsv-redis-postgresql.yml up --build
```

The application will be available at:

- **API:** `http://localhost:8080`
- **Redis:** `localhost:6379`
- **PostgreSQL:** `localhost:5432`

### Compose file layout

Compose files live under `docker/`; there is no single `docker-compose.yml` at the root. Use `-f` to choose a file:

| File                                                  | Stack                                        |
| ----------------------------------------------------- | -------------------------------------------- |
| `dsv/docker-compose.dsv.yml`                          | App only                                     |
| `dsv/docker-compose.dsv-redis.yml`                    | App + Redis                                  |
| `dsv/docker-compose.dsv-postgresql.yml`               | App + PostgreSQL                             |
| `dsv/docker-compose.dsv-redis-postgresql.yml`         | App + Redis + PostgreSQL (full dev stack)    |
| `postgresql/docker-compose.postgresql.yml`            | PostgreSQL only (single node, dev)           |
| `postgresql/docker-compose.postgresql-production.yml` | PostgreSQL primary + 2 standbys (production) |
| `redis/docker-compose.redis.yml`                      | Redis only                                   |

### Docker Compose commands

All commands below assume you are in the **project root**. Use the same `-f` path for the stack you are running.

```bash
# Start full dev stack in foreground (see logs)
docker compose -f docker/dsv/docker-compose.dsv-redis-postgresql.yml up

# Start in background
docker compose -f docker/dsv/docker-compose.dsv-redis-postgresql.yml up -d

# View logs
docker compose -f docker/dsv/docker-compose.dsv-redis-postgresql.yml logs -f app
docker compose -f docker/dsv/docker-compose.dsv-redis-postgresql.yml logs -f redis
docker compose -f docker/dsv/docker-compose.dsv-redis-postgresql.yml logs -f postgres

# Stop services
docker compose -f docker/dsv/docker-compose.dsv-redis-postgresql.yml down

# Stop and remove volumes (clean slate)
docker compose -f docker/dsv/docker-compose.dsv-redis-postgresql.yml down -v

# Rebuild after code changes
./mvnw clean package && mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)
docker compose -f docker/dsv/docker-compose.dsv-redis-postgresql.yml up --build
```

## Configuration

### Environment variables

Configuration is managed through the `.env` file in the project root:

```env
# Redis
REDIS_PASSWORD=your-secure-password

# PostgreSQL (user accounts)
POSTGRES_USER=dsv
POSTGRES_PASSWORD=your-postgres-password
POSTGRES_DB=dsv

# Spring profile (dev, prod, test)
SPRING_PROFILES_ACTIVE=dev
```

For production PostgreSQL (primary + standbys), also set:

```env
POSTGRES_REPLICATION_USER=replicator
POSTGRES_REPLICATION_PASSWORD=your-replication-password
```

**Security note:** Never commit `.env` to git. Use `.env.example` as a template.

Docker Compose loads `.env` from the directory from which you run `docker compose`; when using the recommended commands (from project root), that is the project root.

### Redis configuration

Redis uses the stock `redis:8.6-alpine` image. Our `redis.conf` is mounted at `/usr/local/etc/redis/redis.conf` and passed explicitly (`redis-server /usr/local/etc/redis/redis.conf --requirepass ${REDIS_PASSWORD:-REDIS_PASSWORD}`) so Redis runs as a vanilla server with no bundled modules; the config file has no `include` and no `loadmodule`. Persistence and security are configured in `docker/redis/redis.conf`:

- AOF persistence with `everysec` fsync
- RDB snapshots at 15 min, 5 min, and 1 min intervals (when keys change)
- No eviction policy (suitable for secrets storage)
- Password auth required

### PostgreSQL configuration

- **Development:** Single node via `docker/postgresql/docker-compose.postgresql.yml` or as part of the full dev stack. Image: `postgres:18.2-alpine`. No custom config file (defaults only).
- **Production:** Multi-node (primary + 2 standbys) via `docker/postgresql/docker-compose.postgresql-production.yml`. Uses `docker/postgresql/postgresql.conf` for replication, WAL archiving, and logging. See **Production PostgreSQL** below.

## Production PostgreSQL (multi-node)

For redundancy, run one primary and two synchronous standbys:

```bash
# From project root. Ensure .env has POSTGRES_PASSWORD, POSTGRES_REPLICATION_PASSWORD (and optionally POSTGRES_REPLICATION_USER)
docker compose -f docker/postgresql/docker-compose.postgresql-production.yml up -d
```

- **postgres-primary:** Read-write; port 5432; uses `docker/postgresql/postgresql.conf`.
- **postgres-1, postgres-2:** Read-only standbys streaming from the primary. Application names match `synchronous_standby_names` in `postgresql.conf`.

Applications should connect to the primary (hostname `postgres-primary`) for read-write. See `docker/README.md` for script and network details.

## Standalone Docker (without Compose)

If you need to run the app container without Compose:

### Build the image

```bash
./mvnw clean package
mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)
docker build -t distributed-secrets-vault .
```

### Run the container

```bash
docker run -p 8080:8080 distributed-secrets-vault
```

### With environment variables

```bash
docker run -e "SPRING_PROFILES_ACTIVE=prod" \
  -e "SPRING_DATA_REDIS_HOST=redis.example.com" \
  -e "SPRING_DATA_REDIS_PASSWORD=yourpassword" \
  -e "SPRING_DATASOURCE_URL=jdbc:postgresql://postgres.example.com:5432/dsv" \
  -e "SPRING_DATASOURCE_USERNAME=dsv" \
  -e "SPRING_DATASOURCE_PASSWORD=yourpostgrespassword" \
  -p 8080:8080 distributed-secrets-vault
```

## Development

### Fast rebuild after code changes

The layered Dockerfile keeps dependency layers cached; only the app layer rebuilds when code changes:

```bash
./mvnw clean package
mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)
docker compose -f docker/dsv/docker-compose.dsv-redis-postgresql.yml up --build app
```

### Debugging

Enable remote debugging:

```bash
docker run -e "JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,address=5005,server=y,suspend=n" \
  -p 8080:8080 -p 5005:5005 distributed-secrets-vault
```

Connect your IDE debugger to `localhost:5005`.

## Alternative: Spring Boot Buildpack

Build without the project Dockerfile:

```bash
./mvnw spring-boot:build-image -Dspring-boot.build-image.imageName=distributed-secrets-vault
docker run -p 8080:8080 distributed-secrets-vault
```

## Container management

```bash
docker ps
docker stop <container-id>
docker rm <container-id>
docker rmi distributed-secrets-vault
```

For more detail (structure, services, production PostgreSQL scripts), see **docker/README.md**.
