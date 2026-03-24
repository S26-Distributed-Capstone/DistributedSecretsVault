## Components You Will Build

You will build a distributed secrets vault system. It includes running services and a small set of required definitions that make system behavior clear and usable by other systems.

---

## A. Running Components (Processes)

### 1. Secrets API Service

A long-running HTTP service that accepts requests for creating, updating, retrieving, and deleting secrets.

It will:

- Accept structured JSON input
- Authenticate requests and derive the caller’s identity
- Accept secret **create** requests that establish a new secret
- Reject create requests for secrets that already exist
- Accept secret **update** requests that create a new version of an existing secret
- Reject update requests for secrets that do not exist
- Accept secret retrieval requests for previously stored secrets
- Accept requests to retrieve the version history of a secret
- Accept secret **delete** requests that remove enough stored shards to make reconstruction impossible
- Reject delete requests for secrets that do not exist
- Accept `.env` file content and:
  - expand `secret(NAME)` references by retrieving authoritative secret values
  - process `enc(NAME)` references by creating new secrets and returning `secret(NAME)`
- Fail the entire `.env` request if any referenced secret already exists or cannot be resolved
- Enforce unauthorized rejections without revealing information on internal state
- Enforce caller-scoped isolation on reads and writes
- Expose basic health and status endpoints

The API validates and forwards requests but does not determine secret existence or authoritative state.

---

### 2. Secrets Vault Service

A long-running service responsible for authoritative storage of secret state.

It will:

- Validate and persist proposed secret creations
- Validate and persist secret updates as new versions
- Validate and execute secret deletes using a threshold-based deletion rule
- Maintain a clear distinction between secret existence and secret updates
- Persist multiple versions of its parts of a secret
- Record validity intervals for each secret version
- Shard secret pieces to peer vault instances
- Serve retrieval, history, and delete requests based on authoritative state
- Remain available under partial failure (within range of accepted Shamir's recovery threshold)
- Recover state on restart

---

## B. Required Artifacts (Not Processes)

These are not separate running services. They are required artifacts that define what your running system means and how other systems interact with it.

### 3. Secret, History, and Isolation Model

A shared behavioral model implemented consistently across all components.

It defines:

- How callers are identified and scoped
- What it means for a secret to exist and be retrievable
- The distinction between secret creation and secret update
- How secret updates are versioned
- How historical secret values are retained
- How secret deletion is defined and when a secret is considered non-reconstructable
- What identifiers are used to reference secrets
- How isolation is enforced during retrieval and history access
- How retries and concurrent requests are handled
- What duplicate and _not found_ errors mean under isolation

The model must be documented and observable in practice.

---

### 4. Encryption and Storage Record Definition

A required definition of how secrets are stored durably.

It defines:

- The stored record fields for each secret version, including metadata and the shard a node is privy to
- Shamir's Secret Keeping behavior:
  - A password is separated on a single node into n (configured by user) parts
  - The data is sent out to n - 1 other nodes, with 1 piece staying local to the machine that received the request directly
- no master key required, any node can take requests to decode
- The rule that plaintext secret bytes are never written to durable storage or passed to other nodes

---

### 5. External API Contract

A small, explicit interface describing how clients interact with the system.

It will:

- Define request and response formats
- Clearly distinguish secret creation from secret update
- Define delete request and response behavior, including threshold-based deletion success criteria
- Specify duplicate and _not found_ error behavior
- Describe durability, replication, and isolation guarantees
- Describe secret-keeping and spreading behavior and failure behavior when referenced secrets cannot be resolved
- Describe secret history retrieval semantics, including version ordering and validity timestamps
- Describe `.env` encryption and expansion semantics, including secret creation and all-or-nothing failure
- Hide internal sharding and coordination mechanics

The contract exists to decouple system behavior from implementation details.
