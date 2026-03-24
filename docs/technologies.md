# Tools and Technologies

> List the tools and technologies you plan on using. For each, explain why you decided to use that particular tool or technology. List other options you evaluated and explain why you chose not to use them.

---

## Java 25

**Why we chose it:** Java is the team's primary language, and Java 25 (the current LTS release) gives us access to the latest platform features and performance improvements. The JVM ecosystem offers mature libraries for cryptography, networking, and persistence—all central to this project.

**Alternatives considered:**
- **Go:** Excellent performance and simple concurrency model, but the team has more experience with Java, and Spring Boot's ecosystem provides significantly more out-of-the-box infrastructure for the patterns we need.
- **Python:** Too slow for a latency-sensitive distributed system and weaker typing makes large codebases harder to maintain safely.

---

## Spring Boot 4.0.2

**Why we chose it:** Spring Boot provides a production-ready framework with auto-configuration, dependency injection, and a rich ecosystem of integrations (web, data, validation). It dramatically reduces boilerplate and lets us focus on business logic instead of infrastructure wiring.

**Alternatives considered:**
- **Quarkus:** A strong alternative for cloud-native Java with fast startup, but Spring Boot's larger community and more mature documentation made it the lower-risk choice for a time-constrained capstone.
- **Micronaut:** Similar trade-offs to Quarkus—good for AOT compilation, but less familiar to the team and fewer examples in the distributed-systems space.
- **Raw servlets / Jetty:** Too low-level; would require building much of what Spring Boot provides for free.

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

**Alternatives considered:**
- **Gradle:** More flexible and often faster for large multi-module builds, but adds configuration complexity that isn't warranted for a single-module project.

---

## PostgreSQL 18

**Why we chose it:** PostgreSQL is a proven, open-source relational database with strong ACID guarantees. We use it to store user account information and secret metadata. For production we run one primary and two synchronous standbys for redundancy.

**Alternatives considered:**
- **MySQL / MariaDB:** Comparable feature set for basic CRUD, but PostgreSQL's replication model (streaming replication, synchronous standbys) is more robust and better documented for the high-availability pattern we need.
- **SQLite:** Not suitable for a multi-node, concurrent server environment.
- **CockroachDB:** A fully distributed SQL database that would handle replication automatically, but introduces significant operational complexity and changes the SQL dialect.

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
- **Cassandra:** Well-suited for distributed key-value workloads but operationally heavier; overkill for the current cluster size.
- **etcd:** Designed for configuration/coordination rather than high-throughput data storage.

---

## Shamir's Secret Sharing (`codahale/shamir` 0.7.0)

**Why we chose it:** Shamir's Secret Sharing is the cryptographic foundation of the entire project. It allows a secret to be split into *n* shards such that any *k* of them can reconstruct the original, while fewer than *k* shards reveal nothing. The `codahale/shamir` library is a well-audited, minimal Java implementation of the algorithm.

**Why this approach over alternatives:**
- **AES encryption with a master key:** Requires storing and protecting a master key, which is the exact single point of failure we want to eliminate.
- **HashiCorp Vault's key-splitting:** Conceptually similar (Shamir is used internally) but adds an external service dependency and doesn't let us control the sharding logic ourselves.

---

## Docker & Docker Compose

**Why we chose it:** Docker makes the entire stack (application, Redis, PostgreSQL) reproducible across developer machines and CI environments with a single command. Docker Compose lets us define multi-container topologies (full dev stack, production PostgreSQL cluster) as version-controlled YAML files.

**Alternatives considered:**
- **Podman:** A rootless alternative to Docker; compatible with our Compose files but less universally installed on developer machines.
- **Kubernetes:** The production-grade container orchestrator, but too much operational overhead for a capstone project. We may layer it on top of Docker later.

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

**Alternatives considered:**
- **Jedis connection pool:** An older Redis client; Lettuce (backed by Commons Pool2) is now the default in Spring Data Redis and supports non-blocking I/O.

---

## Eclipse Temurin 25 (Docker base image)

**Why we chose it:** Eclipse Temurin is the Adoptium (formerly AdoptOpenJDK) distribution of OpenJDK. It is free, regularly patched, and widely recommended as the default JDK image for production Docker containers.

**Alternatives considered:**
- **`openjdk` official image:** Deprecated in favor of distribution-specific images like Temurin.
- **GraalVM native image:** Would produce a much smaller, faster-starting binary, but native compilation is incompatible with some Spring Boot features we use and adds significant build complexity.
