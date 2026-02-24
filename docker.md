# Docker Deployment Guide

This guide covers building and running the Distributed Secrets Vault using Docker.

## Prerequisites

- Docker and Docker Compose installed
- Java 25+ (for local builds)

## Quick Start with Docker Compose (Recommended)

The easiest way to run the application with Redis:

```bash
# 1. Setup environment (from project root)
cp .env.example .env
# Edit .env to set your REDIS_PASSWORD

# 2. Build and start all services
./mvnw clean package
mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)
cd docker && docker compose up --build
```

The application will be available at:

- API: `http://localhost:8080`
- Redis: `localhost:6379`

### Docker Compose Commands

```bash
# Start in foreground (see logs)
cd docker && docker compose up

# Start in background
cd docker && docker compose up -d

# View logs
cd docker && docker compose logs -f app
cd docker && docker compose logs -f redis

# Stop services
cd docker && docker compose down

# Stop and remove volumes (clean slate)
cd docker && docker compose down -v

# Rebuild after code changes
./mvnw clean package && mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)
cd docker && docker compose up --build
```

## Configuration

### Environment Variables

Configuration is managed through the `.env` file in the project root:

```env
# Redis Configuration
REDIS_PASSWORD=your-secure-password

# Spring Profile (dev, prod, test)
SPRING_PROFILES_ACTIVE=dev
```

**Security Note:** Never commit `.env` to git. Use `.env.example` as a template.

Docker Compose automatically loads `.env` from the project root (or from `docker/` parent directory).

### Redis Configuration

Redis persistence and performance settings are configured in `docker/redis/redis.conf`:

- AOF persistence with `everysec` fsync
- RDB snapshots every 15 minutes
- No eviction policy (suitable for secrets storage)

## Standalone Docker (Without Compose)

If you need to run without Docker Compose:

### Build the Docker Image

```bash
# Build the JAR and extract layers for optimal caching
./mvnw clean package
mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)

# Build the Docker image
docker build -t distributed-secrets-vault .
```

### Run the Container

```bash
docker run -p 8080:8080 distributed-secrets-vault
```

### With Environment Variables

```bash
docker run -e "SPRING_PROFILES_ACTIVE=prod" \
  -e "SPRING_DATA_REDIS_HOST=redis.example.com" \
  -e "SPRING_DATA_REDIS_PASSWORD=yourpassword" \
  -p 8080:8080 distributed-secrets-vault
```

## Development

### Fast Rebuilding After Code Changes

The layered Dockerfile enables fast rebuilds when only application code changes:

```bash
# Rebuild application
./mvnw clean package
mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)

# Restart with new code
cd docker && docker compose up --build app
```

Only the application layer (~5MB) rebuilds; dependencies (~45MB) remain cached.

### Testing Redis Persistence

TODO: create persistence testing

### Debugging

Enable remote debugging:

```bash
docker run -e "JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,address=5005,server=y,suspend=n" \
  -p 8080:8080 -p 5005:5005 distributed-secrets-vault
```

Connect your IDE debugger to `localhost:5005`

## Alternative: Spring Boot Buildpack

Build without a Dockerfile:

```bash
./mvnw spring-boot:build-image -Dspring-boot.build-image.imageName=distributed-secrets-vault
docker run -p 8080:8080 distributed-secrets-vault
```

## Container Management

```bash
# List running containers
docker ps

# Stop container
docker stop <container-id>

# Remove container
docker rm <container-id>

# Remove image
docker rmi distributed-secrets-vault
```
