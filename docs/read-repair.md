# Read Repair Design

## Goal

GET requests should keep the cluster healthy when reconstruction is still possible but shard availability is close to the Shamir threshold.

If a latest-version GET collects only `k` or `k + repairTriggerBuffer` shards, the coordinating node reconstructs the plaintext value in memory, re-splits it into the configured `n` shards for the same version, and stages a best-effort repair before returning the value.

Repair does not create a new secret version.

## Trigger

Read repair is controlled by `ClusterConfig`:

- `cluster.repairEnabled=true`
- `cluster.repairTriggerBuffer=1`

Repair is considered only for latest-version reads. Explicit historical version reads and all-version reads do not rewrite stored shards.

The trigger condition is:

```text
repairEnabled
AND totalNodes > thresholdK
AND availableShardCount >= thresholdK
AND availableShardCount <= thresholdK + repairTriggerBuffer
```

## Flow

1. `InternalGetService` collects local and peer shards for the latest version.
2. If fewer than `k` shards are available, GET fails as before.
3. If at least `k` shards are available, GET reconstructs the plaintext as before.
4. If shard availability is near threshold, `InternalRepairService`:
   - re-splits the plaintext into `n` shards,
   - stages the local shard in `PendingActionsBuffer` as `ActionType.REPAIR`,
   - sends repair prepare requests to peers,
   - publishes a Kafka commit if repair quorum is reached.
5. `RepairCommitHandler` saves the staged shard at the same version.
6. GET returns the reconstructed value even if repair cannot complete.

Plaintext is never written to durable storage or sent to peer nodes. Peers receive only Shamir shards.

## Concurrency Semantics

Repair follows the same prepare + Kafka commit shape used by create, update, and delete. It is intentionally best-effort under the chosen snapshot-style GET semantics:

- A successful GET returns the value it reconstructed.
- A concurrent PUT may create a newer version while repair is running. Repair still writes only the old version number, so it does not create or overwrite the newer latest version.
- A concurrent DELETE is not rechecked immediately before returning the reconstructed GET value.
- If a repair prepare, quorum, or Kafka publish fails, the repair is discarded and the GET still succeeds.

This keeps GET latency and behavior predictable while allowing reads to heal weakly replicated latest versions.
