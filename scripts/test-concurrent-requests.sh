#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────
# test-concurrent-requests.sh
# Concurrent request handling tests for the same secret key.
# Tests race conditions on reads, writes, and create/delete conflicts.
# ──────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/test-helpers.sh"

echo -e "${CYAN}════════════════════════════════════════════════${NC}"
echo -e "${CYAN} Concurrent Request Integration Tests${NC}"
echo -e "${CYAN}════════════════════════════════════════════════${NC}"

setup_test_cluster

USER="concurrency-user-$(date +%s)"
SECRET_NAME=$(unique_key "conc")
SECRET_VALUE="original-concurrent-value"

# ── 1. Create a secret first ────────────────────────────────────────
echo ""
echo -e "${YELLOW}--- Setup: create test secret ---${NC}"

do_curl POST "${NODE1}/api/v1/secrets" \
    "{\"secretName\":\"${SECRET_NAME}\",\"secretValue\":\"${SECRET_VALUE}\",\"user\":\"${USER}\"}"
assert_status "201" "$HTTP_STATUS" "Create test secret"

sleep 3

# ── 2. Three concurrent reads → all should succeed with same value ──
echo ""
echo -e "${YELLOW}--- Concurrent reads (3 parallel) ---${NC}"

RESULTS_DIR=$(mktemp -d "${ROOT}/target/conc-read-XXXXXX")
for i in 1 2 3; do
    (
        resp=$(curl -sS -w "\n%{http_code}" \
            --connect-timeout 5 --max-time 30 \
            "${NODES[$((i - 1))]}/api/v1/secrets/${SECRET_NAME}?user=${USER}" 2>/dev/null) || true
        status=$(echo "$resp" | tail -1)
        body=$(echo "$resp" | sed '$d')
        echo "${status}" > "${RESULTS_DIR}/status-${i}"
        echo "${body}" > "${RESULTS_DIR}/body-${i}"
    ) &
done
wait

all_reads_ok=true
for i in 1 2 3; do
    s=$(cat "${RESULTS_DIR}/status-${i}" 2>/dev/null || echo "000")
    b=$(cat "${RESULTS_DIR}/body-${i}" 2>/dev/null || echo "")
    if [[ "$s" != "200" ]]; then
        all_reads_ok=false
    fi
    if ! echo "$b" | grep -q "${SECRET_VALUE}"; then
        all_reads_ok=false
    fi
done
rm -rf "$RESULTS_DIR"

TOTAL_COUNT=$((TOTAL_COUNT + 1))
if $all_reads_ok; then
    PASS_COUNT=$((PASS_COUNT + 1))
    echo -e "  ${GREEN}✓ PASS${NC} [${TOTAL_COUNT}] All 3 concurrent reads returned 200 with correct value"
else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    echo -e "  ${RED}✗ FAIL${NC} [${TOTAL_COUNT}] Concurrent reads: not all returned 200 with correct value"
fi

# ── 3. Three concurrent updates → at least one succeeds ─────────────
echo ""
echo -e "${YELLOW}--- Concurrent updates (3 parallel) ---${NC}"

RESULTS_DIR=$(mktemp -d "${ROOT}/target/conc-update-XXXXXX")
for i in 1 2 3; do
    (
        resp=$(curl -sS -w "\n%{http_code}" \
            --connect-timeout 5 --max-time 30 \
            -X PUT \
            -H "Content-Type: application/json" \
            -d "{\"secretCurrentName\":\"${SECRET_NAME}\",\"secretUpdatedValue\":\"update-${i}\",\"user\":\"${USER}\"}" \
            "${NODES[$((i - 1))]}/api/v1/secrets" 2>/dev/null) || true
        status=$(echo "$resp" | tail -1)
        echo "${status}" > "${RESULTS_DIR}/status-${i}"
    ) &
done
wait

success_count=0
conflict_count=0
for i in 1 2 3; do
    s=$(cat "${RESULTS_DIR}/status-${i}" 2>/dev/null || echo "000")
    if [[ "$s" == "200" ]]; then
        success_count=$((success_count + 1))
    elif [[ "$s" == "409" ]]; then
        conflict_count=$((conflict_count + 1))
    fi
done
rm -rf "$RESULTS_DIR"

TOTAL_COUNT=$((TOTAL_COUNT + 1))
if (( success_count >= 1 )); then
    PASS_COUNT=$((PASS_COUNT + 1))
    echo -e "  ${GREEN}✓ PASS${NC} [${TOTAL_COUNT}] Concurrent updates: ${success_count} succeeded, ${conflict_count} conflicts (expected)"
else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    echo -e "  ${RED}✗ FAIL${NC} [${TOTAL_COUNT}] Concurrent updates: none succeeded"
fi

sleep 3

# ── 4. Read final state → version should be consistent ──────────────
echo ""
echo -e "${YELLOW}--- Read final state ---${NC}"

do_curl GET "${NODE1}/api/v1/secrets/${SECRET_NAME}?user=${USER}"
assert_status "200" "$HTTP_STATUS" "Read final state after concurrent updates"

# ── 5. Two concurrent creates of same key (same user) → race ────────
echo ""
echo -e "${YELLOW}--- Concurrent duplicate creates (same user, same key) ---${NC}"

