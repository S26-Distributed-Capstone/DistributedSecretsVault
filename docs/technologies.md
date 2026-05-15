# Tools and Technologies

## Java 25

**Why we chose it:** Java is the language and framework the team chose for building a robust backend system.

**Alternatives considered:**
- **Python:** Used for the client application, but not chosen for the backend due to performance considerations in a distributed, latency-sensitive system.

---

## Spring Boot 4.0.2

**Why we chose it:** Spring Boot provides a production-ready framework with auto-configuration, dependency injection, and a rich ecosystem of integrations (web, data, validation). It dramatically reduces boilerplate and lets us focus on business logic instead of infrastructure wiring.

**Alternatives considered:**
- **Core Java without Spring:** Would require building production-ready infrastructure (dependency injection, request handling, validation) from scratch, which is not practical for a capstone project.

### Spring Web MVC

**Why we chose it:** Provides a straightforward annotation-driven REST API layer (`@RestController`, `@RequestMapping`) that integrates cleanly with the rest of the Spring ecosystem.

### Spring Data JPA

**Why we chose it:** Gives us a repository abstraction over PostgreSQL, eliminating most SQL boilerplate while still allowing custom queries when needed.

### Spring Data Redis

**Why we chose it:** Provides a Spring-idiomatic client for Redis, including `RedisTemplate` and repository support, so secret shards can be read and written with the same patterns used for relational data.

### Spring Validation

**Why we chose it:** Declarative bean validation (`@Valid`, `@NotNull`, etc.) keeps input-validation logic out of service code and produces consistent error responses.

---

## Maven (with Maven Wrapper)

**Why we chose it:** Maven is the most widely used Java build tool and integrates natively with Spring Boot's parent POM. The `mvnw` wrapper ensures every developer and CI runner uses the same Maven version without a separate install step.

---

## PostgreSQL 18

**Why we chose it:** PostgreSQL is a proven, open-source relational database with strong ACID guarantees. We use it exclusively for user account and authentication-related data management. For production we run one primary and two synchronous standbys for redundancy.

**Alternatives considered:**
- **SQLite:** Not suitable for a multi-node, concurrent server environment.

---

## Redis 8 (with AOF Persistence)

**Why we chose it:** Redis provides fast in-memory storage with configurable durability. We store secret shards in Redis because shard reads and writes must be extremely fast (they happen on every secret retrieval), and AOF persistence with `appendfsync everysec` gives us at most one second of data loss on failure—acceptable for this use case.

**Configuration highlights:**
- AOF (Append-Only File) persistence enabled
- RDB snapshots every 15 minutes
- No key eviction (secrets are never auto-deleted)
- Password authentication required

**Alternatives considered:**
- **Pure PostgreSQL for shards:** Would work, but is slower for the high-frequency shard reads/writes and adds unnecessary relational overhead for key-value data.

---

## Shamir's Secret Sharing (`codahale/shamir` 0.7.0)

**Why we chose it:** Shamir's Secret Sharing is the cryptographic foundation of the entire project. It allows a secret to be split into *n* shards such that any *k* of them can reconstruct the original, while fewer than *k* shards reveal nothing. The `codahale/shamir` library is a well-audited, minimal Java implementation of the algorithm. The Shamir algorithm is the focus of the project and no alternative was considered.

---

## Docker & Docker Compose

**Why we chose it:** Docker makes the entire stack (application, Redis, PostgreSQL) reproducible across developer machines and CI environments with a single command. Docker Compose lets us define multi-container topologies (full dev stack, production PostgreSQL cluster) as version-controlled YAML files. We plan to integrate Kubernetes in the future for production orchestration.

---

## HAProxy

**Why we chose it:** HAProxy acts as the gateway in front of the leaderless cluster, distributing incoming HTTP requests across all available nodes. It is battle-tested for high-throughput load balancing and supports health checks so failed nodes are automatically removed from rotation.

**Alternatives considered:**
- **Nginx:** Also a capable reverse proxy, but HAProxy's load-balancing algorithms and health-check semantics are more fine-grained for TCP/HTTP balancing across a cluster.
- **Traefik:** Cloud-native and Kubernetes-aware, but adds complexity we don't need at this stage.

---

## Keepalived

**Why we chose it:** Keepalived provides a virtual IP (VIP) that floats between gateway instances using VRRP. If the active HAProxy node fails, Keepalived promotes a standby so the cluster remains reachable without a DNS change.

**Alternatives considered:**
- **Relying on a single HAProxy instance:** Simpler but introduces a single point of failure at the gateway layer, which contradicts the distributed-availability goals of the project.

---

## Lombok

**Why we chose it:** Lombok generates repetitive Java boilerplate (getters, setters, constructors, `equals`/`hashCode`, builders) at compile time via annotations. This keeps model and DTO classes concise without sacrificing type safety.

**Alternatives considered:**
- **Java Records:** Suitable for immutable data carriers, but lack the builder pattern and mutable-field support needed for JPA entities.
- **Writing boilerplate by hand:** Too verbose and error-prone for a team that wants to focus on distributed-systems logic.

---

## Apache Commons Pool2

**Why we chose it:** Commons Pool2 provides the connection-pool implementation underlying Spring Data Redis's Lettuce driver. It ensures Redis connections are reused across requests rather than opened and closed on every operation, which is critical for low-latency shard access.

---

## Apache Kafka 3.7.0 (Future Integration)

**Why we chose it:** Kafka will provide a persistent, strictly ordered commit log to serve as a distributed queue. We will use Kafka topics to reliably order concurrent mutations (Create, Update, Delete) to the same secret key across multiple nodes, thus establishing a foundation for race-condition tie-breaking in our Two-Phase Commit (2PC) coordinate logic. This prevents using ad-hoc table locks.

**Alternatives considered:**
- **Redis distributed locks:** Could solve concurrency races, but Kafka guarantees strict event ordering natively without risking deadlocks from crashed nodes holding locks.
- **RDBMS locking:** We want to minimize PostgreSQL serialization load.

---

## Eclipse Temurin 25 (Docker base image)

**Why we chose it:** Eclipse Temurin is the Adoptium (formerly AdoptOpenJDK) distribution of OpenJDK. It is free, regularly patched, and widely recommended as the default JDK image for production Docker containers.
