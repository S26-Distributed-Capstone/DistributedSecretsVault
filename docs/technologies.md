# Tools and Technologies

## Java 25

Java is the backend language for the DSV service.

## Spring Boot 4

Spring Boot provides application wiring, configuration, validation, actuator health endpoints, and the REST API layer through Spring Web MVC.

## Spring Data Redis

Redis is the durable shard store. Spring Data Redis provides the Redis client integration used by the repository implementation.

## Redis 8

Redis stores secret shards as key-value data. The Docker configuration enables AOF persistence, RDB snapshots, password authentication, and no eviction.

## Apache Kafka 3.7

Kafka provides commit fanout and ordered messaging infrastructure for distributed mutation coordination.

## ScaleCube

ScaleCube handles cluster membership and peer discovery for DSV app nodes.

## Shamir's Secret Sharing

The `codahale/shamir` library implements the cryptographic split/reconstruct primitive used to divide a secret into `n` shards with a `k` shard reconstruction threshold.

## Maven

Maven builds the Spring Boot application and manages Java dependencies.

## Docker and Docker Compose

Docker makes the app, Redis, and Kafka reproducible across developer machines. Docker Compose defines the single-node and three-node local stacks.

## Kubernetes

Kubernetes manifests under `k8s/` run DSV app pods with Redis sidecars and Kafka for local or production-style orchestration.

## Lombok

Lombok reduces boilerplate in model and DTO classes.

## Apache Commons Pool2

Commons Pool2 backs Lettuce Redis connection pooling.

## Eclipse Temurin 25

The Docker image uses Eclipse Temurin as the Java runtime base image.
