# DistributedSecretsVault

The distributed secrets vault backend repo.
https://docs.google.com/document/d/1nvzYSdccBbdk0uiQnFu74rlTIZ1Uh9fU-n-h0eZdvn0/edit?usp=sharing

# Distributed Secrets Vault

## Project Overview

A distributed secrets vault that stores secret values durably across a leaderless cluster and serves them to authorized callers. Secrets are protected through **Shamir's Secret Sharing** rather than traditional encryption.

The system exposes a simple HTTP API for creating, updating, and retrieving secrets. Each secret is split into n shards (in memory), with 1 shard stored locally and n-1 distributed to other nodes. Reconstruction requires k shards from k different nodes. Updating a secret creates new, independent shards for each version.

Multiple nodes in the cluster coordinate through heartbeats and gossip protocols without a central leader. The system handles concurrent requests safely and remains correct under node failures and restarts.

Secrets are protected using **Shamir's Secret Sharing**: each secret is split into n shards distributed across cluster nodes. No single node can reconstruct a secret; k nodes must cooperate. Plaintext is never stored on disk or transmitted between nodes.

Multiple nodes in a **leaderless cluster** coordinate through heartbeats and gossip protocols without a central leader.

The emphasis of the project is on distributed systems behavior: clear authority over secret existence, durable recording, shard replication, consistent reads under partial failure, concurrency safety, and explainable recovery. System behavior must be observable through logs and state inspection.

---

### 1. [Components and Scope](scope.md)

### 2. [Deliverables & Milestones](milestones.md)

---

## Resulting System Behavior

When the system is running correctly:

- Clients can create new secrets through a simple HTTP API
- Attempts to create a secret that already exists return a duplicate error
- Clients can update existing secrets through a separate API operation
- Attempts to update a non-existent secret return _not found_
- Each update creates a new version while preserving historical values
- Clients can retrieve the latest value of a secret when permitted
- Clients can view the version history of a secret, including when each value was valid
- Clients can submit an `.env` file and:
  - replace `secret(NAME)` references with the caller’s current secret values
  - process `enc(NAME)` references by storing the referenced value as a **new** secret and returning `secret(NAME)`
- If any `enc(NAME)` refers to a secret that already exists, the entire request fails
- Secrets are considered valid only after they are durably recorded
- Secret state is replicated across vault nodes as shards
- Plaintext secret values never exist on disk or between nodes—only Shamir shards are stored
- Each secret is split into n shards; k shards are needed to reconstruct (no master key required)
- Concurrent requests do not create conflicting or duplicate secret records
- Retrieval enforces access boundaries and isolation rules
- Requests for secrets not valid for the caller return _not found_
- Node failures do not corrupt secret state
- Restarted nodes recover state and resume correct operation
- If the configured master key is unavailable or incorrect, retrieval fails deterministically and secrets are not returned
- System behavior under concurrency, failure, and restart is observable

---

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

---

## Team Size and Time Expectations

This project is designed for **3 students**, working approximately **10 hours per week per student** over **14 weeks**.

Every milestone requires a **running, deployable artifact**; documentation must describe what actually runs.
