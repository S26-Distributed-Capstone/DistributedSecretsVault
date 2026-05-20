# Docker Deployment Guide

This guide covers building and running Distributed Secrets Vault with Docker.

## Quick Start

```bash
cp .env.example .env
./mvnw clean package -DskipTests
mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)
docker compose -f docker/dsv/docker-compose.dsv-redis-kafka.yml up --build
```

The application will be available at:

- API: `http://localhost:8080`
- Redis: `localhost:6379`
- Kafka: `localhost:9092`

## Compose Files

| File | Stack |
| --- | --- |
| `docker/dsv/docker-compose.dsv.yml` | App only |
| `docker/dsv/docker-compose.dsv-redis.yml` | App + Redis |
| `docker/dsv/docker-compose.dsv-redis-kafka.yml` | App + Redis + Kafka |
| `docker/dsv/docker-compose.dsv-redis-kafka-3nodes.yml` | Three app nodes + per-node Redis + Kafka |
| `docker/redis/docker-compose.redis.yml` | Redis only |
| `docker/kafka/docker-compose.kafka.yml` | Kafka only |

## Local Three-Node Stack

Start the three-node stack manually:

```bash
./mvnw clean package -DskipTests
mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)
docker compose -f docker/dsv/docker-compose.dsv-redis-kafka-3nodes.yml up -d --build
```

The apps listen on:

- `http://127.0.0.1:8081`
- `http://127.0.0.1:8082`
- `http://127.0.0.1:8083`

Each app in the three-node stack has its own Redis service, matching the Kubernetes sidecar model:

- app1 -> redis1, published at `localhost:6381`
- app2 -> redis2, published at `localhost:6382`
- app3 -> redis3, published at `localhost:6383`

## Configuration

Configuration is loaded from `.env` in the project root when you run Docker Compose from the project root:

```env
REDIS_PASSWORD=your-secure-password
SPRING_PROFILES_ACTIVE=dev
```

Kafka and Redis connection settings are provided by the compose files for containerized runs.

## Redis

Redis stores secret shards. The local config in `docker/redis/redis.conf` enables:

- AOF persistence with `appendfsync everysec`
- RDB snapshots
- no key eviction
- password authentication

## Standalone Image

```bash
./mvnw clean package -DskipTests
mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)
docker build -t distributed-secrets-vault .
docker run -p 8080:8080 distributed-secrets-vault
```

For a standalone container connected to external services:

```bash
docker run \
  -e "SPRING_PROFILES_ACTIVE=prod" \
  -e "SPRING_DATA_REDIS_HOST=redis.example.com" \
  -e "SPRING_DATA_REDIS_PASSWORD=yourpassword" \
  -e "KAFKA_BOOTSTRAP_SERVERS=kafka.example.com:9092" \
  -p 8080:8080 distributed-secrets-vault
```
