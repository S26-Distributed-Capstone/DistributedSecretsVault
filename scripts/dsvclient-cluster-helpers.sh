#!/usr/bin/env bash
# Shared helpers for DSVClient-driven cluster integration/load tests.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPOS_ROOT="$(cd "${ROOT}/.." && pwd)"

DEFAULT_GATEWAY_URL="http://192.168.8.11"
BASE_URL="$DEFAULT_GATEWAY_URL"
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

parse_gateway_arg() {
    if (($# == 0)); then
        export BASE_URL
        return
    fi
    if (($# == 1)); then
        BASE_URL="${1%/}"
        export BASE_URL
        return
    fi
    if (($# == 2)) && [[ "$1" == "--url" || "$1" == "-u" ]]; then
        BASE_URL="${2%/}"
        export BASE_URL
        return
    fi
    die "Usage: $(basename "$0") [gateway-url] or $(basename "$0") --url <gateway-url>"
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "Required command not found: $1"
}

setup_suite() {
    mkdir -p "$WORK_DIR"
    require_command curl
    require_command "$PYTHON_BIN"
    [[ -f "$CLIENT_CLI" ]] || die "DSVClient CLI not found at ${CLIENT_CLI}. Set DSV_CLIENT_DIR or CLIENT_CLI."
    grep -F -- "--all" "$CLIENT_CLI" >/dev/null 2>&1 \
        || die "DSVClient CLI at ${CLIENT_CLI} does not support 'get <name> --all' / '--version'. Update DSVClient or set DSV_CLIENT_DIR to the newer checkout."

    info "Using gateway ${BASE_URL}"
    wait_for_gateway
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
            die "Gateway ${BASE_URL} did not become healthy within ${timeout}s"
        fi
        sleep 3
        elapsed=$((elapsed + 3))
    done
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
    HOME="$home_dir" USERPROFILE="$home_dir" "$PYTHON_BIN" "$CLIENT_CLI" "$@"
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

progress_status() {
    local label="$1"
    local dir="$2"
    local pattern="${3:-status-*}"
    local completed=0
    local passed=0
    local failed=0
    local check=0
    local file
    local status

    for file in "$dir"/$pattern; do
        [[ -f "$file" ]] || continue
        completed=$((completed + 1))
        status="$(cat "$file")"
        case "$status" in
            PASS) passed=$((passed + 1)) ;;
            FAIL) failed=$((failed + 1)) ;;
            CHECK) check=$((check + 1)) ;;
        esac
    done
    info "${label}: completed=${completed}, pass=${passed}, fail=${failed}, check=${check}"
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
