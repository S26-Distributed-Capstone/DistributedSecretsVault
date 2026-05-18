#!/usr/bin/env bash
# Start three DSV instances (see docker/dsv/docker-compose.dsv-redis-kafka-3nodes.yml),
# trigger one commit from app1, and verify all three JVMs log CommitListener for that transaction.
#
# If curl fails with "Recv failure: Connection reset by peer" (56): the JVM may still be
# starting, or on WSL2 use 127.0.0.1 instead of localhost (IPv6 ::1 vs published port).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

COMPOSE_FILE="docker/dsv/docker-compose.dsv-redis-kafka-3nodes.yml"
APPS=(dsv-app-1 dsv-app-2 dsv-app-3)
# 127.0.0.1 avoids WSL/Docker Desktop localhost (::1) edge cases
PUBLISH_URL="${PUBLISH_URL:-http://127.0.0.1:8081}"

echo "==> Building layered JAR layout for Docker"
./mvnw -q clean package -DskipTests
mkdir -p target/dependency
( cd target/dependency && jar -xf ../*.jar )

echo "==> Starting stack (${COMPOSE_FILE})"
docker compose -f "${COMPOSE_FILE}" up -d --build

echo "==> Waiting for apps to listen (up to 180s)"
for _ in $(seq 1 36); do
  if curl -sf --connect-timeout 2 --max-time 10 "${PUBLISH_URL}/actuator/health" >/dev/null 2>&1; then
    break
  fi
  sleep 5
done

if ! curl -sf --connect-timeout 2 --max-time 10 "${PUBLISH_URL}/actuator/health" >/dev/null; then
  echo "ERROR: ${PUBLISH_URL} did not become healthy in time" >&2
  docker compose -f "${COMPOSE_FILE}" logs --tail=80 app1 app2 app3 || true
  exit 1
fi

# Brief settle time: Kafka consumer group coordinator can flap right after startup (NOT_COORDINATOR).
sleep 8

echo "==> Publishing commit via ${PUBLISH_URL}/api/temp-test/kafka"
RESP=""
for attempt in $(seq 1 8); do
  if RESP="$(curl -sS --connect-timeout 3 --max-time 60 "${PUBLISH_URL}/api/temp-test/kafka" 2>/dev/null)"; then
    if echo "${RESP}" | grep -q "Transaction ID:"; then
      break
    fi
  fi
  echo "    (attempt ${attempt}: curl failed or incomplete response; retrying in 3s)" >&2
  sleep 3
done
echo "${RESP}"

TX="$(echo "${RESP}" | sed -n 's/.*Transaction ID: \(.*\)/\1/p')"
if [[ -z "${TX}" ]]; then
  echo "ERROR: could not parse transaction id from response (try 127.0.0.1:8081 if you used localhost)" >&2
  exit 1
fi

echo "==> Checking all three containers for transaction ${TX}"
missing=()
for c in "${APPS[@]}"; do
  if docker logs "${c}" 2>&1 | grep -Fq "Received commit message for transaction ${TX}"; then
    echo "OK  ${c}"
  else
    echo "MISSING  ${c}"
    missing+=("${c}")
  fi
done

if ((${#missing[@]} > 0)); then
  echo "ERROR: commit not observed on: ${missing[*]}" >&2
  for c in "${missing[@]}"; do
    echo "--- last 40 lines of ${c} ---"
    docker logs "${c}" 2>&1 | tail -40
  done
  exit 1
fi

echo "==> All three DSV nodes received the commit message."
