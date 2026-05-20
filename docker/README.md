# Docker Configuration

This directory contains Docker configuration for the Distributed Secrets Vault.

## Structure

```text
docker/
├── dsv/
│   ├── docker-compose.dsv.yml                    # App only
│   ├── docker-compose.dsv-redis.yml              # App + Redis
│   ├── docker-compose.dsv-redis-kafka.yml        # App + Redis + Kafka
│   └── docker-compose.dsv-redis-kafka-3nodes.yml # Three DSV app instances + per-node Redis
├── redis/
│   ├── docker-compose.redis.yml                  # Redis only
│   └── redis.conf                                # Redis persistence and security config
├── kafka/
│   └── docker-compose.kafka.yml                  # Kafka only, KRaft mode
└── README.md
```

Project root:

```text
.env.example                              # Environment variable template
.env                                      # Local config, gitignored
```

## Setup

Create a local environment file from the project root:

```bash
cp .env.example .env
```

Set a Redis password for any real local use:

```env
REDIS_PASSWORD=your-secure-password-here
SPRING_PROFILES_ACTIVE=dev
```

Build and start the Redis + Kafka stack:

```bash
./mvnw clean package -DskipTests
mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)
docker compose -f docker/dsv/docker-compose.dsv-redis-kafka.yml up --build
```

The API listens on `http://localhost:8080`.

## Three App Nodes

For local cluster-like testing, run three DSV app instances against shared Kafka and one Redis service per app node:

```bash
./mvnw clean package -DskipTests
mkdir -p target/dependency && (cd target/dependency && jar -xf ../*.jar)
docker compose -f docker/dsv/docker-compose.dsv-redis-kafka-3nodes.yml up -d --build
```

Apps listen on `8081`, `8082`, and `8083`. Redis instances for those nodes are published on `6381`, `6382`, and `6383`. Each app points at its own Redis service and sets a different `NODE_NAME` so Kafka consumer groups differ and every node receives `secrets-commit` messages.

Manual check:

```bash
curl -sS http://127.0.0.1:8081/api/temp-test/kafka
docker logs dsv-app-1 2>&1 | grep -i "Received commit" | tail -3
docker logs dsv-app-2 2>&1 | grep -i "Received commit" | tail -3
docker logs dsv-app-3 2>&1 | grep -i "Received commit" | tail -3
```

## Services

`redis` stores secret shards durably with AOF persistence and password auth. In the three-node stack this is split into `redis1`, `redis2`, and `redis3`, one per app node.

`kafka` provides commit fanout and ordering infrastructure in KRaft mode.

`app` is the Spring Boot DSV service built from the repository root Dockerfile.

## Commands

```bash
# Start full local stack
docker compose -f docker/dsv/docker-compose.dsv-redis-kafka.yml up

# Start in background
docker compose -f docker/dsv/docker-compose.dsv-redis-kafka.yml up -d

# View logs
docker compose -f docker/dsv/docker-compose.dsv-redis-kafka.yml logs -f app
docker compose -f docker/dsv/docker-compose.dsv-redis-kafka.yml logs -f redis
docker compose -f docker/dsv/docker-compose.dsv-redis-kafka.yml logs -f kafka

# Stop services
docker compose -f docker/dsv/docker-compose.dsv-redis-kafka.yml down

# Clean slate, including volumes
docker compose -f docker/dsv/docker-compose.dsv-redis-kafka.yml down -v
```

All services communicate on the `dsv-network` bridge network. Single-app stacks connect to Redis as `redis`; the three-node stack connects `app1`, `app2`, and `app3` to `redis1`, `redis2`, and `redis3`. Kafka is reachable as `kafka:29092`.
