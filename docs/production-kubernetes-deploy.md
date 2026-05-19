# Production deployment on Kubernetes (k3s)

Guide for deploying Distributed Secrets Vault to a cluster with **3 control-plane nodes** and **10 worker nodes**, using manifests in `k8s/production/`.

The DSV app image is pulled from **Docker Hub** on each node (no per-worker `ctr import`). See [docker-hub.md](docker-hub.md) for build, push, and registry setup.

All resources run in the **`dsv`** namespace.

## What gets deployed

| Resource | Count | Scheduling |
|----------|-------|------------|
| `dsv-app` StatefulSet | 10 pods | Workers only; one pod per host (anti-affinity) |
| `kafka` StatefulSet | 1 pod | Workers only |
| PVCs | 11 × RWO | 10 × Redis (5Gi) + 1 × Kafka (10Gi) |
| Services + Ingress | Traefik → `dsv-app-service` | HTTP API on port **9080** |

Default image: `docker.io/noambensim/distributed-secrets-vault:latest` ([Docker Hub](https://hub.docker.com/r/noambensim/distributed-secrets-vault), via Kustomize).

Cluster parameters (10 nodes, Shamir **k=6**, write quorum **m=6**) are set via `JAVA_TOOL_OPTIONS` in `app-statefulset.yaml`.

## Prerequisites

- `kubectl` with cluster access
- Image published to Docker Hub (public, or private with `imagePullSecrets`)
- Default **StorageClass** for PVCs (k3s: `local-path`)
- Traefik ingress (bundled with k3s)
- **10 schedulable worker nodes** for the default replica count

## 1. Publish the image to Docker Hub

```bash
cp k8s/image.env.example k8s/image.env
# Edit DOCKERHUB_USERNAME if your Hub user differs

docker login
./scripts/docker-build-push.sh
```

Or use GitHub Actions (**Publish Docker image**) with `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` secrets.

## 2. Copy manifests to the remote machine (optional)

```bash
scp -r k8s/production user@REMOTE:/tmp/dsv-k8s/
```

You only need the YAML directory if applying from that host; the cluster pulls the image from Docker Hub automatically.

## 3. Validate before apply

```bash
kubectl apply -k k8s/production/ --dry-run=client
# or from remote copy:
kubectl apply -k /tmp/dsv-k8s/ --dry-run=client

kubectl get nodes -l '!node-role.kubernetes.io/control-plane,!node-role.kubernetes.io/master'
kubectl get storageclass
```

## 4. Deploy

```bash
kubectl apply -k k8s/production/
kubectl get pods -n dsv -w
```

Ordered apply (optional):

```bash
kubectl apply -k k8s/production/ --server-side=false  # or apply resources individually
kubectl wait -n dsv --for=condition=ready pod/kafka-0 --timeout=300s
```

Expect `kafka-0` and `dsv-app-0` … `dsv-app-9` Running.

## 5. Verify

```bash
kubectl get pods -n dsv -o wide
kubectl get pvc -n dsv
kubectl get ingress -n dsv
```

```bash
kubectl port-forward -n dsv svc/dsv-app-service 9080:9080
curl -s http://127.0.0.1:9080/actuator/health
curl -s http://127.0.0.1:9080/api/v1/cluster/status
```

Via Traefik: `curl -s http://<worker-or-lb-ip>/actuator/health`

## Tuning

| Setting | Location | Notes |
|---------|----------|--------|
| Docker image | `k8s/production/kustomization.yaml` | `images.newName` / `newTag` |
| Replica count | `app-statefulset.yaml` | Match worker count |
| Shamir / quorum | `JAVA_TOOL_OPTIONS` | For `N` nodes: `k = N/2+1`, `m = k` |
| Ingress host/TLS | `ingress.yaml` | Production DNS |
| Private Hub repo | `imagePullSecrets` | See [docker-hub.md](docker-hub.md) |

## Troubleshooting

| Symptom | Cause | Fix |
|---------|--------|-----|
| `ErrImagePull` / `ImagePullBackOff` | Image missing on Hub, wrong name/tag, or private without secret | `docker pull` from a worker; check kustomization; add pull secret |
| `ImagePullBackOff` 401 | Private repo | `kubectl create secret docker-registry ...` + `imagePullSecrets` |
| `dsv-app-*` Pending | Anti-affinity / insufficient workers | Need 10 workers; `kubectl describe pod` |
| PVC Pending | No StorageClass | Configure `local-path` or other provisioner |
| 503 on writes | Cluster not fully up | Wait for 10 pods; check `/api/v1/cluster/nodes` |

## Teardown

```bash
kubectl delete -k k8s/production/
```
