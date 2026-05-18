#!/usr/bin/env bash
# Demo the Distributed Secrets Vault lifecycle across a local Docker cluster.
#
# What it shows:
#   - secret create, read, update, version reads, and delete
#   - multiple client identities using the same secret name without leakage
#   - one app node failing while quorum-capable operations continue
#   - a stopped node rebooting and serving data again
#   - quorum protection when too many app nodes are down
#
# Tunables:
#   NODE_COUNT=3..10        number of DSV app nodes to run, default 3
#   BASE_PORT=8081          host port for node 1; node N uses BASE_PORT+N-1
#   REDIS_BASE_PORT=6381    host port for redis1; redisN uses REDIS_BASE_PORT+N-1
#   QUORUM_M=<n>            required write ACKs, default majority
#   THRESHOLD_K=<n>         Shamir reconstruction threshold, default majority
#   KEEP_STACK=1            leave the Docker stack running after the demo
#   SKIP_BUILD=1            reuse target/dependency instead of rebuilding
#   PROJECT_NAME=dsv-demo   Docker Compose project/container prefix
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEMO_DIR="${ROOT}/target/dsv-demo"
COMPOSE_FILE="${DEMO_DIR}/docker-compose.demo.yml"

NODE_COUNT="${NODE_COUNT:-3}"
BASE_PORT="${BASE_PORT:-8081}"
PROJECT_NAME="${PROJECT_NAME:-dsv-demo}"
REDIS_PASSWORD="${REDIS_PASSWORD:-REDIS_PASSWORD}"
REDIS_BASE_PORT="${REDIS_BASE_PORT:-${REDIS_HOST_PORT:-6381}}"
KAFKA_HOST_PORT="${KAFKA_HOST_PORT:-19092}"
THRESHOLD_K="${THRESHOLD_K:-$((NODE_COUNT / 2 + 1))}"
DEFAULT_QUORUM_M="$((NODE_COUNT / 2 + 1))"
if (( DEFAULT_QUORUM_M < THRESHOLD_K )); then
    DEFAULT_QUORUM_M="$THRESHOLD_K"
fi
QUORUM_M="${QUORUM_M:-$DEFAULT_QUORUM_M}"
SPRING_PROFILE="${SPRING_PROFILES_ACTIVE:-dev}"
STARTUP_TIMEOUT_SECONDS="${STARTUP_TIMEOUT_SECONDS:-210}"
CLUSTER_TIMEOUT_SECONDS="${CLUSTER_TIMEOUT_SECONDS:-180}"
COMMIT_SETTLE_SECONDS="${COMMIT_SETTLE_SECONDS:-4}"
KEEP_STACK="${KEEP_STACK:-0}"
SKIP_BUILD="${SKIP_BUILD:-0}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

TOTAL_COUNT=0
PASS_COUNT=0
FAIL_COUNT=0
HTTP_STATUS=""
HTTP_BODY=""
LATEST_ALICE_VALUE=""

die() {
    echo -e "${RED}ERROR:${NC} $*" >&2
    exit 1
}

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

expect_status() {
    local expected="$1"
    local actual="$2"
    local label="$3"

    if [[ "$actual" == "$expected" ]]; then
        pass "${label} (HTTP ${actual})"
    else
        fail "${label} (expected HTTP ${expected}, got ${actual:-empty})"
        print_body_preview
    fi
}

expect_body_contains() {
    local expected="$1"
    local body="$2"
    local label="$3"

    if printf "%s" "$body" | grep -Fq -- "$expected"; then
        pass "$label"
    else
        fail "${label} (body did not contain '${expected}')"
        print_body_preview
    fi
}

expect_body_not_contains() {
    local unexpected="$1"
    local body="$2"
    local label="$3"

    if printf "%s" "$body" | grep -Fq -- "$unexpected"; then
        fail "${label} (body unexpectedly contained '${unexpected}')"
        print_body_preview
    else
        pass "$label"
    fi
}

print_body_preview() {
    local preview
    preview="$(printf "%s" "$HTTP_BODY" | tr '\n' ' ' | cut -c 1-240)"
    if [[ -n "$preview" ]]; then
        echo "    Body: ${preview}"
    fi
}

