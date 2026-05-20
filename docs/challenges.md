## Distributed Systems Challenges

1. **Shard Creation and Distribution**  
   Secrets are split with Shamir’s Secret Sharing, one shard is retained locally, and `n-1` shards are sent to peers. Plaintext is never durably stored. Each node persists only its assigned encrypted shard, which limits blast radius if a single node is compromised.

2. **Quorum-Based Reconstruction**  
   Reads collect at least `k` shards and reconstruct only in memory. If fewer than `k` shards are available, the read fails deterministically instead of returning partial or stale data.

3. **Create vs Update Under Concurrency**  
   Create requires non-existent key; update requires existing key. Both use the same Kafka-based two-phase write flow. This keeps write ordering consistent while preserving operation-specific preconditions.

4. **Versioning and Time Metadata**  
   The DSV Worker attaches request timestamp metadata. Versions are committed in per-key Kafka order. This avoids relying on a global clock source while maintaining monotonic per-key history.

5. **History and Validity Intervals**  
   Each version is independently stored and retrievable. `valid_from`/`valid_to` define active intervals. Intervals are updated during commits so historical reads can be served without ambiguity.

6. **Replication of Authoritative State**  
   Shards replicate through write quorum. Metadata converges through commit propagation and gossip. Any node can therefore answer existence/version queries from local replicated metadata.

7. **Retries and Idempotency**  
   Safe retries return existing committed outcomes. Duplicate create returns `409`; duplicate identical update is idempotent. This lets clients retry on timeout without risking duplicate state transitions.

8. **Namespace Isolation**  
   Secrets are separated into logical namespaces (`user:key:version`) allowing different groups to reuse key names. Pre-condition checks are enforced on every request path before shard access.

9. **Deterministic Failure Semantics**  
   Precondition failures are stable (`409` for duplicate create, `404` for missing update/retrieve/delete). Equivalent requests against equivalent cluster state produce the same status code.

10. **`.env` Batch Semantics**  
    `enc(NAME)` and `secret(NAME)` processing is all-or-nothing; failures roll back staged writes. Callers receive either a fully transformed file or a single error response.

11. **Failure Phases for Writes**  
    - **Ordering phase failure**: Kafka commit log write failed; no intent published.  
    - **Writing phase failure**: intent published but write quorum fails; partial writes roll back.
    Phase separation makes recovery behavior explicit and prevents ambiguous outcomes for in-flight writes.

12. **Recovery and Availability**  
    Nodes recover from their peers by synchronizing the current shard inventory and fetching missing shards locally. On the first cluster startup, recovery no-ops because there are no peers yet. Quorum rules determine whether reads/writes continue or fail fast during degraded periods.
