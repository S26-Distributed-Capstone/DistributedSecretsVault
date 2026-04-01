## Distributed Systems Challenges

1. **Shard Creation and Distribution**  
   Secrets are split with Shamir’s Secret Sharing, one shard is retained locally, and `n-1` shards are sent to peers. Plaintext is never durably stored.

2. **Quorum-Based Reconstruction**  
   Reads collect at least `k` shards and reconstruct only in memory.

3. **Create vs Update Under Concurrency**  
   Create requires non-existent key; update requires existing key. Both use the same lock-based two-phase write flow.

4. **Versioning and Time Metadata**  
   The gateway attaches request timestamp metadata. Versions are committed in per-key lock order.

5. **History and Validity Intervals**  
   Each version is independently stored and retrievable. `valid_from`/`valid_to` define active intervals.

6. **Replication of Authoritative State**  
   Shards replicate through write quorum. Metadata converges through commit propagation and gossip.

7. **Retries and Idempotency**  
   Safe retries return existing committed outcomes. Duplicate create returns `409`; duplicate identical update is idempotent.

8. **Isolation by Caller**  
   Secrets are scoped by authenticated identity (`user:key:version`) and cross-tenant leakage is prevented.

9. **Deterministic Failure Semantics**  
   Precondition failures are stable (`409` for duplicate create, `404` for missing update/retrieve/delete).

10. **`.env` Batch Semantics**  
    `enc(NAME)` and `secret(NAME)` processing is all-or-nothing; failures roll back staged writes.

11. **Failure Phases for Writes**  
    - **Voting phase failure**: lock quorum not reached; no commit.  
    - **Writing phase failure**: lock held but write quorum fails; partial writes roll back.

12. **Recovery and Availability**  
    Nodes recover from durable storage + gossip, discard stale shards by epoch, and rejoin automatically when healthy.
