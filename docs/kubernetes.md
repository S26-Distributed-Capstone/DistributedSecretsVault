# Kubernetes Architecture and Configuration

The Kubernetes deployment runs DSV as a leaderless app cluster with per-node Redis sidecars and a Kafka broker. All manifests use the **`dsv`** namespace.

## High-Level Architecture

```mermaid
graph TD
    Client([Client])
    Ingress[Ingress]
    Service[dsv-app-service]
    Kafka[(Kafka)]

    Client --> Ingress
    Ingress --> Service

    subgraph AppPods[DSV StatefulSet]
        App1[DSV App 0 + Redis Sidecar]
        App2[DSV App 1 + Redis Sidecar]
        App3[DSV App 2 + Redis Sidecar]
        AppN[DSV App N + Redis Sidecar]
    end

    Service --> App1
    Service --> App2
    Service --> App3
    Service --> AppN

    App1 <--> Kafka
    App2 <--> Kafka
    App3 <--> Kafka
    AppN <--> Kafka
```

## DSV App and Redis

The app runs as a StatefulSet. Each pod includes:

- a Spring Boot DSV container
- a Redis sidecar on `localhost:6379`
- a PVC mounted into Redis for durable shard storage

This keeps shard storage local to the DSV pod while still allowing Kubernetes to reschedule pods with their persistent volumes.

## Cluster Discovery

`dsv-app-headless` is a headless service that returns DNS records for app pods. ScaleCube uses:

- `SEED_DNS_HOST=dsv-app-headless.dsv.svc.cluster.local`
- `SEED_DNS_PORT=4801`
- `CLUSTER_PORT=4801`

The app service `dsv-app-service` separately provides load-balanced HTTP traffic on port **9080** (container `SERVER_PORT=9080`, avoiding common **8080** conflicts).

## Kafka

Kafka runs as a single-broker KRaft StatefulSet in the current manifests. DSV app pods connect through:

```text
KAFKA_BOOTSTRAP_SERVERS=kafka.dsv.svc.cluster.local:9092
```

## Testing Environment

`k8s/testing` is intended for Docker Desktop, Minikube, or K3d. It runs three DSV app replicas without production node-affinity constraints.

```bash
kubectl apply -f k8s/testing/
kubectl get pods -w
```

## Production Environment

`k8s/production` targets a multi-node cluster (e.g. 3 control-plane + 10 workers):

- app pods avoid control-plane nodes
- app pods use pod anti-affinity (one DSV pod per worker)
- **10 replicas** with `cluster.totalNodes=10`, `thresholdK=6`, `quorumM=6`

Full steps (scp manifests, import image on all workers, verify): [production-kubernetes-deploy.md](production-kubernetes-deploy.md).

```bash
kubectl apply -f k8s/production/ --dry-run=client
kubectl apply -f k8s/production/
```
