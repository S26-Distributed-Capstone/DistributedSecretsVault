# Create Secret

A client can create a secret only if another secret with the same key doesn't already exist. The secret will be sent to all n nodes, and will not persist until confirmation from m (a number in between k and n) nodes is received.

---

## Table of Contents

**Happy Path**

- [1. Create one secret](#1-create-one-secret)
- [2. Create two secrets](#2-create-two-secrets)

**Error Cases**

---

## 1. Create one secret

- Client sends a POST request to create a new secret
- Gateway forwards the request to any cluster node (leaderless routing)
- Node saves initial timestamp in Lamport clock
- Node runs Shamir's algorithm to split the secret into n (number of nodes in the cluster) shards
- Node sends shards to other nodes
- Upon receiving a shard, the other nodes check to see if they already contain the same key as the secret that is trying to be created, send back confirmation if key doesn't already exist
- Node keeps a shard for itself, checks if it already contains the key for the secret either persisted or temporary, and adds itself to the confirmations if it doesn't
- Node waits for confirmation from m (a number in between k and n and more than n-m) nodes (2 Phase Commit)
- Node sends message to nodes to persist any shard for this secret that is being temporarily stored
- Node persists shard that it is temporarily storing
- Confirmation sent back to user

```mermaid
sequenceDiagram
    participant User
    participant Gateway
    participant Node as Cluster Node
    participant Peers as Other Nodes
    participant Clock as Lamport Clock

    User->>Gateway: POST /secret {key,value}
    Gateway->>Node: Forward request
    Node->>Clock: Save intial timestamp
    Clock-->>Node: Send back conformation
    Node->>Node: Run Shamir's algorithm to split secret into n shards
    Node->>Peers: Send n-1 shards to other nodes
    Peers->>Peers: Check if key already exists in node
    Node->>Node: Node checks if the key for the secret already exists on<br> the node, and then adds a confirmation to the count <br>of the other nodes if key does not already exist
    Peers-->>Node: Return confirmation
    Node->>Node: Wait for conformation from m nodes
    Node->>Peers: Tell nodes to persist shards
    Peers-->>Node: Send back confirmation
    Node->>Node: Persist shard
    Node-->>Gateway: Return confirmation that secret was created
    Gateway-->>User: "Secret Created"
```

---

## 2. Create two secrets

- Client sends a POST request to create a new secret
- Client sends another a POST request to create a second secret with the same key
- Gateway forwards each request to any cluster node (leaderless routing) (can be to the same node or different nodes, but it doesn't matter because precedence is determined by Lamport clock)
- Node save initial timestamps in Lamport clock
- Node run Shamir's algorithm to split the secrets into n (number of nodes in the cluster) shards
- Node sends shards of each secret to other nodes
- Upon receiving a shard, the other nodes check to see if they already persisted the same key as the secret that is trying to be created, and then check their temporary stored secret shards that have not yet been persisted if any of the keys match the key of this secret
- If it does match a key temporarily stored, checks Lamport clock to find out which one came first
- Continues with the shard that came first, aborts for the shard that came second
- Node also keeps a shard for itself, checks if it already contains the key for the secret persisted, and then checks temporary (non-persisted) secrets to see if any have the same key
- If yes, then check Lamport clock which one was first
- Continues with the shard that came first, aborts for the shard that came second
- Node waits for confirmation from m (a number in between k and n and more than n-m) nodes (2 Phase Commit) for each secret
- Node times out waiting for later created secret
- Node sends error message through gateway back to client that the later secret creation failed
- Node sends message to nodes to persist any shard for the earlier created secret that is being temporarily stored
- Node persists shard that it is temporarily storing for earlier created secret
- Confirmation sent back for the secret which was created

```mermaid
sequenceDiagram
    participant User
    participant Gateway
    participant Node as Cluster Node
    participant Peers as Other Nodes
    participant Clock as Lamport Clock

    par Secret 1
      User->>Gateway: POST /secret {key,value}
      Gateway->>Node: Forward request
      Node->>Clock: Save intial timestamp
      Clock-->>Node: Send back conformation
      Node->>Node: Run Shamir's algorithm to split each secret into n shards
      Node->>Peers: Send n-1 shards to other nodes
      Peers->>Peers: Check if key already exists in node or in temporary storage
      Peers->>Clock: If key is already in temporary storage, check clock to see which one came first
      Clock-->>Peers: Clock confirms this user request came first
      Node->>Node: Node checks if the key for the secret already exists on the node
      Node->>Clock: If key is already in temporary storage on the node, check clock to see which one came first
      Clock-->>Node: Clock confirms this user request came first
      Node->>Node: Node adds a confirmation to the count of the other nodes
      Peers-->>Node: Return confirmation
      Node->>Node: Wait for conformation from m nodes
    and Secret 2
      User->>Gateway: POST /secret {same key as secret 1,value (could be different)}
      Gateway->>Node: Forward request
      Node->>Clock: Save intial timestamp
      Clock-->>Node: Send back conformation
      Node->>Node: Run Shamir's algorithm to split each secret into n shards
      Node->>Peers: Send n-1 shards to other nodes
      Peers->>Peers: Check if key already exists in node or in temporary storage
      Peers->>Clock: If key is already in temporary storage, check clock to see which one came first
      Clock-->>Peers: Clock says this user request came second
      Node->>Node: Node checks if the key for the secret already exists on the node
      Node->>Clock: If key is already in temporary storage on the node, check clock to see which one came first
      Clock-->>Node: Clock says this user request came second
      Node->>Node: Wait for conformation from m nodes
      Node-->>Gateway: Send error message upon timeout
      break after error message is sent to user
        Gateway-->>User: "Secret 2 failed to create"
      end
    end
    Node->>Peers: Tell nodes to persist shards
    Peers-->>Node: Send back confirmation
    Node->>Node: Persist shard
    Node-->>Gateway: Return confirmation that secret was created
    Gateway-->>User: "Secret 1 Created"
```

---



