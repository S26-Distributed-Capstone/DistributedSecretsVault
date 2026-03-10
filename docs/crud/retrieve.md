# Retrieve Secret(s)

A client may retrieve secrets in three ways:

- **Latest version** — omit the version parameter; the cluster resolves and returns the most recent version
- **Specific version** — supply an explicit version number to retrieve an exact historical snapshot
- **All versions** — request every stored version, receiving a map of version → secret value

In every case the receiving node collects at least k shards (the reconstruction threshold) from itself and peer nodes, rebuilds the plaintext exclusively in memory, and returns the value to the client. The plaintext is never written to durable storage.

---

## Table of Contents

**Happy Path**

- [1. Retrieve Latest Version](#1-retrieve-latest-version)
- [2. Retrieve Specific Version](#2-retrieve-specific-version)
- [3. Retrieve All Versions](#3-retrieve-all-versions)

**Error Cases**

- [4. Secret Not Found](#4-secret-not-found)
- [5. Version Not Found](#5-version-not-found)
- [6. Insufficient Shards](#6-insufficient-shards)
- [7. Not Authorized to Access Secret](#7-not-authorized-to-access-secret)
- [8. Gateway Unavailable](#8-gateway-unavailable)
- [9. Node Unavailable](#9-node-unavailable)
- [10. Lamport Clock Unavailable](#10-lamport-clock-unavailable)
- [11. Local Shard Read Failure](#11-local-shard-read-failure)
- [12. Version Enumeration Failure](#12-version-enumeration-failure)
- [13. Shard Reconstruction Failure](#13-shard-reconstruction-failure)

---

## Happy Paths

### 1. Retrieve Latest Version

- Client sends a GET request for a secret key without specifying a version
- Gateway forwards the request to any cluster node (leaderless routing)
- Receiving node consults the cluster-wide Lamport clock to resolve the current latest version number
- Node loads its own local shard for `user:key:latest-version` from durable storage
- Node requests k-1 additional shards from peer nodes
- Node reconstructs the plaintext secret in memory using Shamir's algorithm (k of n shards)
- Secret value is returned to the client; plaintext is cleared from memory immediately

```mermaid
sequenceDiagram
    participant User
    participant Gateway
    participant Node as Cluster Node
    participant Clock as Lamport Clock
    participant Peers as Other Nodes

    User->>Gateway: GET /secret/{key}
    Gateway->>Node: Forward request
    Node->>Clock: Resolve latest version for {key}
    Clock-->>Node: Latest version number
    Node->>Node: Load local shard for user:key:version from storage
    Node->>Peers: Request k-1 shards for user:key:version
    Peers-->>Node: Return shards (encrypted in transit)
    Node->>Node: Reconstruct plaintext in memory<br/>using Shamir's algorithm (k of n shards)
    Node-->>Gateway: Return secret value + version
    Gateway-->>User: Secret value + version
```

---

### 2. Retrieve Specific Version

- Client sends a GET request for a secret key with an explicit version number
- Gateway forwards the request to any cluster node
- Receiving node skips version resolution — it uses the requested version directly
- Node loads its own local shard for `user:key:requested-version` from durable storage
- Node requests k-1 additional shards from peer nodes for the same version
- Node reconstructs the plaintext secret in memory using Shamir's algorithm (k of n shards)
- Secret value for the requested version is returned to the client; plaintext is cleared from memory

```mermaid
sequenceDiagram
    participant User
    participant Gateway
    participant Node as Cluster Node
    participant Peers as Other Nodes

    User->>Gateway: GET /secret/{key}?version={v}
    Gateway->>Node: Forward request
    Node->>Node: Load local shard for user:key:v from storage
    Node->>Peers: Request k-1 shards for user:key:v
    Peers-->>Node: Return shards (encrypted in transit)
    Node->>Node: Reconstruct plaintext in memory<br/>using Shamir's algorithm (k of n shards)
    Node->>Node: Clear plaintext from memory
    Node-->>Gateway: Return secret value + version
    Gateway-->>User: Secret value (version: v)
```

---

### 3. Retrieve All Versions

- Client sends a GET request for a secret key requesting all versions
- Gateway forwards the request to any cluster node
- Receiving node queries its local storage to enumerate all known versions of `user:key`
- For each version, the node loads its local shard and requests k-1 shards from peers
- Each version's plaintext is reconstructed independently in memory and added to the result map
- Plaintext for each version is cleared from memory as soon as it is added to the map
- Node returns the complete map of version → secret value to the client

```mermaid
sequenceDiagram
    participant User
    participant Gateway
    participant Node as Cluster Node
    participant Peers as Other Nodes

    User->>Gateway: GET /secret/{key}/versions
    Gateway->>Node: Forward request
    Node->>Node: Enumerate all stored versions for user:key
    loop For each version V
        Node->>Node: Load local shard for user:key:V from storage
        Node->>Peers: Request k-1 shards for user:key:V
        Peers-->>Node: Return shards (encrypted in transit)
        Node->>Node: Reconstruct plaintext in memory<br/>using Shamir's algorithm (k of n shards)
        Node->>Node: Add V → secret value to result map<br/>Clear plaintext from memory
    end
    Node-->>Gateway: Return map {version → secret value}
    Gateway-->>User: Map of all versions to secret values
```

---

## Error Cases

### 4. Secret Not Found

TODO

---

### 5. Version Not Found

TODO

---

### 6. Insufficient Shards

TODO

---

### 7. Not Authorized to Access Secret

TODO

---

### 8. Gateway Unavailable

TODO

---

### 9. Node Unavailable

TODO

---

### 10. Lamport Clock Unavailable

TODO

---

### 11. Local Shard Read Failure

TODO

---

### 12. Version Enumeration Failure

TODO

---

### 13. Shard Reconstruction Failure

TODO
