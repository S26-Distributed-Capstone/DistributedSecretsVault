# Kubernetes Configuration

This directory contains the Kubernetes (K8s) deployment manifests for the Distributed Secrets Vault. The configuration is tuned for K3s environments but uses standard K8s primitives.

## Structure

```
k8s/
├── production/
│   ├── app-service.yaml            # Headless and ClusterIP services for DSV App routing
│   ├── app-statefulset.yaml        # StatefulSet for Agent nodes (DSV Spring Boot + Redis sidecar)
│   ├── ingress.yaml                # Traefik Ingress configuration to expose the gateway
│   ├── postgres-configmap.yaml     # Wrapper shell script for intelligent Postgres primary/replica discovery
│   └── postgres.yaml               # StatefulSet for the PostgreSQL user database cluster
├── testing/
│   ├── app-service.yaml            # Same as production, for local testing
│   ├── app-statefulset.yaml        # 1 Replica, No Node/Pod Anti-Affinity constraints
│   ├── ingress.yaml                # Local ingress configuration
│   ├── postgres-configmap.yaml     # Wrapper script config
│   └── postgres.yaml               # 1 Replica, No Node/Pod Anti-Affinity constraints
└── README.md                       # This file (You are here)
```

## Architecture & Configuration

The Kubernetes deployment mirrors the production requirements:

1. **Control Plane & Agent Node Segregation (`production` only)**
   - Pods are governed by `nodeAffinity` rules ensuring they do not get scheduled on `node-role.kubernetes.io/control-plane`.
   - Control plane naturally serves as the Load Balancer/Gateway entry point via `ingress.yaml`.
2. **Strict Pod Placements (`production` only)**
   - Hard limits of 1 DSV App (+ Redis sidecar) per physical hardware node are enforced via `podAntiAffinity` rules matching `kubernetes.io/hostname`.
3. **App Architecture**
   - **DSV Worker & Redis:** Redis is deployed as a *sidecar container* inside the DSV `StatefulSet`. If the pod dies, it recovers data natively via Persistent Volumes (`volumeClaimTemplates`).
   - **ScaleCube peer discovery:** ScaleCube bootstrap is DNS-based. Each DSV worker resolves `SEED_DNS_HOST` (the headless worker service) and joins peers on `SEED_DNS_PORT`/`CLUSTER_PORT` (`4801`).
   - **PostgreSQL Database:** Handled via a `StatefulSet` with replicas. A smart wrapper script mounted from `postgres-configmap.yaml` automatically discovers if a pod is the primary (`postgres-0`) or a replica (e.g., `postgres-1`) based on the hostname provided by the `StatefulSet` and initializes replication logic accordingly.
4. **Dynamic Scaling (Standby Strategy)**
   - We target 12 total agent nodes natively requesting `replicas: 12`. If fewer physical worker nodes exist (e.g., 5 nodes available), 5 pods run and 7 remain pending gracefully acting as a standby queue.

## Usage

### Production (Multi-Node Target)
To run the production deployment onto a properly labeled multi-node system (e.g. standard K3s installation).

1. Review and apply Secrets/ConfigMaps to fulfill the Environment Variables if needed natively.
2. Apply the production configurations:
   ```bash
   kubectl apply -f k8s/production/
   ```

### Local Testing (Single-Node Dev)
A lightweight version in `testing/` strips away the Affinity constraints and lowers replica counts, making it perfect for Docker Desktop, Minikube, or K3d local development on a single machine.

1. Apply the testing configurations:
   ```bash
   kubectl apply -f k8s/testing/
   ```
2. Verify rollout:
   ```bash
   kubectl get pods -w
   ```
3. Expose the Ingress endpoint if your local orchestrator requires specific tunings or simply curl the proxy IP endpoint.

## Environment Variables Mapping

Most configurations mirror the `.env` settings expected globally:

| Target Container    | Variables Set via K8s Manifest                       | Description / Source Mapping               |
| ------------------- | ---------------------------------------------------- | ------------------------------------------ |
| `dsv-app`           | `NODE_NAME`, `POD_IP`                                | StatefulSet pod identity used by ScaleCube |
| `dsv-app`           | `CLUSTER_PORT`, `SEED_DNS_PORT`                      | ScaleCube membership transport port        |
| `dsv-app`           | `SEED_DNS_HOST`                                      | Headless service DNS used for peer lookup  |
| `dsv-app`           | `SPRING_DATA_REDIS_HOST="localhost"`                 | Redis operates as a sidecar container      |
| `dsv-app`           | `SPRING_DATASOURCE_URL`                              | Routes to the headless Postgres service    |
| `postgres`          | `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`  | Environment values or `Secret` references  |
| `postgres`          | `POSTGRES_REPLICATION_USER`, `POSTGRES_PRIMARY_HOST` | Bound dynamically for the StatefulSet init |

## ScaleCube Discovery Contract

ScaleCube startup is now fully DNS-based in both `k8s/production` and `k8s/testing`:

- `SEED_DNS_HOST` points to `dsv-app-headless.default.svc.cluster.local`
- `SEED_DNS_PORT` and `CLUSTER_PORT` are set to `4801`
- `dsv-app-headless` exposes port `4801` to provide discoverable endpoints for cluster membership
- Runtime defaults are sourced from `application.properties`:
  - `scalecube.cluster.default-port=4801`
  - `scalecube.dns.resolve.max-attempts=5`
  - `scalecube.dns.resolve.retry-delay-ms=1000`

*Note: In production deployments, it's highly recommended to replace hardcoded values (like `POSTGRES_PASSWORD="POSTGRES_PASSWORD"`) inside `k8s/production/postgres.yaml` with a K8s `Secret` before applying.*