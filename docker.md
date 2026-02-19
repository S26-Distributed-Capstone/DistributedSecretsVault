# Docker Deployment Guide

This guide covers building and running the Distributed Secrets Vault using Docker.

## Prerequisites

- Docker installed and running
- Java 25+ (for local builds)

## Quick Start

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

The application will be available at `http://localhost:8080`

## Configuration

> **Note:** Currently, the application runs with default Spring Boot configuration. 
> Startup configuration requirements will be documented here as features are implemented.

### Environment Variables

Pass environment variables using `-e` flag:

```bash
docker run -e "SPRING_PROFILES_ACTIVE=prod" -p 8080:8080 distributed-secrets-vault
```

### Volume Mounts

For persistent data or configuration files (when needed):

```bash
docker run -v /path/to/config:/config -p 8080:8080 distributed-secrets-vault
```

## Development

### Rebuilding After Code Changes

Layered Dockerfile enables fast rebuilds when only application code changes:

```bash
./mvnw clean package
(cd target/dependency; jar -xf ../*.jar)
docker build -t distributed-secrets-vault .
```

Only the application layer (~5MB) rebuilds; dependencies (~45MB) remain cached.

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
