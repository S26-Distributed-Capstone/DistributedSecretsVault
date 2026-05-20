# Cluster Test Scripts

These scripts only send DSVClient traffic to an already-running DSV cluster.
They do not start Docker, run `kubectl`, or open a port-forward.

Default gateway:

```bash
http://192.168.8.11
```

Run the full gauntlet:

```bash
./scripts/test-dsvclient-gauntlet.sh
```

Run against a different gateway:

```bash
./scripts/test-dsvclient-gauntlet.sh http://192.168.8.11
```

Run one phase:

```bash
./scripts/test-dsvclient-high-concurrency.sh http://192.168.8.11
./scripts/test-dsvclient-mixed-concurrency.sh http://192.168.8.11
./scripts/test-dsvclient-same-key-namespaces.sh http://192.168.8.11
./scripts/test-dsvclient-failure.sh http://192.168.8.11
```

Expected DSVClient location defaults to `../DSVClient`. Override only if needed:

```bash
DSV_CLIENT_DIR=/path/to/DSVClient ./scripts/test-dsvclient-gauntlet.sh http://192.168.8.11
```

The high-concurrency phase defaults to 2000 flows with 200 parallel workers.
Each flow runs create, get, update, get-all, and delete.
