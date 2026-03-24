## Distributed Systems Challenges You Will Need to Address

You are expected to design, implement, and explain how your system handles:

- Shard creation and distribution: splitting secrets into n shards in memory, securely distributing n-1 shards to peers, storing 1 shard locally
- Quorum-based reconstruction: collecting k shards from k nodes and reconstructing secrets in memory only
- Distinguishing create and update operations under concurrency
- Versioned updates using cluster-wide logical timestamps (Lamport clock)
- Tracking and serving historical secret versions
- Defining validity intervals for secret values  
- Replication of authoritative state across all nodes
- Correct handling of retries and idempotency
- Isolation between different callers or tenants
- Coordinated creation and retrieval of multiple secrets in a single operation
- Deterministic failure when duplicate or missing secrets are encountered
- Deterministic transformation of `.env` files
- Node failures during read or write operations
- Restart and recovery without manual intervention
- Quorum availability: remaining operational while maintaining security with at least k healthy nodes
- Making behavior observable and explainable
- Heartbeat and gossip protocols for failure detection and node state dissemination
