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

2. New user signs up

- TODO: finalize the approach
- Ideas: hash/encrypt user credentials, use a system that doesn't require the user to store credentials (OPAQUE)
- Assume that Oauth2 is set up

---

3. A User puts a secret in the storage

- Client sends the secret with the secret's key to any cluster node
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

    User->>Gateway: PUT /secret {key, value}
    Gateway->>Node: Forward request
    Node->>Node: Split secret into n shards (in memory)<br/>Plaintext never written to disk
    Node->>Cluster: Distribute n-1 shards (encrypted in transit)
    Node->>Node: Store local shard to disk
    Cluster->>Cluster: Store received shards to disk
    Cluster-->>Node: ACK
    Node-->>Gateway: Success + Version
    Gateway-->>User: Secret stored (version)
```

---

4. A user gets a stored secret from the storage

- Client requests the secret using the secret's unique key and version
- The user may request all versions of a secret, which will return a map of version to secret value
- Receiving node requests k shards from other nodes (minimum threshold to reconstruct)
- Node retrieves its own local shard from storage
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

- Each time a secret is updated (or stored), the cluster returns the secret version
- Version is determined using a cluster-wide clock system (Lamport?)
- A user can request either a specific version of the secret or the latest
- Update creates a new set of shards for the new version (independent from previous version shards)
- **Each version is independently sharded; old shards remain for version history**

```mermaid
sequenceDiagram
    participant User
    participant Gateway
    participant Node1 as Cluster Node
    participant Clock as Lamport Clock
    participant Nodes as Other Nodes

    User->>Gateway: PUT /secret/{key} {new_value}
    Gateway->>Node1: Forward update request
    Node1->>Clock: Request next version
    Clock-->>Node1: New version number
    Node1->>Node1: Split new secret value into n shards (in memory)<br/>Plaintext never written to disk
    Node1->>Nodes: Distribute n-1 shards with new version
    Node1->>Node1: Store local shard to disk
    Nodes->>Nodes: Store received shards to disk
    Nodes-->>Node1: ACK
    Node1-->>Gateway: Success + New Version
    Gateway-->>User: Secret updated (version: N+1)
```

---

6. Cluster node stores its parts in a map

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
        E1["alice:db-password:1 → shard_a1"]
        E2["alice:db-password:2 → shard_a2"]
        E3["bob:api-key:1 → shard_b1"]
    end

    E1 -.-> KV
    E2 -.-> KV
    E3 -.-> KV
```

---

7. Node failure detection

- Use logging with heartbeats and gossip to determine node status

```mermaid
sequenceDiagram
    participant Node1
    participant Node2
    participant Node3
    participant Cluster as Other Nodes

    loop Periodic Heartbeats
        Node1->>Cluster: Heartbeat
        Cluster-->>Node1: Heartbeat ACK
    end

    loop Gossip Protocol
        Node1->>Cluster: Gossip: Node states
        Cluster->>Node1: Gossip: Node states
    end

    Note over Node1,Cluster: Node3 stops responding

    Node1->>Node1: Detect Node3 timeout
    Node1->>Cluster: Gossip: Node3 failed
    Cluster-->>Node1: Confirm: Node3 failed
```

---

8. Node failure recovery

- TODO: finalize the approach
