# Kubernetes Configuration

This directory contains Kubernetes manifests for Distributed Secrets Vault. The app runs as a StatefulSet, each app pod has a Redis sidecar for shard storage, and Kafka runs as a StatefulSet for commit messaging.

## Structure

```text
k8s/
├── production/
│   ├── namespace.yaml
│   ├── app-service.yaml
│   ├── app-statefulset.yaml
│   ├── ingress.yaml
│   ├── kafka-service.yaml
│   └── kafka-statefulset.yaml
├── testing/
│   ├── namespace.yaml
│   ├── app-service.yaml
│   ├── app-statefulset.yaml
│   ├── ingress.yaml
│   ├── kafka-service.yaml
│   └── kafka-statefulset.yaml
└── README.md
```

All resources use the **`dsv`** namespace.

## Architecture

- `dsv-app` is a StatefulSet.
- Redis runs as a sidecar inside every `dsv-app` pod and persists data through a per-pod PVC.
- `dsv-app-headless` exposes pod DNS records for ScaleCube peer discovery.
- `dsv-app-service` load-balances HTTP traffic to healthy app pods on port **9080** (avoids common **8080** conflicts).
- Kafka is available at `kafka.dsv.svc.cluster.local:9092`.

The production manifests keep the one-app-pod-per-worker-node placement strategy through node affinity and pod anti-affinity. The testing manifests remove those scheduling constraints for Docker Desktop, Minikube, or K3d.

## Local Testing

Build the local image first:

```bash
./mvnw clean package -DskipTests
mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)
docker build -t dsv-backend:latest .
```

Then deploy:

```bash
kubectl apply -f k8s/testing/
kubectl get pods -n dsv -w
```

The testing app manifest uses `imagePullPolicy: Never`, so the image must exist in the local cluster's Docker image store.

## Production

Tuned for **10 worker nodes** (one `dsv-app` pod per worker, Shamir k=6 / quorum m=6). See [docs/production-kubernetes-deploy.md](../docs/production-kubernetes-deploy.md) for scp, image import on all workers, and verification steps.

```bash
kubectl apply -f k8s/production/ --dry-run=client
kubectl apply -f k8s/production/
```

Before production use, load `dsv-backend:latest` on every worker (or switch to a registry image) and set ingress host/TLS as needed.

## App Environment

| Variable | Purpose |
| --- | --- |
| `NODE_NAME` | StatefulSet pod identity |
| `POD_IP` | Pod IP used for cluster membership |
| `CLUSTER_PORT` | ScaleCube transport port |
| `SEED_DNS_HOST` | Headless service DNS name for peer discovery |
| `SEED_DNS_PORT` | ScaleCube seed port |
| `SPRING_DATA_REDIS_HOST` | `localhost`, because Redis is a sidecar |
| `SPRING_DATA_REDIS_PORT` | Redis sidecar port |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka service DNS endpoint |

## ScaleCube Discovery

ScaleCube startup is DNS-based:

- `SEED_DNS_HOST=dsv-app-headless.dsv.svc.cluster.local`
- `SEED_DNS_PORT=4801`
- `CLUSTER_PORT=4801`

The headless service exposes port `4801` so each worker can resolve and join active peer pods.
