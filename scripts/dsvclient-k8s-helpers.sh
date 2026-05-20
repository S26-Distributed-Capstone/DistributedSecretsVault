#!/usr/bin/env bash
# Shared helpers for DSVClient-driven Kubernetes integration/load tests.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPOS_ROOT="$(cd "${ROOT}/.." && pwd)"

NAMESPACE="${NAMESPACE:-dsv}"
STATEFULSET="${STATEFULSET:-dsv-app}"
SERVICE="${SERVICE:-dsv-app-service}"
SERVICE_PORT="${SERVICE_PORT:-9080}"
LOCAL_PORT="${LOCAL_PORT:-19080}"
BASE_URL="${BASE_URL:-http://127.0.0.1:${LOCAL_PORT}}"
DSV_CLIENT_DIR="${DSV_CLIENT_DIR:-${REPOS_ROOT}/DSVClient}"
CLIENT_CLI="${CLIENT_CLI:-${DSV_CLIENT_DIR}/cli.py}"
PYTHON_BIN="${PYTHON_BIN:-python3}"
RUN_ID="${RUN_ID:-$(date +%Y%m%d%H%M%S)-${RANDOM}}"
WORK_DIR="${WORK_DIR:-${ROOT}/target/dsvclient-k8s-${RUN_ID}}"
PORT_FORWARD_PID=""

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

TOTAL_COUNT=0
PASS_COUNT=0
FAIL_COUNT=0

info() {
    echo -e "${CYAN}==>${NC} $*"
}

section() {
    echo ""
    echo -e "${YELLOW}--- $* ---${NC}"
}

pass() {
    TOTAL_COUNT=$((TOTAL_COUNT + 1))
    PASS_COUNT=$((PASS_COUNT + 1))
    echo -e "  ${GREEN}[PASS]${NC} [${TOTAL_COUNT}] $*"
}

fail() {
    TOTAL_COUNT=$((TOTAL_COUNT + 1))
    FAIL_COUNT=$((FAIL_COUNT + 1))
    echo -e "  ${RED}[FAIL]${NC} [${TOTAL_COUNT}] $*"
}

die() {
    echo -e "${RED}ERROR:${NC} $*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "Required command not found: $1"
}

setup_suite() {
    mkdir -p "$WORK_DIR"
    require_command kubectl
    require_command curl
    require_command "$PYTHON_BIN"
    [[ -f "$CLIENT_CLI" ]] || die "DSVClient CLI not found at ${CLIENT_CLI}. Set DSV_CLIENT_DIR or CLIENT_CLI."

    info "Using namespace=${NAMESPACE}, service=${SERVICE}, base_url=${BASE_URL}"
    kubectl get namespace "$NAMESPACE" >/dev/null
    kubectl -n "$NAMESPACE" rollout status "statefulset/${STATEFULSET}" --timeout=240s
    ensure_gateway
    wait_for_gateway
}

ensure_gateway() {
    if [[ -n "${NO_PORT_FORWARD:-}" || -n "${EXTERNAL_BASE_URL:-}" ]]; then
        BASE_URL="${EXTERNAL_BASE_URL:-${BASE_URL}}"
        return
    fi

    info "Starting kubectl port-forward svc/${SERVICE} ${LOCAL_PORT}:${SERVICE_PORT}"
    kubectl -n "$NAMESPACE" port-forward "svc/${SERVICE}" "${LOCAL_PORT}:${SERVICE_PORT}" \
        >"${WORK_DIR}/port-forward.log" 2>&1 &
    PORT_FORWARD_PID="$!"
    trap cleanup_suite EXIT
}

cleanup_suite() {
    local status=$?
    if [[ -n "$PORT_FORWARD_PID" ]]; then
        kill "$PORT_FORWARD_PID" >/dev/null 2>&1 || true
        wait "$PORT_FORWARD_PID" >/dev/null 2>&1 || true
    fi
    return "$status"
}

wait_for_gateway() {
    local timeout="${GATEWAY_TIMEOUT_SECONDS:-180}"
    local elapsed=0
    while ! curl -sf --connect-timeout 2 --max-time 10 "${BASE_URL}/actuator/health" >/dev/null 2>&1; do
        if (( elapsed >= timeout )); then
            [[ -f "${WORK_DIR}/port-forward.log" ]] && tail -40 "${WORK_DIR}/port-forward.log" || true
            die "Gateway ${BASE_URL} did not become healthy within ${timeout}s"
        fi
        sleep 3
        elapsed=$((elapsed + 3))
    done
}

wait_for_rollout() {
    kubectl -n "$NAMESPACE" rollout status "statefulset/${STATEFULSET}" --timeout="${ROLLOUT_TIMEOUT:-300s}"
    wait_for_gateway
}

client_home() {
    local username="$1"
    local home_dir="${WORK_DIR}/homes/${username}-$$-${RANDOM}"
    mkdir -p "${home_dir}/.dsv_client"
    cat > "${home_dir}/.dsv_client/config.json" <<JSON
{
  "base_url": "${BASE_URL}",
  "username": "${username}"
}
JSON
    printf "%s" "$home_dir"
}

dsvc() {
    local username="$1"
    shift
    local home_dir
    home_dir="$(client_home "$username")"
    HOME="$home_dir" "$PYTHON_BIN" "$CLIENT_CLI" "$@"
}

unique_name() {
    local prefix="${1:-secret}"
    printf "%s-%s-%s" "$prefix" "$RUN_ID" "$RANDOM"
}

expect_output_contains() {
    local output="$1"
    local expected="$2"
    local label="$3"
    if printf "%s" "$output" | grep -Fq -- "$expected"; then
        pass "$label"
    else
        fail "${label} (expected output to contain '${expected}')"
        printf "    Output: %s\n" "$(printf "%s" "$output" | tr '\n' ' ' | cut -c 1-240)"
    fi
}

write_status() {
    local file="$1"
    local status="$2"
    printf "%s" "$status" > "$file"
}

count_status() {
    local dir="$1"
    local status="$2"
    local count=0
    local file
    for file in "$dir"/status-*; do
        [[ -f "$file" ]] || continue
        [[ "$(cat "$file")" == "$status" ]] && count=$((count + 1))
    done
    printf "%s" "$count"
}

print_summary() {
    echo ""
    echo -e "${CYAN}Summary${NC}"
    echo "  total:  ${TOTAL_COUNT}"
    echo -e "  passed: ${GREEN}${PASS_COUNT}${NC}"
    if (( FAIL_COUNT > 0 )); then
        echo -e "  failed: ${RED}${FAIL_COUNT}${NC}"
        return 1
    fi
    echo "  failed: 0"
    return 0
}