validate_config() {
    [[ "$NODE_COUNT" =~ ^[0-9]+$ ]] || die "NODE_COUNT must be numeric"
    [[ "$BASE_PORT" =~ ^[0-9]+$ ]] || die "BASE_PORT must be numeric"
    [[ "$REDIS_BASE_PORT" =~ ^[0-9]+$ ]] || die "REDIS_BASE_PORT must be numeric"
    [[ "$QUORUM_M" =~ ^[0-9]+$ ]] || die "QUORUM_M must be numeric"
    [[ "$THRESHOLD_K" =~ ^[0-9]+$ ]] || die "THRESHOLD_K must be numeric"

    if (( NODE_COUNT < 3 || NODE_COUNT > 10 )); then
        die "NODE_COUNT must be between 3 and 10 for this fault-tolerance demo"
    fi
    if (( QUORUM_M < 1 || QUORUM_M > NODE_COUNT )); then
        die "QUORUM_M must be between 1 and NODE_COUNT"
    fi
    if (( THRESHOLD_K < 1 || THRESHOLD_K > NODE_COUNT )); then
        die "THRESHOLD_K must be between 1 and NODE_COUNT"
    fi
    if (( QUORUM_M < THRESHOLD_K )); then
        die "QUORUM_M must be greater than or equal to THRESHOLD_K"
    fi
}

require_command() {
    local command_name="$1"
    command -v "$command_name" >/dev/null 2>&1 || die "Required command not found: ${command_name}"
}

require_docker_compose() {
    require_command docker
    docker compose version >/dev/null 2>&1 || die "Docker Compose v2 is required: 'docker compose' is not available"
}

docker_compose() {
    docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" "$@"
}

node_url() {
    local index="$1"
    printf "http://127.0.0.1:%d" "$((BASE_PORT + index - 1))"
}

node_container() {
    local index="$1"
    printf "%s-app-%d" "$PROJECT_NAME" "$index"
}

redis_container() {
    local index="$1"
    printf "%s-redis-%d" "$PROJECT_NAME" "$index"
}

