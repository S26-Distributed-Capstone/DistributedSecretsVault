#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────
# Shared utility functions for DSV integration test scripts.
# Source this file from other test scripts:  source "$(dirname "$0")/test-helpers.sh"
# ──────────────────────────────────────────────────────────────────────
set -euo pipefail

# ── Paths ────────────────────────────────────────────────────────────
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT}/docker/dsv/docker-compose.dsv-redis-kafka-3nodes.yml"

# ── Test runner options ─────────────────────────────────────────────
# AUTO_START_CLUSTER=1 starts a fresh Docker stack for each test script.
# Set AUTO_START_CLUSTER=0 to run a script against an already-running stack.
AUTO_START_CLUSTER="${AUTO_START_CLUSTER:-1}"
KEEP_STACK="${KEEP_STACK:-0}"
SKIP_BUILD="${SKIP_BUILD:-0}"

# ── Node URLs ────────────────────────────────────────────────────────
NODE1="${NODE1:-http://127.0.0.1:8081}"
NODE2="${NODE2:-http://127.0.0.1:8082}"
NODE3="${NODE3:-http://127.0.0.1:8083}"
NODES=("$NODE1" "$NODE2" "$NODE3")

# ── Colors ───────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# ── Counters ─────────────────────────────────────────────────────────
PASS_COUNT=0
FAIL_COUNT=0
TOTAL_COUNT=0

# ── Functions ────────────────────────────────────────────────────────

# Container names in docker/dsv/docker-compose.dsv-redis-kafka-3nodes.yml.
app_container() {
    local index="$1"
    printf "dsv-app-%d" "$index"
}

redis_container() {
    local index="$1"
    printf "dsv-redis-%d" "$index"
}

