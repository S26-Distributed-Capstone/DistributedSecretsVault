# Kubernetes Configuration

This directory contains Kubernetes manifests for Distributed Secrets Vault. The app runs as a StatefulSet, each app pod has a Redis sidecar for shard storage, and Kafka runs as a StatefulSet for commit messaging.

**Deploy with Kustomize** (`kubectl apply -k …`) so the DSV image is resolved to Docker Hub. See [docs/docker-hub.md](../docs/docker-hub.md).

## Structure

```text
k8s/
├── image.env.example          # Docker Hub user/tag for build/push scripts
├── production/
│   ├── kustomization.yaml     # → docker.io/noambensim/distributed-secrets-vault:latest
│   ├── namespace.yaml
│   ├── app-service.yaml
│   ├── app-statefulset.yaml
│   ├── ingress.yaml
│   ├── kafka-service.yaml
│   └── kafka-statefulset.yaml
├── testing/
│   ├── kustomization.yaml
│   ├── patches/
│   └── …
└── README.md
```

All resources use the **`dsv`** namespace.

## Architecture

- `dsv-app` is a StatefulSet.
- Redis runs as a sidecar inside every `dsv-app` pod and persists data through a per-pod PVC.
- `dsv-app-headless` exposes pod DNS records for ScaleCube peer discovery.
- `dsv-app-service` load-balances HTTP traffic to healthy app pods on port **9080**.
- Kafka is available at `kafka.dsv.svc.cluster.local:9092`.

Production keeps one app pod per worker node (affinity + anti-affinity). Testing drops those constraints for local clusters.

## Publish image (Docker Hub)

```bash
cp k8s/image.env.example k8s/image.env
docker login
./scripts/docker-build-push.sh
```

## Testing environment

```bash
kubectl apply -k k8s/testing/
kubectl get pods -n dsv -w
```

Pulls the same Docker Hub image (`imagePullPolicy: IfNotPresent`). For fully offline local images, build and tag locally as `docker.io/noambensim/distributed-secrets-vault:latest` or edit `k8s/testing/kustomization.yaml`.

## Production environment

Tuned for **10 worker nodes** (Shamir k=3 / quorum m=5). See [docs/production-kubernetes-deploy.md](../docs/production-kubernetes-deploy.md).

```bash
kubectl apply -k k8s/production/ --dry-run=client
kubectl apply -k k8s/production/
```

Workers pull [noambensim/distributed-secrets-vault:latest](https://hub.docker.com/r/noambensim/distributed-secrets-vault) automatically.

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

- `SEED_DNS_HOST=dsv-app-headless.dsv.svc.cluster.local`
- `SEED_DNS_PORT=4801`
- `CLUSTER_PORT=4801`
