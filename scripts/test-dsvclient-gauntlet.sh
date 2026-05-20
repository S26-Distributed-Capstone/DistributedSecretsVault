#!/usr/bin/env bash
# Full DSVClient Kubernetes gauntlet: simple load, mixed load, namespace contention, and pod failure.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

export NAMESPACE="${NAMESPACE:-dsv}"
export RUN_ID="${RUN_ID:-gauntlet-$(date +%Y%m%d%H%M%S)-${RANDOM}}"
export WORK_DIR="${WORK_DIR:-$(cd "${SCRIPT_DIR}/.." && pwd)/target/dsvclient-k8s-${RUN_ID}}"
export LOCAL_PORT="${LOCAL_PORT:-19080}"

echo "DSVClient Kubernetes gauntlet"
echo "  namespace: ${NAMESPACE}"
echo "  run id:    ${RUN_ID}"
echo "  work dir:  ${WORK_DIR}"

# Run each phase with an external base URL after one shared port-forward is established.
source "${SCRIPT_DIR}/dsvclient-k8s-helpers.sh"
setup_suite
export EXTERNAL_BASE_URL="${BASE_URL}"
export NO_PORT_FORWARD=1

REQUESTS="${GAUNTLET_HIGH_REQUESTS:-250}" \
PARALLELISM="${GAUNTLET_HIGH_PARALLELISM:-50}" \
bash "${SCRIPT_DIR}/test-dsvclient-high-concurrency.sh"

SEED_COUNT="${GAUNTLET_MIXED_SEED_COUNT:-80}" \
ROUNDS="${GAUNTLET_MIXED_ROUNDS:-10}" \
PARALLELISM="${GAUNTLET_MIXED_PARALLELISM:-100}" \
bash "${SCRIPT_DIR}/test-dsvclient-mixed-concurrency.sh"

USERS="${GAUNTLET_NAMESPACE_USERS:-5}" \
KEYS="${GAUNTLET_NAMESPACE_KEYS:-12}" \
REQUESTS_PER_USER="${GAUNTLET_NAMESPACE_REQUESTS_PER_USER:-120}" \
PARALLELISM="${GAUNTLET_NAMESPACE_PARALLELISM:-100}" \
bash "${SCRIPT_DIR}/test-dsvclient-same-key-namespaces.sh"

LOAD_REQUESTS="${GAUNTLET_FAILURE_REQUESTS:-180}" \
PARALLELISM="${GAUNTLET_FAILURE_PARALLELISM:-45}" \
bash "${SCRIPT_DIR}/test-dsvclient-failure.sh"

echo ""
echo "Gauntlet completed. Logs are under ${WORK_DIR}"
