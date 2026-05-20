# Peer Recovery

When a node joins a running cluster, it catches up from its peers by:

1. Discovering peer nodes via ScaleCube.
2. Asking peers for the secret keys and versions they currently store.
3. Comparing that cluster inventory with its own local Redis data.
4. Requesting any missing shards from peers.
5. Storing recovered shards locally so it can participate in Shamir reconstruction.

---

## First cluster startup

When the very first node starts a brand-new cluster, there are no peers yet. In that case:

- peer discovery returns empty
- recovery exits cleanly
- nothing is fetched or stored
- the node continues normal startup

So first-time boot is safe and effectively a no-op.

---

## New node joining an existing cluster

When a new node joins a cluster that already has data:

- the node discovers existing peers
- peers export their current state
- the joining node downloads missing shards
- the joining node stores those shards in Redis
- the node becomes queryable for shard reads and Shamir reconstruction

That means newly added nodes are automatically brought up to date.

---

## Internal endpoints

Each node exposes these internal peer-only endpoints:

- `GET /internal/recovery/state`
- `GET /internal/recovery/shard/{user}/{key}/{version}`
- `GET /internal/recovery/health`

These are used only by other cluster nodes.

---

## Recovery config usage

Recovery is always on. There is no on/off switch for peer recovery.

The recovery config only tunes how startup behaves:

- `delay-seconds` — how long to wait after startup before checking peers
- `peer-connectivity-timeout-seconds` — how long to wait for peers to appear
- `min-required-peers` — how many peers must be visible before catch-up starts

For a brand-new cluster, these settings do not cause problems because the node simply finds no peers and exits recovery cleanly. When a new node joins an existing cluster, the same settings let it wait briefly, discover peers, and pull the missing shards it needs.

---