write_compose_file() {
    mkdir -p "$DEMO_DIR"

    cat > "$COMPOSE_FILE" <<YAML
name: ${PROJECT_NAME}

services:
  kafka:
    image: apache/kafka:3.7.0
    container_name: ${PROJECT_NAME}-kafka
    ports:
      - "${KAFKA_HOST_PORT}:9092"
    environment:
      - KAFKA_NODE_ID=1
      - KAFKA_PROCESS_ROLES=broker,controller
      - KAFKA_LISTENERS=PLAINTEXT_HOST://0.0.0.0:9092,PLAINTEXT_INTERNAL://0.0.0.0:29092,CONTROLLER://0.0.0.0:9093
      - KAFKA_ADVERTISED_LISTENERS=PLAINTEXT_HOST://localhost:${KAFKA_HOST_PORT},PLAINTEXT_INTERNAL://kafka:29092
      - KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER
      - KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT_INTERNAL
      - KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT,PLAINTEXT_INTERNAL:PLAINTEXT
      - KAFKA_CONTROLLER_QUORUM_VOTERS=1@kafka:9093
      - KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1
      - KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1
      - KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1
      - KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0
      - KAFKA_NUM_PARTITIONS=1
    volumes:
      - kafka-data:/var/lib/kafka/data
    healthcheck:
      test: ["CMD-SHELL", "/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server kafka:29092 || exit 1"]
      interval: 10s
      timeout: 10s
      retries: 8
      start_period: 45s
    networks:
      - dsv-network
YAML

    for i in $(seq 1 "$NODE_COUNT"); do
        local host_port
        local redis_host_port
        host_port=$((BASE_PORT + i - 1))
        redis_host_port=$((REDIS_BASE_PORT + i - 1))
        cat >> "$COMPOSE_FILE" <<YAML

  redis${i}:
    image: redis:8.6-alpine
    container_name: ${PROJECT_NAME}-redis-${i}
    ports:
      - "${redis_host_port}:6379"
    environment:
      - REDIS_PASSWORD=${REDIS_PASSWORD}
    command: redis-server /usr/local/etc/redis/redis.conf --requirepass ${REDIS_PASSWORD}
    volumes:
      - redis-data-${i}:/data
      - "${ROOT}/docker/redis/redis.conf:/usr/local/etc/redis/redis.conf:ro"
    healthcheck:
      test: ["CMD-SHELL", "redis-cli -a \"\$\${REDIS_PASSWORD}\" ping"]
      interval: 10s
      timeout: 3s
      retries: 8
    networks:
      - dsv-network

  app${i}:
    build:
      context: "${ROOT}"
    container_name: ${PROJECT_NAME}-app-${i}
    ports:
      - "${host_port}:8080"
    environment:
      - NODE_NAME=node-${i}
      - POD_IP=app${i}
      - SERVER_PORT=8080
      - CLUSTER_PORT=4801
      - SEED_DNS_HOST=app1
      - SEED_DNS_PORT=4801
      - SPRING_DATA_REDIS_HOST=redis${i}
      - SPRING_DATA_REDIS_PORT=6379
      - SPRING_DATA_REDIS_PASSWORD=${REDIS_PASSWORD}
      - SPRING_PROFILES_ACTIVE=${SPRING_PROFILE}
      - KAFKA_BOOTSTRAP_SERVERS=kafka:29092
      - JAVA_TOOL_OPTIONS=-Dcluster.totalNodes=${NODE_COUNT} -Dcluster.thresholdK=${THRESHOLD_K} -Dcluster.quorumM=${QUORUM_M}
      - CLUSTER_TOTAL_NODES=${NODE_COUNT}
      - CLUSTER_TOTALNODES=${NODE_COUNT}
      - CLUSTER_THRESHOLD_K=${THRESHOLD_K}
      - CLUSTER_THRESHOLDK=${THRESHOLD_K}
      - CLUSTER_QUORUM_M=${QUORUM_M}
      - CLUSTER_QUORUMM=${QUORUM_M}
    depends_on:
      redis${i}:
        condition: service_healthy
      kafka:
        condition: service_healthy
    networks:
      - dsv-network
YAML
    done

    cat >> "$COMPOSE_FILE" <<YAML

volumes:
  kafka-data:
YAML

    for i in $(seq 1 "$NODE_COUNT"); do
        cat >> "$COMPOSE_FILE" <<YAML
  redis-data-${i}:
YAML
    done

    cat >> "$COMPOSE_FILE" <<YAML

networks:
  dsv-network:
    driver: bridge
YAML
}

