# Docker Hub image for Kubernetes

Production and testing manifests pull the DSV backend from Docker Hub. Workers do not need a local `ctr import`.

## Image

Published at [noambensim/distributed-secrets-vault](https://hub.docker.com/r/noambensim/distributed-secrets-vault) on Docker Hub:

```text
docker.io/noambensim/distributed-secrets-vault:latest
```

Pull manually:

```bash
docker pull noambensim/distributed-secrets-vault:latest
```

To change the repository, edit:

- `k8s/image.env` (for build/push scripts)
- `k8s/production/kustomization.yaml` and `k8s/testing/kustomization.yaml` (`images.newName` / `newTag`)

## One-time Docker Hub setup

1. Repository: [noambensim/distributed-secrets-vault](https://hub.docker.com/r/noambensim/distributed-secrets-vault).
2. Create an access token: Docker Hub → Account Settings → Security → New Access Token.
3. For GitHub Actions, add repository secrets:
   - `DOCKERHUB_USERNAME`
   - `DOCKERHUB_TOKEN` (the access token, not your account password)

## Build and push (local)

```bash
cp k8s/image.env.example k8s/image.env
# Edit DOCKERHUB_USERNAME if needed

docker login
chmod +x scripts/docker-build-push.sh
./scripts/docker-build-push.sh
```

Or publish via GitHub Actions: **Actions → Publish Docker image → Run workflow**, or push a tag `v1.0.0`.

## Deploy to the cluster

```bash
kubectl apply -k k8s/production/ --dry-run=client
kubectl apply -k k8s/production/
kubectl get pods -n dsv -w
```

Each node pulls `docker.io/noambensim/distributed-secrets-vault:latest` when a pod starts (`imagePullPolicy: IfNotPresent`).

## Private repository

If the image is private, create a pull secret in the `dsv` namespace:

```bash
kubectl create secret docker-registry dockerhub-credentials \
  --docker-server=https://index.docker.io/v1/ \
  --docker-username=YOUR_USER \
  --docker-password=YOUR_TOKEN \
  -n dsv
```

Add to `k8s/production/app-statefulset.yaml` under `spec.template.spec`:

```yaml
imagePullSecrets:
  - name: dockerhub-credentials
```

See `k8s/production/dockerhub-pull-secret.yaml.example`.

## Pin a release

Set the same tag in `k8s/image.env` (`DSV_IMAGE_TAG`) and in both kustomization files (`images.newTag`), then push and redeploy:

```bash
kubectl apply -k k8s/production/
kubectl rollout restart statefulset/dsv-app -n dsv
```
