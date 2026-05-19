# Production deployment on Kubernetes (k3s)

Guide for deploying Distributed Secrets Vault to a cluster with **3 control-plane nodes** and **10 worker nodes**, using manifests in `k8s/production/`.

All resources run in the **`dsv`** namespace (`namespace.yaml` is applied first).

## What gets deployed

| Resource | Count | Scheduling |
|----------|-------|------------|
| `dsv-app` StatefulSet | 10 pods | Workers only; one pod per host (anti-affinity) |
| `kafka` StatefulSet | 1 pod | Workers only |
| PVCs | 11 × RWO | 10 × Redis (5Gi) + 1 × Kafka (10Gi) |
| Services + Ingress | Traefik → `dsv-app-service` | HTTP API on port **9080** |

Cluster parameters (10 nodes, Shamir **k=6**, write quorum **m=6**) are set via `JAVA_TOOL_OPTIONS` in `app-statefulset.yaml`.

## Prerequisites

- `kubectl` on the machine you deploy from, with a valid kubeconfig for the cluster
- Built image tagged **`dsv-backend:latest`** on **every worker** that can run `dsv-app` (see [Image distribution](#image-distribution))
- Default **StorageClass** for dynamic PVCs (k3s: `local-path` is typical)
- Traefik ingress controller (bundled with k3s)

## 1. Build the application image (build machine)

```bash
cd DistributedSecretsVault
./mvnw clean package -DskipTests
mkdir -p target/dependency && (cd target/dependency && jar -xf ../*.jar)
docker build -t dsv-backend:latest .
docker save dsv-backend:latest | gzip -9 > dsv-backend.tar.gz
```

## 2. Copy manifests to the remote machine (scp)

From your laptop (or CI), upload the production YAML directory:

```bash
scp -r k8s/production user@REMOTE:/tmp/dsv-k8s/
```

Optional: upload the image tarball to a jump host or each worker:

```bash
scp dsv-backend.tar.gz user@REMOTE:/tmp/
```

You can apply with `kubectl` from any host that has cluster access; the YAML does not need to live on a control-plane node.

## 3. Load the image on all workers

DSV pods only schedule on **workers**. Import the image on **each of the 10 workers** (not required on control-plane nodes unless they run workloads).

On each worker (after `scp` of the tarball):

```bash
gunzip -c /tmp/dsv-backend.tar.gz | sudo k3s ctr images import -
sudo k3s ctr images ls | grep dsv-backend
```

Loop from your workstation (adjust hosts and SSH user):

```bash
for host in worker1 worker2 worker3 worker4 worker5 worker6 worker7 worker8 worker9 worker10; do
  scp dsv-backend.tar.gz user@${host}:/tmp/
  ssh user@${host} 'gunzip -c /tmp/dsv-backend.tar.gz | sudo k3s ctr images import -'
done
```

**Alternative:** push to a private registry, set `image:` in `app-statefulset.yaml`, and add `imagePullSecrets` if needed.

## 4. Validate manifests (before apply)

On the remote machine with `kubectl`:

```bash
kubectl apply -f /tmp/dsv-k8s/ --dry-run=client
kubectl diff -f /tmp/dsv-k8s/   # optional; shows changes if upgrading
```

Check cluster capacity:

```bash
kubectl get nodes -l '!node-role.kubernetes.io/control-plane,!node-role.kubernetes.io/master'
# Expect 10 Ready workers

kubectl get storageclass
# Expect a default StorageClass for PVCs
```

## 5. Deploy (order matters for first install)

```bash
kubectl apply -f /tmp/dsv-k8s/namespace.yaml
kubectl apply -f /tmp/dsv-k8s/kafka-service.yaml
kubectl apply -f /tmp/dsv-k8s/kafka-statefulset.yaml
kubectl wait -n dsv --for=condition=ready pod/kafka-0 --timeout=300s

kubectl apply -f /tmp/dsv-k8s/app-service.yaml
kubectl apply -f /tmp/dsv-k8s/app-statefulset.yaml
kubectl apply -f /tmp/dsv-k8s/ingress.yaml
```

Or apply everything at once (Kafka may restart apps until it is ready):

```bash
kubectl apply -f /tmp/dsv-k8s/
kubectl get pods -n dsv -w
```

Expect:

- `kafka-0` → Running on a worker
- `dsv-app-0` … `dsv-app-9` → Running (each: `dsv-app` + `redis-sidecar`)

## 6. Verify

```bash
kubectl get pods -n dsv -o wide
kubectl get pvc -n dsv
kubectl get ingress -n dsv dsv-ingress
```

Port-forward (if ingress is not exposed yet):

```bash
kubectl port-forward -n dsv svc/dsv-app-service 9080:9080
curl -s http://127.0.0.1:9080/actuator/health | jq .
curl -s http://127.0.0.1:9080/api/v1/cluster/status | jq .
curl -s http://127.0.0.1:9080/api/v1/cluster/nodes | jq .
```

`cluster/status` should report **10** healthy nodes once ScaleCube has formed the cluster.

Via Traefik (k3s default):

```bash
curl -s http://<any-worker-or-lb-ip>/actuator/health
```

## Headlamp

1. Open the cluster in Headlamp and select the **`dsv`** namespace.
2. **Workloads** → StatefulSets: confirm `dsv-app` (10/10) and `kafka` (1/1).
3. **Storage** → PVCs: all Bound.
4. **Network** → Services / Ingress: `dsv-app-service`, `dsv-ingress`.
5. Use **Pod logs** (`dsv-app` container) if a pod is not Ready.
6. **Port forward** `dsv-app-service` port **9080** for API tests without DNS.

## Tuning

| Setting | Location | Notes |
|---------|----------|--------|
| Replica count | `app-statefulset.yaml` `replicas` | Match worker count for one pod per node |
| Shamir / quorum | `JAVA_TOOL_OPTIONS` | For `N` nodes: `k = N/2+1`, `m = k` (see demo script) |
| Ingress host/TLS | `ingress.yaml` | Add `host` and `tls` for production DNS |
| Image registry | `app-statefulset.yaml` `image` | Replace `dsv-backend:latest` |
| Storage size | PVC templates | Redis 5Gi, Kafka 10Gi defaults |

## Troubleshooting

| Symptom | Cause | Fix |
|---------|--------|-----|
| `ErrImagePull` / `ImagePullBackOff` | Image missing on that worker | Import `dsv-backend.tar.gz` on that node |
| `dsv-app-*` Pending | Anti-affinity or no workers | Need 10 schedulable workers; check `kubectl describe pod` |
| PVC Pending | No StorageClass | Install/configure provisioner (e.g. `local-path`) |
| 503 on writes | Cluster not fully up | Wait for 10 pods; check `/api/v1/cluster/nodes` |
| Ingress 404 | Wrong class | `kubectl get ingressclass`; set `ingressClassName: traefik` |

## Teardown

```bash
kubectl delete -f /tmp/dsv-k8s/
# PVCs are retained by default; delete manually if you need a clean slate:
# kubectl delete pvc -l app=dsv-app
# kubectl delete pvc -l app=kafka
```
