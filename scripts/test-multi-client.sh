#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────
# test-multi-client.sh
# Multi-user isolation and concurrent client request tests.
# Verifies that secrets from different users don't leak across tenants.
# ──────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/test-helpers.sh"

echo -e "${CYAN}════════════════════════════════════════════════${NC}"
echo -e "${CYAN} Multi-Client Integration Tests${NC}"
echo -e "${CYAN}════════════════════════════════════════════════${NC}"

SECRET_NAME=$(unique_key "shared-name")
USER_A="alice-$(date +%s)"
USER_B="bob-$(date +%s)"
VALUE_A="alice-secret-value"
VALUE_B="bob-secret-value"

# ── 1. User A creates a secret on Node 1 ────────────────────────────
echo ""
echo -e "${YELLOW}--- User A creates ---${NC}"

do_curl POST "${NODE1}/api/v1/secrets" \
    "{\"secretName\":\"${SECRET_NAME}\",\"secretValue\":\"${VALUE_A}\",\"user\":\"${USER_A}\"}"
assert_status "201" "$HTTP_STATUS" "User A creates secret on Node 1"

sleep 3

# ── 2. User B creates secret with same name on Node 2 ───────────────
echo ""
echo -e "${YELLOW}--- User B creates (same name, different owner) ---${NC}"

do_curl POST "${NODE2}/api/v1/secrets" \
    "{\"secretName\":\"${SECRET_NAME}\",\"secretValue\":\"${VALUE_B}\",\"user\":\"${USER_B}\"}"
assert_status "201" "$HTTP_STATUS" "User B creates secret with same name on Node 2"

sleep 3

# ── 3. User A reads from Node 2 → sees own value ────────────────────
echo ""
echo -e "${YELLOW}--- Read isolation ---${NC}"

do_curl GET "${NODE2}/api/v1/secrets/${SECRET_NAME}?user=${USER_A}"
assert_status "200" "$HTTP_STATUS" "User A reads from Node 2"
assert_body_contains "${VALUE_A}" "$HTTP_BODY" "User A sees own value"
assert_body_not_contains "${VALUE_B}" "$HTTP_BODY" "User A does NOT see Bob's value"

# ── 4. User B reads from Node 3 → sees own value ────────────────────
do_curl GET "${NODE3}/api/v1/secrets/${SECRET_NAME}?user=${USER_B}"
assert_status "200" "$HTTP_STATUS" "User B reads from Node 3"
assert_body_contains "${VALUE_B}" "$HTTP_BODY" "User B sees own value"
assert_body_not_contains "${VALUE_A}" "$HTTP_BODY" "User B does NOT see Alice's value"

# ── 5. User A tries to read with wrong user → 404 ───────────────────
echo ""
echo -e "${YELLOW}--- Cross-tenant isolation ---${NC}"

FAKE_USER="eve-$(date +%s)"
do_curl GET "${NODE1}/api/v1/secrets/${SECRET_NAME}?user=${FAKE_USER}"
assert_status "404" "$HTTP_STATUS" "Unknown user cannot read existing secret"

# ── 6. User A updates, User B deletes — no interference ─────────────
echo ""
echo -e "${YELLOW}--- Independent operations ---${NC}"

# User A updates
do_curl PUT "${NODE1}/api/v1/secrets" \
    "{\"secretCurrentName\":\"${SECRET_NAME}\",\"secretUpdatedValue\":\"alice-updated\",\"user\":\"${USER_A}\"}"
assert_status "200" "$HTTP_STATUS" "User A updates own secret"

sleep 3

# User B deletes
do_curl DELETE "${NODE2}/api/v1/secrets" \
    "{\"deleteName\":\"${SECRET_NAME}\",\"user\":\"${USER_B}\"}"
assert_status "204" "$HTTP_STATUS" "User B deletes own secret"

sleep 3

# User A's secret should still exist with updated value
do_curl GET "${NODE3}/api/v1/secrets/${SECRET_NAME}?user=${USER_A}"
assert_status "200" "$HTTP_STATUS" "User A's secret still exists after User B deletes"
assert_body_contains "alice-updated" "$HTTP_BODY" "User A has updated value"

# User B's secret should be gone
do_curl GET "${NODE1}/api/v1/secrets/${SECRET_NAME}?user=${USER_B}"
assert_status "404" "$HTTP_STATUS" "User B's secret is gone"

