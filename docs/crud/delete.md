# Delete Secret

A client can delete a secret by sending a DELETE request specifying the secret key. The delete request is broadcast to all n nodes and is considered successful once at least m − k + 1 delete acknowledgments are received (ensuring fewer than k shards remain and the secret can no longer be reconstructed).

---

## Table of Contents

**Happy Path**

- [1. Delete one secret](#1-delete-one-secret)

**Error Cases**

- [2. Secret not found](#2-secret-not-found)
- [3. Authentication failure](#3-authentication-failure)
- [4. Invalid request](#4-invalid-request)

---

## 1. Delete one secret

- The client sends a DELETE request to the gateway specifying the secret key.
- The gateway forwards the request to the cluster; the receiving node broadcasts the delete to all n nodes.
- Each node checks its local storage for a shard matching the key, deletes it, and returns an acknowledgment.
- After m − k + 1 acknowledgments are received (or the timeout is reached with that threshold met), the deletion is confirmed and the client receives a 204 No Content response.
- **Response**: `204 No Content`

```mermaid
sequenceDiagram
    participant Client
    participant Controller as SecretController
    participant Service as DeleteSecretService
    participant Node1 as Cluster Node 1
    participant Node2 as Cluster Node 2
    participant NodeN as Cluster Node N

    Client->>Controller: DELETE /secret/{key}
    activate Controller
    Controller->>Controller: Validate DeleteSecretRequest
    Controller->>Service: invoke delete(key)
    activate Service
    Service->>Node1: Broadcast delete shard request
    Service->>Node2: Broadcast delete shard request
    Service->>NodeN: Broadcast delete shard request

    activate Node1
    Node1->>Node1: Find shard for key
    Node1->>Service: Return success acknowledgment
    deactivate Node1

    activate Node2
    Node2->>Node2: Find shard for key
    Node2->>Service: Return success acknowledgment
    deactivate Node2

    activate NodeN
    NodeN->>NodeN: Find shard for key
    NodeN->>Service: Return success acknowledgment
    deactivate NodeN

    Service->>Service: Collect acknowledgments (threshold: m − k + 1)
    deactivate Service
    Controller-->>Client: 204 No Content
    deactivate Controller
```

---

## 2. Secret not found

- The specified key does not correspond to any existing secret in the cluster.
- No node returns a shard for that key; the deletion threshold cannot be met.
- The client receives: "Secret not found".
- **Response**: `404 Not Found`

---

## 3. Authentication failure

- The client's credentials are missing, expired, or invalid.
- The request is rejected before reaching the cluster.
- The client receives: "Unauthorized".
- **Response**: `401 Unauthorized`

---

## 4. Invalid request

- The request is malformed, for example the key field is missing or empty.
- The controller rejects the request during validation before forwarding it.
- The client receives: "Bad request".
- **Response**: `400 Bad Request`