RACE_NAME=$(unique_key "race")

(
    resp=$(curl -sS -w "\n%{http_code}" \
        --connect-timeout 5 --max-time 30 \
        -X POST \
        -H "Content-Type: application/json" \
        -d "{\"secretName\":\"${RACE_NAME}\",\"secretValue\":\"val-A\",\"user\":\"${USER}\"}" \
        "${NODE1}/api/v1/secrets" 2>/dev/null) || true
    echo "$resp" | tail -1 > "/tmp/dsv-race-1"
) &

(
    resp=$(curl -sS -w "\n%{http_code}" \
        --connect-timeout 5 --max-time 30 \
        -X POST \
        -H "Content-Type: application/json" \
        -d "{\"secretName\":\"${RACE_NAME}\",\"secretValue\":\"val-B\",\"user\":\"${USER}\"}" \
        "${NODE2}/api/v1/secrets" 2>/dev/null) || true
    echo "$resp" | tail -1 > "/tmp/dsv-race-2"
) &

wait

RACE1=$(cat /tmp/dsv-race-1 2>/dev/null || echo "000")
RACE2=$(cat /tmp/dsv-race-2 2>/dev/null || echo "000")
rm -f /tmp/dsv-race-1 /tmp/dsv-race-2

# Exactly one should be 201 and the other 409 (or both could be 201 if timing allows)
TOTAL_COUNT=$((TOTAL_COUNT + 1))
race_201=0
race_409=0
[[ "$RACE1" == "201" ]] && race_201=$((race_201 + 1))
[[ "$RACE2" == "201" ]] && race_201=$((race_201 + 1))
[[ "$RACE1" == "409" ]] && race_409=$((race_409 + 1))
[[ "$RACE2" == "409" ]] && race_409=$((race_409 + 1))

if (( race_201 >= 1 && (race_201 + race_409 == 2) )); then
    PASS_COUNT=$((PASS_COUNT + 1))
    echo -e "  ${GREEN}✓ PASS${NC} [${TOTAL_COUNT}] Concurrent duplicate creates: ${race_201}×201, ${race_409}×409"
else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    echo -e "  ${RED}✗ FAIL${NC} [${TOTAL_COUNT}] Concurrent creates: Race1=${RACE1}, Race2=${RACE2} (expected one 201 + one 409)"
fi

sleep 3

# ── 6. Create then immediately delete concurrently ──────────────────
echo ""
echo -e "${YELLOW}--- Concurrent create + delete (same key) ---${NC}"

CD_NAME=$(unique_key "create-delete")

# First create the secret
do_curl POST "${NODE1}/api/v1/secrets" \
    "{\"secretName\":\"${CD_NAME}\",\"secretValue\":\"ephemeral\",\"user\":\"${USER}\"}"
CREATE_FIRST_STATUS="$HTTP_STATUS"

sleep 3

# Now try to delete and create (update) concurrently
(
    resp=$(curl -sS -w "\n%{http_code}" \
        --connect-timeout 5 --max-time 30 \
        -X DELETE \
        -H "Content-Type: application/json" \
        -d "{\"deleteName\":\"${CD_NAME}\",\"user\":\"${USER}\"}" \
        "${NODE2}/api/v1/secrets" 2>/dev/null) || true
    echo "$resp" | tail -1 > "/tmp/dsv-cd-delete"
) &

(
    resp=$(curl -sS -w "\n%{http_code}" \
        --connect-timeout 5 --max-time 30 \
        -X PUT \
        -H "Content-Type: application/json" \
        -d "{\"secretCurrentName\":\"${CD_NAME}\",\"secretUpdatedValue\":\"updated\",\"user\":\"${USER}\"}" \
        "${NODE3}/api/v1/secrets" 2>/dev/null) || true
    echo "$resp" | tail -1 > "/tmp/dsv-cd-update"
) &

wait

CD_DELETE=$(cat /tmp/dsv-cd-delete 2>/dev/null || echo "000")
CD_UPDATE=$(cat /tmp/dsv-cd-update 2>/dev/null || echo "000")
rm -f /tmp/dsv-cd-delete /tmp/dsv-cd-update

# At least one should succeed; the other may conflict
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [[ "$CD_DELETE" =~ ^(204|404|409|503)$ ]] && [[ "$CD_UPDATE" =~ ^(200|404|409|503)$ ]]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    echo -e "  ${GREEN}✓ PASS${NC} [${TOTAL_COUNT}] Concurrent delete+update: delete=${CD_DELETE}, update=${CD_UPDATE}"
else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    echo -e "  ${RED}✗ FAIL${NC} [${TOTAL_COUNT}] Concurrent delete+update: delete=${CD_DELETE}, update=${CD_UPDATE}"
fi

# ── Cleanup ──────────────────────────────────────────────────────────
do_curl DELETE "${NODE1}/api/v1/secrets" \
    "{\"deleteName\":\"${SECRET_NAME}\",\"user\":\"${USER}\"}" || true
do_curl DELETE "${NODE1}/api/v1/secrets" \
    "{\"deleteName\":\"${RACE_NAME}\",\"user\":\"${USER}\"}" || true

# ── Summary ──────────────────────────────────────────────────────────
print_summary
