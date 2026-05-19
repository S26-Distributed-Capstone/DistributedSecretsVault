# Distributed Secrets Vault system architecture

1. General architecture

- Client makes requests to the ingress gateway (Traefik)
- Gateway routes requests to any DSV Worker pod in the cluster
- The DSV Worker handles API requests and shard storage (unified design)

```mermaid
graph LR
    Client([Client])
    Gateway[Ingress Gateway<br/>Traefik] --> LeaderlessCluster

    subgraph LeaderlessCluster[Kubernetes Cluster]
        direction LR
        Node1[DSV Worker 1]
        Node2[DSV Worker 2]
        Node3[DSV Worker 3]
        Nodes[...]
        NodeN[DSV Worker N]
    end

    Client -->|HTTP/S Requests| Gateway
```

---

2. User identity

- User identity and authentication are outside the current DSV backend runtime.
- The vault service stores and retrieves secret shards; it no longer depends on a relational database.

---

3. A User puts a new secret in the storage

- Client sends the secret with the secret's key to the ingress gateway
- A DSV Worker receives the request and acts as the Coordinating Node
- Receiving node attaches request timestamp metadata and starts two-phase commit via Kafka:
  - ordering phase: node publishes a create intent to the strictly ordered Kafka commit log
  - writing phase: node distributes shards via ScaleCube and confirms persistence
- Receiving node applies Shamir's Secret Sharing in memory, splitting the secret into n shards
- Node distributes n-1 shards to other nodes and keeps 1 shard locally
- k shards are required to reconstruct the secret (threshold scheme)
- **Plaintext secret is never written to durable storage or passed between nodes**
- No master key is required; secrets are protected by distribution

```mermaid
sequenceDiagram
    participant User
    participant Ingress as Traefik Ingress
    participant Node as Coordinating Node
    participant Kafka as Kafka Broker
    participant Cluster as Peer Nodes

    User->>Ingress: POST /secret {key, value}
    Ingress->>Node: Forward HTTP request
    Node->>Node: Attach request timestamp metadata
    Node->>Kafka: Publish create intent for user:key
    Kafka-->>Node: Acknowledge strict ordering
    Kafka-->>Cluster: Broadcast intent
    Node->>Node: Split secret into n shards (in memory)<br/>Plaintext never written to disk
    Node->>Cluster: Writing phase: distribute n-1 shards (encrypted in transit)
    Node->>Node: Store local shard to Redis
    Cluster->>Cluster: Store received shards to Redis
    Cluster-->>Node: Write ACK
    Node-->>Ingress: Success + Version
    Ingress-->>User: Secret stored (version)
```

---

4. A user gets a stored secret from the storage

- Client requests the secret using the secret's unique key and version
- The user may request all versions of a secret, which will return a map of version to secret value
- Receiving node requests k - 1 shards from nodes, and gets one from itself (minimum threshold to reconstruct)
- Node reconstructs the plaintext secret in memory using Shamir's algorithm (requires k of n shards)
- **Plaintext exists only in memory during reconstruction, never written to disk**
- Node returns the secret value to the client and clears it from memory

```mermaid
sequenceDiagram
    participant User
    participant Ingress as Traefik Ingress
    participant Node as Coordinating Node
    participant Cluster as Peer Nodes

    User->>Ingress: GET /secret/{key}?version={v}
    Ingress->>Node: Forward HTTP request
    Node->>Node: Load local shard from Redis
    Node->>Cluster: Request k-1 additional shards via ScaleCube
    Cluster-->>Node: Return shards (encrypted in transit)
    Node->>Node: Reconstruct plaintext in memory<br/>using Shamir's algorithm (k of n shards)
    Node-->>Ingress: Return secret value
    Ingress-->>User: Secret value
```

---

5. A user updates a stored secret (version control)

- Create and update both use the same two-phase commit flow with Kafka ordering.
- Phase 1 (ordering phase): node publishes update intent to Kafka; concurrent writes are resolved by commit log order.
- Phase 2 (writing phase): shards are distributed and persisted to Redis.
- The DSV Worker attaches request timestamp metadata to incoming write requests.
- Each successful write returns a new secret version
- A user can request either a specific version of the secret or the latest
- Update creates a new set of shards for the new version (independent from previous version shards)
- **Each version is independently sharded; old shards remain for version history**

```mermaid
sequenceDiagram
    participant User
    participant Ingress as Traefik Ingress
    participant Node1 as Coordinating Node
    participant Kafka as Kafka Broker
    participant Nodes as Peer Nodes

    User->>Ingress: PUT /secret/{key} {new_value}
    Ingress->>Node1: Forward HTTP request
    Node1->>Node1: Attach request timestamp metadata
    Node1->>Kafka: Publish update intent for user:key
    Kafka-->>Node1: Acknowledge strict ordering
    Kafka-->>Nodes: Broadcast intent
    Node1->>Node1: Split new secret value into n shards (in memory)<br/>Plaintext never written to disk
    Node1->>Nodes: Writing phase: distribute n-1 shards with new version
    Node1->>Node1: Store local shard to Redis
    Nodes->>Nodes: Store received shards to Redis
    Nodes-->>Node1: Write ACK
    Node1-->>Ingress: Success + New Version
    Ingress-->>User: Secret updated (version: N+1)
```

---

6. A user deletes a stored secret

- Client sends a delete request for a secret key to any cluster node
- Receiving node broadcasts delete to nodes storing shards for that key
- Deletion is considered successful once at least `m-k+1` delete acknowledgments are confirmed
- This guarantees fewer than `k` persisted shards can remain, so reconstruction is no longer possible
- The client receives success only after the threshold is met

```mermaid
sequenceDiagram
    participant User
    participant Ingress as Traefik Ingress
    participant Node as Coordinating Node
    participant Kafka as Kafka Broker
    participant Cluster as Peer Nodes

    User->>Ingress: DELETE /secret {key}
    Ingress->>Node: Forward HTTP request
    Node->>Kafka: Publish delete intent for user:key (strict ordering)
    Kafka-->>Node: Acknowledge ordering
    Node->>Cluster: Broadcast delete for key shards via ScaleCube
    Cluster-->>Node: Delete ACKs
    Node->>Node: Verify ACK count >= m-k+1
    Node-->>Ingress: Success (non-reconstructable)
    Ingress-->>User: Secret deleted
```

---

7. Cluster node stores its parts in a map

- Each node maps user:key:version to its assigned shard
- No node has enough information to reconstruct a secret alone
- Requires k nodes to collaborate for secret reconstruction
- **No master key is used; security comes from shard distribution**

```mermaid
graph LR
    subgraph "Node KV Store"
        KV[(Key-Value Store)]
    end

    subgraph "Key Structure"
        Key["user:key:version"]
    end

    subgraph "Value Structure"
        Value["Secret Part (Shard)"]
    end

    Key -->|Maps to| Value
    Value -->|Stored in| KV

    subgraph "Example Entries"
        E1["alice:db-password:1 → {shard_a1}"]
        E2["alice:db-password:2 → {shard_a2}"]
        E3["bob:api-key:1 → {shard_b1}"]
    end

    E1 -.-> KV
    E2 -.-> KV
    E3 -.-> KV
```

---

8. Node failure detection

- Node status and cluster membership is managed by ScaleCube and Kubernetes.

---

9. Node failure recovery

- If failure occurs in the **ordering phase**, no shard writes are committed and the request fails.
- If failure occurs in the **writing phase**, partially written shards are rolled back.
- Recovered nodes rejoin automatically via ScaleCube and synchronize state from Kafka and peers.