# Start the 3-node cluster (build + docker compose up)
start_cluster() {
    mkdir -p "$ROOT/target"

    if [[ "$SKIP_BUILD" == "1" ]]; then
        echo -e "${CYAN}==> Skipping Maven build because SKIP_BUILD=1${NC}"
    else
        echo -e "${CYAN}==> Building layered JAR layout for Docker${NC}"
        (cd "$ROOT" && ./mvnw -q clean package -DskipTests)
        mkdir -p "$ROOT/target/dependency"
        (cd "$ROOT/target/dependency" && jar -xf ../*.jar)
    fi

    echo -e "${CYAN}==> Starting 3-node cluster${NC}"
    docker compose -f "$COMPOSE_FILE" down -v --remove-orphans >/dev/null 2>&1 || true
    docker compose -f "$COMPOSE_FILE" up -d --build

    echo -e "${CYAN}==> Waiting for all nodes to become healthy (up to 180s)${NC}"
    for url in "${NODES[@]}"; do
        wait_for_health "$url"
    done

    # Brief settle time for Kafka consumer group coordination
    sleep 8
    echo -e "${GREEN}==> All nodes healthy${NC}"
}

setup_test_cluster() {
    mkdir -p "$ROOT/target"

    if [[ "$AUTO_START_CLUSTER" == "0" ]]; then
        echo -e "${CYAN}==> Using existing 3-node cluster because AUTO_START_CLUSTER=0${NC}"
        for url in "${NODES[@]}"; do
            wait_for_health "$url"
        done
        return
    fi

    trap cleanup_test_cluster EXIT
    start_cluster
}

cleanup_test_cluster() {
    local status=$?

    if [[ "$AUTO_START_CLUSTER" == "0" ]]; then
        return "$status"
    fi

    if [[ "$KEEP_STACK" == "1" ]]; then
        echo -e "${CYAN}==> KEEP_STACK=1, leaving cluster running${NC}"
        return "$status"
    fi

    stop_cluster
    return "$status"
}

# Stop the cluster
stop_cluster() {
    echo -e "${CYAN}==> Stopping cluster${NC}"
    docker compose -f "$COMPOSE_FILE" down -v --remove-orphans 2>/dev/null || true
}

# Wait for a node's actuator health endpoint to respond
wait_for_health() {
    local url="$1"
    local timeout=180
    local elapsed=0
    while ! curl -sf --connect-timeout 2 --max-time 10 "${url}/actuator/health" >/dev/null 2>&1; do
        if (( elapsed >= timeout )); then
            echo -e "${RED}ERROR: ${url} did not become healthy in ${timeout}s${NC}" >&2
            return 1
        fi
        sleep 5
        elapsed=$((elapsed + 5))
    done
}

wait_for_container_health() {
    local container="$1"
    local timeout=180
    local elapsed=0
    local status

    while true; do
        status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
            "$container" 2>/dev/null || true)"
        if [[ "$status" == "healthy" || "$status" == "running" ]]; then
            return
        fi
        if (( elapsed >= timeout )); then
            echo -e "${RED}ERROR: ${container} did not become healthy in ${timeout}s${NC}" >&2
            return 1
        fi
        sleep 3
        elapsed=$((elapsed + 3))
    done
}

stop_node() {
    local index="$1"
    local app
    local redis
    app="$(app_container "$index")"
    redis="$(redis_container "$index")"
    docker stop "$app" "$redis" >/dev/null 2>&1 || true
}

start_node() {
    local index="$1"
    local app
    local redis
    app="$(app_container "$index")"
    redis="$(redis_container "$index")"

    docker start "$redis" >/dev/null 2>&1
    wait_for_container_health "$redis"
    docker start "$app" >/dev/null 2>&1
    wait_for_health "${NODES[$((index - 1))]}"
}

stop_nodes() {
    local index
    for index in "$@"; do
        stop_node "$index"
    done
}

start_nodes() {
    local index
    for index in "$@"; do
        docker start "$(redis_container "$index")" >/dev/null 2>&1
    done
    for index in "$@"; do
        wait_for_container_health "$(redis_container "$index")"
    done
    for index in "$@"; do
        docker start "$(app_container "$index")" >/dev/null 2>&1
    done
    for index in "$@"; do
        wait_for_health "${NODES[$((index - 1))]}"
    done
}

# Assert HTTP status code
# Usage: assert_status <expected_code> <actual_code> <test_name>
assert_status() {
    local expected="$1"
    local actual="$2"
    local test_name="$3"
    TOTAL_COUNT=$((TOTAL_COUNT + 1))
    if [[ "$actual" == "$expected" ]]; then
        PASS_COUNT=$((PASS_COUNT + 1))
        echo -e "  ${GREEN}✓ PASS${NC} [${TOTAL_COUNT}] ${test_name} (HTTP ${actual})"
    else
        FAIL_COUNT=$((FAIL_COUNT + 1))
        echo -e "  ${RED}✗ FAIL${NC} [${TOTAL_COUNT}] ${test_name} (expected HTTP ${expected}, got ${actual})"
    fi
}

# Assert response body contains a substring
# Usage: assert_body_contains <expected_substring> <body> <test_name>
assert_body_contains() {
    local expected="$1"
    local body="$2"
    local test_name="$3"
    TOTAL_COUNT=$((TOTAL_COUNT + 1))
    if echo "$body" | grep -q "$expected"; then
        PASS_COUNT=$((PASS_COUNT + 1))
        echo -e "  ${GREEN}✓ PASS${NC} [${TOTAL_COUNT}] ${test_name}"
    else
        FAIL_COUNT=$((FAIL_COUNT + 1))
        echo -e "  ${RED}✗ FAIL${NC} [${TOTAL_COUNT}] ${test_name} (expected body to contain '${expected}')"
        echo "    Actual body: ${body:0:200}"
    fi
}

# Assert response body does NOT contain a substring
# Usage: assert_body_not_contains <unexpected_substring> <body> <test_name>
assert_body_not_contains() {
    local unexpected="$1"
    local body="$2"
    local test_name="$3"
    TOTAL_COUNT=$((TOTAL_COUNT + 1))
    if echo "$body" | grep -q "$unexpected"; then
        FAIL_COUNT=$((FAIL_COUNT + 1))
        echo -e "  ${RED}✗ FAIL${NC} [${TOTAL_COUNT}] ${test_name} (body should NOT contain '${unexpected}')"
    else
        PASS_COUNT=$((PASS_COUNT + 1))
        echo -e "  ${GREEN}✓ PASS${NC} [${TOTAL_COUNT}] ${test_name}"
    fi
}

# Print final test summary
print_summary() {
    echo ""
    echo -e "${CYAN}════════════════════════════════════════════════${NC}"
    echo -e "${CYAN} Test Summary${NC}"
    echo -e "${CYAN}════════════════════════════════════════════════${NC}"
    echo -e "  Total:  ${TOTAL_COUNT}"
    echo -e "  ${GREEN}Passed: ${PASS_COUNT}${NC}"
    if (( FAIL_COUNT > 0 )); then
        echo -e "  ${RED}Failed: ${FAIL_COUNT}${NC}"
    else
        echo -e "  Failed: 0"
    fi
    echo -e "${CYAN}════════════════════════════════════════════════${NC}"
    if (( FAIL_COUNT > 0 )); then
        echo -e "${RED}SOME TESTS FAILED${NC}"
        return 1
    else
        echo -e "${GREEN}ALL TESTS PASSED${NC}"
        return 0
    fi
}

# Perform a curl request and capture status + body
# Usage: do_curl <method> <url> [json_body]
# Sets: HTTP_STATUS, HTTP_BODY
do_curl() {
    local method="$1"
    local url="$2"
    local body="${3:-}"
    local response

    if [[ -n "$body" ]]; then
        response=$(curl -sS -w "\n%{http_code}" \
            --connect-timeout 5 --max-time 30 \
            -X "$method" \
            -H "Content-Type: application/json" \
            -d "$body" \
            "$url" 2>/dev/null) || true
    else
        response=$(curl -sS -w "\n%{http_code}" \
            --connect-timeout 5 --max-time 30 \
            -X "$method" \
            "$url" 2>/dev/null) || true
    fi

    HTTP_STATUS=$(echo "$response" | tail -1)
    HTTP_BODY=$(echo "$response" | sed '$d')
}

# Generate a unique test key name to avoid collisions between runs
unique_key() {
    local prefix="${1:-test}"
    echo "${prefix}-$(date +%s)-${RANDOM}"
}