# ── 7. Concurrent creates: 5 users, different keys, in parallel ─────
echo ""
echo -e "${YELLOW}--- Concurrent parallel creates (5 users) ---${NC}"

PARALLEL_RESULTS_DIR=$(mktemp -d "${ROOT}/target/parallel-XXXXXX")
for i in $(seq 1 5); do
    (
        usr="parallel-user-${i}-$(date +%s)"
        name=$(unique_key "parallel-${i}")
        resp=$(curl -sS -w "\n%{http_code}" \
            --connect-timeout 5 --max-time 30 \
            -X POST \
            -H "Content-Type: application/json" \
            -d "{\"secretName\":\"${name}\",\"secretValue\":\"val-${i}\",\"user\":\"${usr}\"}" \
            "${NODES[$((i % 3))]}/api/v1/secrets" 2>/dev/null) || true
        status=$(echo "$resp" | tail -1)
        echo "$status" > "${PARALLEL_RESULTS_DIR}/result-${i}"
    ) &
done
wait

parallel_pass=0
parallel_fail=0
for i in $(seq 1 5); do
    result=$(cat "${PARALLEL_RESULTS_DIR}/result-${i}" 2>/dev/null || echo "000")
    if [[ "$result" == "201" ]]; then
        parallel_pass=$((parallel_pass + 1))
    else
        parallel_fail=$((parallel_fail + 1))
    fi
done
rm -rf "$PARALLEL_RESULTS_DIR"

TOTAL_COUNT=$((TOTAL_COUNT + 1))
if (( parallel_pass == 5 )); then
    PASS_COUNT=$((PASS_COUNT + 1))
    echo -e "  ${GREEN}✓ PASS${NC} [${TOTAL_COUNT}] All 5 parallel creates succeeded (${parallel_pass}/5)"
else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    echo -e "  ${RED}✗ FAIL${NC} [${TOTAL_COUNT}] Parallel creates: ${parallel_pass}/5 succeeded, ${parallel_fail}/5 failed"
fi

# ── 8. Two users create same name concurrently → both succeed ────────
echo ""
echo -e "${YELLOW}--- Concurrent same-name creates (different owners) ---${NC}"

SAME_NAME=$(unique_key "concurrent-same")
U1="concurrent-alice-$(date +%s)"
U2="concurrent-bob-$(date +%s)"

STATUS1=""
STATUS2=""

(
    resp=$(curl -sS -w "\n%{http_code}" \
        --connect-timeout 5 --max-time 30 \
        -X POST \
        -H "Content-Type: application/json" \
        -d "{\"secretName\":\"${SAME_NAME}\",\"secretValue\":\"alice-val\",\"user\":\"${U1}\"}" \
        "${NODE1}/api/v1/secrets" 2>/dev/null) || true
    echo "$resp" | tail -1 > "/tmp/dsv-concurrent-1"
) &

(
    resp=$(curl -sS -w "\n%{http_code}" \
        --connect-timeout 5 --max-time 30 \
        -X POST \
        -H "Content-Type: application/json" \
        -d "{\"secretName\":\"${SAME_NAME}\",\"secretValue\":\"bob-val\",\"user\":\"${U2}\"}" \
        "${NODE2}/api/v1/secrets" 2>/dev/null) || true
    echo "$resp" | tail -1 > "/tmp/dsv-concurrent-2"
) &

wait

STATUS1=$(cat /tmp/dsv-concurrent-1 2>/dev/null || echo "000")
STATUS2=$(cat /tmp/dsv-concurrent-2 2>/dev/null || echo "000")
rm -f /tmp/dsv-concurrent-1 /tmp/dsv-concurrent-2

TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [[ "$STATUS1" == "201" && "$STATUS2" == "201" ]]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    echo -e "  ${GREEN}✓ PASS${NC} [${TOTAL_COUNT}] Both users created same-name secret (201, 201)"
else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    echo -e "  ${RED}✗ FAIL${NC} [${TOTAL_COUNT}] Concurrent same-name creates: User1=${STATUS1}, User2=${STATUS2}"
fi

# ── Cleanup ──────────────────────────────────────────────────────────
do_curl DELETE "${NODE1}/api/v1/secrets" \
    "{\"deleteName\":\"${SECRET_NAME}\",\"user\":\"${USER_A}\"}" || true

# ── Summary ──────────────────────────────────────────────────────────
print_summary
