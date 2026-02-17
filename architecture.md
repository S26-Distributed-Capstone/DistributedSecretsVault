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

- Send the secret with the secret's key to the cluster
- Cluster uses Shamir's secret sharing algorithm and then spreads parts of the secret

```mermaid
sequenceDiagram
    participant User
    participant Gateway
    participant Node as Cluster Node
    participant Cluster as Other Nodes

    User->>Gateway: PUT /secret {key, value}
    Gateway->>Node: Forward request
    Node->>Node: Apply Shamir's Secret Sharing
    Node->>Cluster: Distribute secret parts
    Cluster-->>Node: ACK
    Node-->>Gateway: Success + Version
    Gateway-->>User: Secret stored (version)
```

---

4. A user gets a stored secret from the storage

- Request the secret using the secret's unique key and version
- The user may request all versions of a secret, which will return a map of version to secret value
- A cluster member requests all the parts for the requested secret (UDP multicast?)
- The cluster member then rebuilds the secret and returns it to the user

```mermaid
sequenceDiagram
    participant User
    participant Gateway
    participant Node as Cluster Node
    participant Cluster as Other Nodes

    User->>Gateway: GET /secret/{key}?version={v}
    Gateway->>Node: Forward request
    Node->>Cluster: Multicast: Request parts
    Cluster-->>Node: Return parts
    Node->>Node: Reconstruct secret<br/>using Shamir's algorithm
    Node-->>Gateway: Return secret value
    Gateway-->>User: Secret value
```

---

5. A user updates a stored secret (version control)

- Each time a secret is updated (or stored), the cluster returns the secret version
- Version is determined using a cluster-wide clock system (Lamport?)
- A user can request either a specific version of the secret or the latest

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
    Node1->>Node1: Apply Shamir's Secret Sharing
    Node1->>Nodes: Distribute secret parts<br/>with new version
    Nodes-->>Node1: ACK
    Node1-->>Gateway: Success + New Version
    Gateway-->>User: Secret updated (version: N+1)
```

---

6. Cluster node stores its parts in a map

- Each node stores its part of the secret in a KV store
- Each node maps the user:key:version to the part of the secret

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
