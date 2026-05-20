#!/usr/bin/env bash
# Full DSVClient gauntlet: high load, mixed load, and namespace contention.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

export NAMESPACE="${NAMESPACE:-dsv}"
export RUN_ID="${RUN_ID:-gauntlet-$(date +%Y%m%d%H%M%S)-${RANDOM}}"
export WORK_DIR="${WORK_DIR:-$(cd "${SCRIPT_DIR}/.." && pwd)/target/dsvclient-k8s-${RUN_ID}}"

# Run each phase against the configured gateway. Defaults to the Traefik ingress.
source "${SCRIPT_DIR}/dsvclient-cluster-helpers.sh"
parse_gateway_arg "$@"

echo "DSVClient cluster gauntlet"
echo "  gateway:   ${BASE_URL}"
echo "  run id:    ${RUN_ID}"
echo "  work dir:  ${WORK_DIR}"

setup_suite

REQUESTS="${GAUNTLET_HIGH_REQUESTS:-2000}" \
PARALLELISM="${GAUNTLET_HIGH_PARALLELISM:-200}" \
PROGRESS_INTERVAL="${GAUNTLET_HIGH_PROGRESS_INTERVAL:-200}" \
bash "${SCRIPT_DIR}/test-dsvclient-high-concurrency.sh" "${BASE_URL}"

SEED_COUNT="${GAUNTLET_MIXED_SEED_COUNT:-250}" \
ROUNDS="${GAUNTLET_MIXED_ROUNDS:-12}" \
PARALLELISM="${GAUNTLET_MIXED_PARALLELISM:-200}" \
PROGRESS_INTERVAL="${GAUNTLET_MIXED_PROGRESS_INTERVAL:-200}" \
bash "${SCRIPT_DIR}/test-dsvclient-mixed-concurrency.sh" "${BASE_URL}"

USERS="${GAUNTLET_NAMESPACE_USERS:-10}" \
KEYS="${GAUNTLET_NAMESPACE_KEYS:-20}" \
REQUESTS_PER_USER="${GAUNTLET_NAMESPACE_REQUESTS_PER_USER:-500}" \
PARALLELISM="${GAUNTLET_NAMESPACE_PARALLELISM:-200}" \
PROGRESS_INTERVAL="${GAUNTLET_NAMESPACE_PROGRESS_INTERVAL:-200}" \
bash "${SCRIPT_DIR}/test-dsvclient-same-key-namespaces.sh" "${BASE_URL}"

echo ""
echo "Gauntlet completed. Logs are under ${WORK_DIR}"