build_app() {
    if [[ "$SKIP_BUILD" == "1" ]]; then
        info "Skipping Maven build because SKIP_BUILD=1"
        return
    fi

    info "Building Spring Boot jar layout for Docker"
    (cd "$ROOT" && ./mvnw -q clean package -DskipTests)
    mkdir -p "${ROOT}/target/dependency"
    (cd "${ROOT}/target/dependency" && jar -xf ../*.jar)
}

start_cluster() {
    info "Starting ${NODE_COUNT}-node demo stack from ${COMPOSE_FILE}"
    docker_compose down -v --remove-orphans >/dev/null 2>&1 || true
    docker_compose up -d --build

    section "Waiting for health"
    for i in $(seq 1 "$NODE_COUNT"); do
        wait_for_health "$(node_url "$i")" "node ${i}"
    done

    wait_for_cluster_membership "$QUORUM_M"
    sleep "$COMMIT_SETTLE_SECONDS"
    show_cluster_status
}

wait_for_health() {
    local url="$1"
    local label="$2"
    local elapsed=0

    while ! curl -sf --connect-timeout 2 --max-time 10 "${url}/actuator/health" >/dev/null 2>&1; do
        if (( elapsed >= STARTUP_TIMEOUT_SECONDS )); then
            docker_compose logs --tail=80 || true
            die "${label} at ${url} did not become healthy within ${STARTUP_TIMEOUT_SECONDS}s"
        fi
        sleep 5
        elapsed=$((elapsed + 5))
    done
    echo "  ${label} healthy at ${url}"
}

extract_total_nodes() {
    sed -n 's/.*"totalNodes":[[:space:]]*\([0-9][0-9]*\).*/\1/p'
}

wait_for_cluster_membership() {
    local minimum="$1"
    local elapsed=0

    info "Waiting for each node to see at least ${minimum} cluster endpoint(s)"
    while true; do
        local all_ready=true
        for i in $(seq 1 "$NODE_COUNT"); do
            local body
            local total
            body="$(curl -sf --connect-timeout 2 --max-time 10 "$(node_url "$i")/api/v1/cluster/status" 2>/dev/null || true)"
            total="$(printf "%s" "$body" | extract_total_nodes)"
            if [[ -z "$total" || "$total" -lt "$minimum" ]]; then
                all_ready=false
                break
            fi
        done

        if [[ "$all_ready" == "true" ]]; then
            return
        fi
        if (( elapsed >= CLUSTER_TIMEOUT_SECONDS )); then
            show_cluster_status
            die "Cluster membership did not reach the configured quorum within ${CLUSTER_TIMEOUT_SECONDS}s"
        fi
        sleep 5
        elapsed=$((elapsed + 5))
    done
}

show_cluster_status() {
    section "Cluster status"
    for i in $(seq 1 "$NODE_COUNT"); do
        local body
        body="$(curl -sf --connect-timeout 2 --max-time 10 "$(node_url "$i")/api/v1/cluster/status" 2>/dev/null || true)"
        echo "  node ${i}: ${body:-unavailable}"
    done
}

request() {
    local method="$1"
    local url="$2"
    local body="${3:-}"
    local response

    if [[ -n "$body" ]]; then
        response="$(curl -sS -w '\n%{http_code}' \
            --connect-timeout 5 \
            --max-time 45 \
            -X "$method" \
            -H "Content-Type: application/json" \
            -d "$body" \
            "$url" 2>/dev/null || true)"
    else
        response="$(curl -sS -w '\n%{http_code}' \
            --connect-timeout 5 \
            --max-time 45 \
            -X "$method" \
            "$url" 2>/dev/null || true)"
    fi

    HTTP_STATUS="$(printf "%s\n" "$response" | tail -n 1)"
    HTTP_BODY="$(printf "%s\n" "$response" | sed '$d')"
    echo "  ${method} ${url} -> HTTP ${HTTP_STATUS:-empty}"
}

settle_commits() {
    sleep "$COMMIT_SETTLE_SECONDS"
}

stop_node() {
    local index="$1"
    local app_container
    local redis_node_container
    app_container="$(node_container "$index")"
    redis_node_container="$(redis_container "$index")"
    info "Stopping ${app_container} and ${redis_node_container}"
    docker stop "$app_container" "$redis_node_container" >/dev/null
}

start_node() {
    local index="$1"
    local app_container
    local redis_node_container
    app_container="$(node_container "$index")"
    redis_node_container="$(redis_container "$index")"
    info "Starting ${redis_node_container}"
    docker start "$redis_node_container" >/dev/null
    wait_for_container_health "$redis_node_container"
    info "Starting ${app_container}"
    docker start "$app_container" >/dev/null
    wait_for_health "$(node_url "$index")" "node ${index}"
    sleep "$COMMIT_SETTLE_SECONDS"
}

wait_for_container_health() {
    local container="$1"
    local elapsed=0
    local status

    while true; do
        status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
            "$container" 2>/dev/null || true)"
        if [[ "$status" == "healthy" || "$status" == "running" ]]; then
            return
        fi
        if (( elapsed >= STARTUP_TIMEOUT_SECONDS )); then
            die "${container} did not become healthy within ${STARTUP_TIMEOUT_SECONDS}s"
        fi
        sleep 3
        elapsed=$((elapsed + 3))
    done
}

stop_nodes_except_one() {
    local app_containers=()
    local redis_containers=()
    local i
    for i in $(seq 2 "$NODE_COUNT"); do
        app_containers+=("$(node_container "$i")")
        redis_containers+=("$(redis_container "$i")")
    done
    info "Stopping app and Redis containers for nodes 2..${NODE_COUNT}; node 1 remains online"
    docker stop "${app_containers[@]}" >/dev/null 2>&1 || true
    docker stop "${redis_containers[@]}" >/dev/null 2>&1 || true
}

