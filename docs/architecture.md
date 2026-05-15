# Distributed Secrets Vault system architecture

1. General architecture

- Client makes requests to the gateway
- Gateway is set up with HAProxy & keepalived
- Gateway sends requests to any node in the cluster (leaderless)

```mermaid
graph LR
    Client([Client])
    Gateway[Gateway<br/>HAProxy + Keepalived] --> LeaderlessCluster

    subgraph LeaderlessCluster[Leaderless Cluster]
        direction LR
        Node1[Cluster Node 1]
        Node2[Cluster Node 2]
        Node3[Cluster Node 3]
        Nodes[...]
        NodeN[Cluster Node N]
    end

    Client -->|HTTP/S Requests| Gateway
```

---

2. User identity

- User identity and authentication are outside the current DSV backend runtime.
- The vault service stores and retrieves secret shards; it no longer depends on a relational database.

---

3. A User puts a new secret in the storage

- Client sends the secret with the secret's key to any cluster node
- Gateway attaches request timestamp metadata before forwarding the write
- Receiving node runs two-phase commit for `user:key`:
  - voting phase: nodes vote on write-lock ownership
  - writing phase: lock owner writes shards and then releases lock
- Receiving node applies Shamir's Secret Sharing in memory, splitting the secret into n shards
- Node distributes n-1 shards to other nodes and keeps 1 shard locally
- k shards are required to reconstruct the secret (threshold scheme)
- **Plaintext secret is never written to durable storage or passed between nodes**
- No master key is required; secrets are protected by distribution

```mermaid
sequenceDiagram
    participant User
    participant Gateway
    participant Node as Cluster Node
    participant Cluster as Other Nodes

    User->>Gateway: POST /secret {key, value}
    Gateway->>Gateway: Attach request timestamp metadata
    Gateway->>Node: Forward request + timestamp metadata
    Node->>Cluster: Voting phase: request write lock for user:key
    Cluster-->>Node: Vote ACKs
    Node->>Node: Split secret into n shards (in memory)<br/>Plaintext never written to disk
    Node->>Cluster: Writing phase: distribute n-1 shards (encrypted in transit)
    Node->>Node: Store local shard to disk
    Cluster->>Cluster: Store received shards to disk
    Cluster-->>Node: Write ACK
    Node->>Cluster: Release write lock
    Node-->>Gateway: Success + Version
    Gateway-->>User: Secret stored (version)
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
    participant Gateway
    participant Node as Cluster Node
    participant Cluster as Other Nodes

    User->>Gateway: GET /secret/{key}?version={v}
    Gateway->>Node: Forward request
    Node->>Node: Load local shard from disk
    Node->>Cluster: Request k-1 additional shards
    Cluster-->>Node: Return shards (encrypted in transit)
    Node->>Node: Reconstruct plaintext in memory<br/>using Shamir's algorithm (k of n shards)
    Node-->>Gateway: Return secret value
    Gateway-->>User: Secret value
```

---

5. A user updates a stored secret (version control)

- Create and update both use the same two-phase commit flow with distributed write locks.
- Phase 1 (voting phase): nodes vote on lock ownership for `user:key`; writes are blocked on other nodes until lock is released.
- Phase 2 (writing phase): after lock quorum is reached, shards are distributed and persisted; lock is released after commit/rollback.
- The gateway attaches request timestamp metadata to incoming write requests.
- Each successful write returns a new secret version
- A user can request either a specific version of the secret or the latest
- Update creates a new set of shards for the new version (independent from previous version shards)
- **Each version is independently sharded; old shards remain for version history**

```mermaid
sequenceDiagram
    participant User
    participant Gateway
    participant Node1 as Cluster Node
    participant Nodes as Other Nodes

    User->>Gateway: PUT /secret/{key} {new_value}
    Gateway->>Gateway: Attach request timestamp metadata
    Gateway->>Node1: Forward update request + timestamp metadata
    Node1->>Nodes: Voting phase: request write lock for user:key
    Nodes-->>Node1: Vote responses
    Node1->>Node1: Lock quorum reached
    Node1->>Node1: Split new secret value into n shards (in memory)<br/>Plaintext never written to disk
    Node1->>Nodes: Writing phase: distribute n-1 shards with new version
    Node1->>Node1: Store local shard to disk
    Nodes->>Nodes: Store received shards to disk
    Nodes-->>Node1: Write ACK
    Node1->>Nodes: Release write lock
    Node1-->>Gateway: Success + New Version
    Gateway-->>User: Secret updated (version: N+1)
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
    participant Gateway
    participant Node as Cluster Node
    participant Cluster as Other Nodes

    User->>Gateway: DELETE /secret {key}
    Gateway->>Node: Forward delete request
    Node->>Cluster: Broadcast delete for key shards
    Cluster-->>Node: Delete ACKs
    Node->>Node: Verify ACK count >= m-k+1
    Node-->>Gateway: Success (non-reconstructable)
    Gateway-->>User: Secret deleted
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

- If failure occurs in the **voting phase**, no shard writes are committed and lock requests expire/rollback.
- If failure occurs in the **writing phase**, partially written shards are rolled back using the write transaction ID before lock release.
- Recovered nodes rejoin automatically and only accept writes after lock state is synchronized.
