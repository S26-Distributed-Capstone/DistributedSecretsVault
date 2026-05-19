#!/usr/bin/env bash
# Build the DSV backend image and push to Docker Hub.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

if [[ -f k8s/image.env ]]; then
  # shellcheck disable=SC1091
  source k8s/image.env
elif [[ -f k8s/image.env.example ]]; then
  # shellcheck disable=SC1091
  source k8s/image.env.example
fi

: "${DOCKERHUB_USERNAME:?Set DOCKERHUB_USERNAME in k8s/image.env (see k8s/image.env.example)}"
: "${DSV_IMAGE_NAME:=distributed-secrets-vault}"
: "${DSV_IMAGE_TAG:=latest}"
DOCKERHUB_IMAGE="${DOCKERHUB_IMAGE:-${DOCKERHUB_USERNAME}/${DSV_IMAGE_NAME}}"

echo "Building ${DOCKERHUB_IMAGE}:${DSV_IMAGE_TAG} ..."
./mvnw clean package -DskipTests
mkdir -p target/dependency
( cd target/dependency && jar -xf ../*.jar )

docker build -t "${DOCKERHUB_IMAGE}:${DSV_IMAGE_TAG}" .
docker tag "${DOCKERHUB_IMAGE}:${DSV_IMAGE_TAG}" "docker.io/${DOCKERHUB_IMAGE}:${DSV_IMAGE_TAG}"

echo "Pushing docker.io/${DOCKERHUB_IMAGE}:${DSV_IMAGE_TAG} ..."
docker push "docker.io/${DOCKERHUB_IMAGE}:${DSV_IMAGE_TAG}"

echo "Done. Deploy with: kubectl apply -k k8s/production/"