start_all_nodes() {
    local app_containers=()
    local redis_containers=()
    local i
    for i in $(seq 2 "$NODE_COUNT"); do
        redis_containers+=("$(redis_container "$i")")
        app_containers+=("$(node_container "$i")")
    done
    info "Starting Redis containers for nodes 2..${NODE_COUNT}"
    docker start "${redis_containers[@]}" >/dev/null
    for i in $(seq 2 "$NODE_COUNT"); do
        wait_for_container_health "$(redis_container "$i")"
    done
    info "Starting app containers for nodes 2..${NODE_COUNT}"
    docker start "${app_containers[@]}" >/dev/null
    for i in $(seq 1 "$NODE_COUNT"); do
        wait_for_health "$(node_url "$i")" "node ${i}"
    done
    wait_for_cluster_membership "$QUORUM_M"
    sleep "$COMMIT_SETTLE_SECONDS"
}

parallel_client_creates() {
    section "Parallel clients create independent secrets"

    local clients=5
    if (( NODE_COUNT < clients )); then
        clients="$NODE_COUNT"
    fi

    local results_dir
    results_dir="$(mktemp -d "${DEMO_DIR}/parallel-XXXXXX")"

    for i in $(seq 1 "$clients"); do
        (
            local node_index
            local user
            local name
            local value
            local url
            local response
            local status

            node_index=$((((i - 1) % NODE_COUNT) + 1))
            user="parallel-client-${i}-${RUN_ID}"
            name="parallel-secret-${i}-${RUN_ID}"
            value="parallel-value-${i}-${RUN_ID}"
            url="$(node_url "$node_index")/api/v1/secrets"
            response="$(curl -sS -w '\n%{http_code}' \
                --connect-timeout 5 \
                --max-time 45 \
                -X POST \
                -H "Content-Type: application/json" \
                -d "{\"secretName\":\"${name}\",\"secretValue\":\"${value}\",\"user\":\"${user}\"}" \
                "$url" 2>/dev/null || true)"
            status="$(printf "%s\n" "$response" | tail -n 1)"
            printf "%s" "$status" > "${results_dir}/status-${i}"
        ) &
    done
    wait

    local success=0
    local i
    for i in $(seq 1 "$clients"); do
        local status
        status="$(cat "${results_dir}/status-${i}" 2>/dev/null || printf "000")"
        if [[ "$status" == "201" ]]; then
            success=$((success + 1))
        fi
    done
    rm -rf "$results_dir"

    if (( success == clients )); then
        pass "All ${clients} parallel client creates succeeded"
    else
        fail "Parallel client creates succeeded ${success}/${clients}"
    fi
    settle_commits
}

