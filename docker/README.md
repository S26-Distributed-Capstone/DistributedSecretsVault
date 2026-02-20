# Docker Configuration

This directory contains all Docker-related configuration for the Distributed Secrets Vault.

## Structure

```
docker/
├── docker-compose.yml    # Orchestrates app + Redis
├── redis/
│   └── redis.conf        # Redis persistence and security config
└── README.md             # This file

Project root:
├── .env.example          # Environment variable template
└── .env                  # Your local config (gitignored)
```

## Setup

1. **Create environment file in project root:**

   ```bash
   cp .env.example .env
   ```

2. **Set Redis password in `.env`:**

   ```env
   REDIS_PASSWORD=your-secure-password-here
   ```

3. **Build and start from project root:**

   ```bash
   ./mvnw clean package
   mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)
   cd docker && docker compose up --build
   ```

   Note: Docker Compose automatically loads `.env` from the project root.

## Environment Variables

| Variable                 | Description                   | Default         |
| ------------------------ | ----------------------------- | --------------- |
| `REDIS_PASSWORD`         | Redis authentication password | `REDISPASSWORD` |
| `SPRING_PROFILES_ACTIVE` | Spring Boot profile           | `dev`           |

## Redis Configuration

Redis is configured for durable secret storage with:

- **AOF persistence**: `appendfsync everysec` (max 1 second data loss)
- **RDB snapshots**: Every 15 minutes if keys changed
- **No eviction**: Secrets are never auto-deleted
- **Password auth**: Required for all connections

See `redis/redis.conf` for full configuration.

## Services

### `redis`

- **Image**: redis:8.4
- **Ports**: 6379
- **Volumes**: Persistent data in `redis-data` volume

### `app`

- **Build**: From parent directory Dockerfile
- **Ports**: 8080
- **Depends on**: Redis (waits for health check)

## Commands

```bash
# Start services
docker compose up

# Start in background
docker compose up -d

# View logs
docker compose logs -f app
docker compose logs -f redis

# Stop services
docker compose down

# Clean slate (removes volumes)
docker compose down -v

# Rebuild after code changes
cd .. && ./mvnw clean package && mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)
cd docker && docker compose up --build
```

## Network

All services communicate on the `dsv-network` bridge network. The app connects to Redis using the hostname `redis`.
