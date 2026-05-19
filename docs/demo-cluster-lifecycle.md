# Local Cluster Lifecycle Demo

This demo runs a local Docker-based Distributed Secrets Vault cluster and exercises the full secret lifecycle across multiple clients and nodes. The script builds the Spring Boot application, generates a temporary Docker Compose file at `target/dsv-demo/docker-compose.demo.yml`, starts Kafka plus one Redis service per app node, runs the demo checks, and then tears the stack down.

## What It Demonstrates

The default run starts a 3-node cluster:

- `app1` on `http://127.0.0.1:8081` with `redis1`
- `app2` on `http://127.0.0.1:8082` with `redis2`
- `app3` on `http://127.0.0.1:8083` with `redis3`
- Kafka for commit fanout and cluster coordination

The demo validates:

- secret creation, latest reads, updates, version reads, all-version reads, and deletion
- multiple users storing the same secret name without leaking values across users
- parallel clients creating secrets against different app nodes
- continued quorum operation when one node and its Redis service are stopped
- node reboot and recovery after the stopped app and Redis service restart
- read rejection when fewer than `K` shards are online
- write rejection when fewer than `M` nodes are available for quorum

With the defaults, the script uses `NODE_COUNT=3`, `THRESHOLD_K=2`, and `QUORUM_M=2`.

## Prerequisites

Install and start:

- Docker with Docker Compose
- Java and the Maven wrapper requirements used by the project
- `curl`
- `jar`, unless running with `SKIP_BUILD=1`

From the project root, create a local environment file if you do not already have one:

```bash
cp .env.example .env
```

The demo defaults to `REDIS_PASSWORD=REDIS_PASSWORD`, matching the local development configuration.

## Run The Demo

From the repository root:

```bash
./scripts/demo-cluster-lifecycle.sh
```

The first run may take longer because Docker needs to pull Redis, Kafka, and Java base images.

## Useful Run Modes

Customize the quorum and reconstruction threshold:

```bash
NODE_COUNT=5 THRESHOLD_K=3 QUORUM_M=3 ./scripts/demo-cluster-lifecycle.sh
```

Move the published ports if the defaults are already in use:

```bash
BASE_PORT=9081 REDIS_BASE_PORT=7381 KAFKA_HOST_PORT=29092 ./scripts/demo-cluster-lifecycle.sh
```

## Configuration Reference

| Variable | Default | Description |
| --- | --- | --- |
| `NODE_COUNT` | `3` | Number of app nodes and Redis instances to run. Must be between `3` and `10`. |
| `BASE_PORT` | `8081` | Host port for app node 1. Node N uses `BASE_PORT + N - 1`. |
| `REDIS_BASE_PORT` | `6381` | Host port for Redis node 1. Redis N uses `REDIS_BASE_PORT + N - 1`. |
| `KAFKA_HOST_PORT` | `19092` | Host port published for Kafka. |
| `THRESHOLD_K` | majority | Number of Shamir shards required to reconstruct a secret. |
| `QUORUM_M` | majority | Number of write acknowledgements required to commit. Must be greater than or equal to `THRESHOLD_K`. |
| `REDIS_PASSWORD` | `REDIS_PASSWORD` | Password configured for every local Redis instance. |
| `SPRING_PROFILES_ACTIVE` | `dev` | Spring profile used by the app containers. |
| `KEEP_STACK` | `0` | Set to `1` to leave containers running after the demo. |
| `SKIP_BUILD` | `0` | Set to `1` to skip Maven packaging and reuse `target/dependency`. |
| `PROJECT_NAME` | `dsv-demo` | Docker Compose project and container name prefix. |

## Troubleshooting

If Docker reports that a port is already allocated, rerun with different `BASE_PORT`, `REDIS_BASE_PORT`, or `KAFKA_HOST_PORT` values.

If the script cannot connect to the Docker daemon, make sure Docker Desktop or the Docker service is running and that your shell has permission to access the Docker socket.