run_demo() {
    RUN_ID="$(date +%Y%m%d%H%M%S)-${RANDOM}"
    local alice="alice-${RUN_ID}"
    local bob="bob-${RUN_ID}"
    local shared_secret="shared-login-${RUN_ID}"
    local alice_original="alice-original-${RUN_ID}"
    local alice_rotated="alice-rotated-${RUN_ID}"
    local alice_after_failure="alice-after-failure-${RUN_ID}"
    local bob_original="bob-original-${RUN_ID}"
    local bob_rotated="bob-rotated-${RUN_ID}"
    LATEST_ALICE_VALUE="$alice_original"

    echo -e "${CYAN}Distributed Secrets Vault demo${NC}"
    echo "  nodes:       ${NODE_COUNT}"
    echo "  node ports:  ${BASE_PORT}..$((BASE_PORT + NODE_COUNT - 1))"
    echo "  quorum m:    ${QUORUM_M}"
    echo "  threshold k: ${THRESHOLD_K}"
    echo "  run id:      ${RUN_ID}"

    section "Alice creates, reads, updates, and reads versions"
    request POST "$(node_url 1)/api/v1/secrets" \
        "{\"secretName\":\"${shared_secret}\",\"secretValue\":\"${alice_original}\",\"user\":\"${alice}\"}"
    expect_status "201" "$HTTP_STATUS" "Alice creates ${shared_secret} through node 1"
    settle_commits

    request GET "$(node_url 2)/api/v1/secrets/${shared_secret}?user=${alice}"
    expect_status "200" "$HTTP_STATUS" "Alice reads latest through node 2"
    expect_body_contains "$alice_original" "$HTTP_BODY" "Alice sees the original value"

    local update_node=3
    request PUT "$(node_url "$update_node")/api/v1/secrets" \
        "{\"secretCurrentName\":\"${shared_secret}\",\"secretUpdatedValue\":\"${alice_rotated}\",\"user\":\"${alice}\"}"
    expect_status "200" "$HTTP_STATUS" "Alice updates through node ${update_node}"
    if [[ "$HTTP_STATUS" == "200" ]]; then
        LATEST_ALICE_VALUE="$alice_rotated"
    fi
    settle_commits

    request GET "$(node_url 1)/api/v1/secrets/${shared_secret}?user=${alice}"
    expect_status "200" "$HTTP_STATUS" "Alice reads latest through node 1"
    expect_body_contains "$LATEST_ALICE_VALUE" "$HTTP_BODY" "Latest read returns the rotated value"

    request GET "$(node_url 2)/api/v1/secrets/${shared_secret}?user=${alice}&version=1"
    expect_status "200" "$HTTP_STATUS" "Alice reads version 1 through node 2"
    expect_body_contains "$alice_original" "$HTTP_BODY" "Version 1 still returns the original value"

    request GET "$(node_url 1)/api/v1/secrets/${shared_secret}/all?user=${alice}"
    expect_status "200" "$HTTP_STATUS" "Alice reads all versions"
    expect_body_contains "$alice_original" "$HTTP_BODY" "Version history contains original value"
    expect_body_contains "$LATEST_ALICE_VALUE" "$HTTP_BODY" "Version history contains latest value"

    section "Bob uses the same secret name without seeing Alice's value"
    request POST "$(node_url 2)/api/v1/secrets" \
        "{\"secretName\":\"${shared_secret}\",\"secretValue\":\"${bob_original}\",\"user\":\"${bob}\"}"
    expect_status "201" "$HTTP_STATUS" "Bob creates the same secret name through node 2"
    settle_commits

    request GET "$(node_url 3)/api/v1/secrets/${shared_secret}?user=${bob}"
    expect_status "200" "$HTTP_STATUS" "Bob reads through node 3"
    expect_body_contains "$bob_original" "$HTTP_BODY" "Bob sees Bob's value"
    expect_body_not_contains "$LATEST_ALICE_VALUE" "$HTTP_BODY" "Bob does not see Alice's value"

    request PUT "$(node_url 1)/api/v1/secrets" \
        "{\"secretCurrentName\":\"${shared_secret}\",\"secretUpdatedValue\":\"${bob_rotated}\",\"user\":\"${bob}\"}"
    expect_status "200" "$HTTP_STATUS" "Bob updates his own secret through node 1"
    settle_commits

    request DELETE "$(node_url 2)/api/v1/secrets" \
        "{\"deleteName\":\"${shared_secret}\",\"user\":\"${bob}\"}"
    expect_status "204" "$HTTP_STATUS" "Bob deletes his own secret through node 2"
    settle_commits

    request GET "$(node_url 3)/api/v1/secrets/${shared_secret}?user=${bob}"
    expect_status "404" "$HTTP_STATUS" "Bob's deleted secret is gone"

    request GET "$(node_url 1)/api/v1/secrets/${shared_secret}?user=${alice}"
    expect_status "200" "$HTTP_STATUS" "Alice's secret still exists after Bob deletes"
    expect_body_contains "$LATEST_ALICE_VALUE" "$HTTP_BODY" "Alice still sees her latest value"

    parallel_client_creates

    section "Failure: one node goes down and quorum operations continue"
    local failed_node="$NODE_COUNT"
    stop_node "$failed_node"
    sleep "$COMMIT_SETTLE_SECONDS"

    request GET "$(node_url 1)/api/v1/secrets/${shared_secret}?user=${alice}"
    expect_status "200" "$HTTP_STATUS" "Alice reads from node 1 while node ${failed_node} is down"
    expect_body_contains "$LATEST_ALICE_VALUE" "$HTTP_BODY" "Read during one-node failure returns current value"

    request PUT "$(node_url 1)/api/v1/secrets" \
        "{\"secretCurrentName\":\"${shared_secret}\",\"secretUpdatedValue\":\"${alice_after_failure}\",\"user\":\"${alice}\"}"
    if (( NODE_COUNT - 1 >= QUORUM_M )); then
        expect_status "200" "$HTTP_STATUS" "Alice updates while node ${failed_node} is down"
        if [[ "$HTTP_STATUS" == "200" ]]; then
            LATEST_ALICE_VALUE="$alice_after_failure"
        fi
    else
        expect_status "503" "$HTTP_STATUS" "Write is rejected without quorum"
    fi
    settle_commits

    section "Reboot: stopped node rejoins and serves the current value"
    start_node "$failed_node"
    request GET "$(node_url "$failed_node")/api/v1/secrets/${shared_secret}?user=${alice}"
    expect_status "200" "$HTTP_STATUS" "Recovered node ${failed_node} serves Alice's secret"
    expect_body_contains "$LATEST_ALICE_VALUE" "$HTTP_BODY" "Recovered node returns the current Alice value"

    section "Too many failures: writes are protected by quorum"
    stop_nodes_except_one
    sleep "$COMMIT_SETTLE_SECONDS"

    request GET "$(node_url 1)/api/v1/secrets/${shared_secret}?user=${alice}"
    if (( THRESHOLD_K == 1 )); then
        expect_status "200" "$HTTP_STATUS" "Existing secret remains readable from the surviving node"
        expect_body_contains "$LATEST_ALICE_VALUE" "$HTTP_BODY" "Surviving node returns the current value"
    else
        expect_status "503" "$HTTP_STATUS" "Read is rejected when fewer than K shards are online"
    fi

    request POST "$(node_url 1)/api/v1/secrets" \
        "{\"secretName\":\"quorum-check-${RUN_ID}\",\"secretValue\":\"should-not-commit-without-quorum\",\"user\":\"${alice}\"}"
    if (( QUORUM_M > 1 )); then
        expect_status "503" "$HTTP_STATUS" "New write is rejected when only one node is online"
    else
        expect_status "201" "$HTTP_STATUS" "New write succeeds because QUORUM_M=1"
    fi

    section "Full recovery and deletion"
    start_all_nodes
    request GET "$(node_url 2)/api/v1/secrets/${shared_secret}?user=${alice}"
    expect_status "200" "$HTTP_STATUS" "Alice reads after full recovery"
    expect_body_contains "$LATEST_ALICE_VALUE" "$HTTP_BODY" "Full recovery preserved Alice's value"

    request DELETE "$(node_url 3)/api/v1/secrets" \
        "{\"deleteName\":\"${shared_secret}\",\"user\":\"${alice}\"}"
    expect_status "204" "$HTTP_STATUS" "Alice deletes through node 3"
    settle_commits

    request GET "$(node_url 1)/api/v1/secrets/${shared_secret}?user=${alice}"
    expect_status "404" "$HTTP_STATUS" "Deleted Alice secret returns 404 from node 1"

    request GET "$(node_url 2)/api/v1/secrets/${shared_secret}?user=${alice}"
    expect_status "404" "$HTTP_STATUS" "Deleted Alice secret returns 404 from node 2"
}

print_summary() {
    echo ""
    echo -e "${CYAN}Demo summary${NC}"
    echo "  total:  ${TOTAL_COUNT}"
    echo -e "  passed: ${GREEN}${PASS_COUNT}${NC}"
    if (( FAIL_COUNT > 0 )); then
        echo -e "  failed: ${RED}${FAIL_COUNT}${NC}"
        return 1
    fi
    echo "  failed: 0"
    return 0
}

cleanup() {
    if [[ "$KEEP_STACK" == "1" ]]; then
        echo ""
        info "KEEP_STACK=1, leaving demo stack running"
        echo "  Compose file: ${COMPOSE_FILE}"
        echo "  Node 1 URL:   $(node_url 1)"
        return
    fi

    if [[ -f "$COMPOSE_FILE" ]]; then
        echo ""
        info "Stopping demo stack"
        docker_compose down -v --remove-orphans >/dev/null 2>&1 || true
    fi
}

main() {
    validate_config
    require_docker_compose
    require_command curl
    if [[ "$SKIP_BUILD" != "1" ]]; then
        require_command jar
    fi

    trap cleanup EXIT

    build_app
    write_compose_file
    start_cluster
    run_demo
    print_summary
}

main "$@"
