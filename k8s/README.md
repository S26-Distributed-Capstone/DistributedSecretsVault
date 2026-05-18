# Kubernetes Configuration

This directory contains Kubernetes manifests for Distributed Secrets Vault. The app runs as a StatefulSet, each app pod has a Redis sidecar for shard storage, and Kafka runs as a StatefulSet for commit messaging.

## Structure

```text
k8s/
├── production/
│   ├── app-service.yaml
│   ├── app-statefulset.yaml
│   ├── ingress.yaml
│   ├── kafka-service.yaml
│   └── kafka-statefulset.yaml
├── testing/
│   ├── app-service.yaml
│   ├── app-statefulset.yaml
│   ├── ingress.yaml
│   ├── kafka-service.yaml
│   └── kafka-statefulset.yaml
└── README.md
```

## Architecture

- `dsv-app` is a StatefulSet.
- Redis runs as a sidecar inside every `dsv-app` pod and persists data through a per-pod PVC.
- `dsv-app-headless` exposes pod DNS records for ScaleCube peer discovery.
- `dsv-app-service` load-balances HTTP traffic to healthy app pods.
- Kafka is available at `kafka.default.svc.cluster.local:9092`.

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
kubectl get pods -w
```

The testing app manifest uses `imagePullPolicy: Never`, so the image must exist in the local cluster's Docker image store.

## Production

```bash
kubectl apply -f k8s/production/
```

Before production use, replace placeholder image and ingress details with the registry image and hostnames for the target cluster.

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

- `SEED_DNS_HOST=dsv-app-headless.default.svc.cluster.local`
- `SEED_DNS_PORT=4801`
- `CLUSTER_PORT=4801`

The headless service exposes port `4801` so each worker can resolve and join active peer pods.
