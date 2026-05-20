## Distributed Systems Challenges

1. **Shard Creation and Distribution**  
   Secrets are split with Shamir’s Secret Sharing, one shard is retained locally, and `n-1` shards are sent to peers. Plaintext is never durably stored. Each node persists only its assigned encrypted shard, which limits blast radius if a single node is compromised.

2. **Quorum-Based Reconstruction**  
   Reads collect at least `k` shards and reconstruct only in memory. If fewer than `k` shards are available, the read fails deterministically instead of returning partial or stale data.

3. **Read Repair Under Degraded Replication**  
   Latest-version reads also measure how many shards were actually available. If a GET can reconstruct the value but only has `k` or `k + repairTriggerBuffer` shards, the coordinating node performs best-effort read repair before returning. Repair re-splits the reconstructed plaintext in memory and redistributes shards for the same version through the existing prepare + Kafka commit path. It does not create a new version, and it does not apply to explicit historical reads.

4. **Create vs Update Under Concurrency**  
   Create requires non-existent key; update requires existing key. Both use the same Kafka-based two-phase write flow. This keeps write ordering consistent while preserving operation-specific preconditions.

5. **Versioning and Time Metadata**  
   The DSV Worker attaches request timestamp metadata. Versions are committed in per-key Kafka order. This avoids relying on a global clock source while maintaining monotonic per-key history.

6. **History and Validity Intervals**  
   Each version is independently stored and retrievable. `valid_from`/`valid_to` define active intervals. Intervals are updated during commits so historical reads can be served without ambiguity.

7. **Replication of Authoritative State**  
   Shards replicate through write quorum. Metadata converges through commit propagation and gossip. Any node can therefore answer existence/version queries from local replicated metadata.

8. **Retries and Idempotency**  
   Safe retries return existing committed outcomes. Duplicate create returns `409`; duplicate identical update is idempotent. This lets clients retry on timeout without risking duplicate state transitions.

9. **Namespace Isolation**  
   Secrets are separated into logical namespaces (`user:key:version`) allowing different groups to reuse key names. Pre-condition checks are enforced on every request path before shard access.

10. **Deterministic Failure Semantics**  
   Precondition failures are stable (`409` for duplicate create, `404` for missing update/retrieve/delete). Equivalent requests against equivalent cluster state produce the same status code.

11. **`.env` Batch Semantics**  
    `enc(NAME)` and `secret(NAME)` processing is all-or-nothing; failures roll back staged writes. Callers receive either a fully transformed file or a single error response.

12. **Failure Phases for Writes**  
    - **Ordering phase failure**: Kafka commit log write failed; no intent published.  
    - **Writing phase failure**: intent published but write quorum fails; partial writes roll back.
    Phase separation makes recovery behavior explicit and prevents ambiguous outcomes for in-flight writes.

13. **Recovery and Availability**  
    Nodes recover from durable storage, and rejoin automatically when healthy. Quorum rules determine whether reads/writes continue or fail fast during degraded periods. Read repair improves availability after partial failures by restoring shard redundancy while reads are still reconstructable.

14. **Repair vs Concurrent Mutation**  
    Read repair follows snapshot-style GET semantics. If a GET reconstructs a value, it may return that value even if a PUT or DELETE commits immediately afterward. Repair is version-preserving, so a concurrent PUT creates a newer version rather than being overwritten by repair. A concurrent DELETE is not rechecked before returning the already reconstructed GET result.
