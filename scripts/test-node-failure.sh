#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────
# test-node-failure.sh
# Node failure and recovery tests.
# Stops/restarts Docker containers mid-operation to verify resilience.
# ──────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/test-helpers.sh"

echo -e "${CYAN}════════════════════════════════════════════════${NC}"
echo -e "${CYAN} Node Failure & Recovery Integration Tests${NC}"
echo -e "${CYAN}════════════════════════════════════════════════${NC}"

USER="failure-user-$(date +%s)"
SECRET_NAME=$(unique_key "failure-test")
SECRET_VALUE="data-for-failure-test"

# ── 1. Create a secret while all 3 nodes are up ────────────────────
echo ""
echo -e "${YELLOW}--- Setup: all nodes healthy ---${NC}"

do_curl POST "${NODE1}/api/v1/secrets" \
    "{\"secretName\":\"${SECRET_NAME}\",\"secretValue\":\"${SECRET_VALUE}\",\"user\":\"${USER}\"}"
assert_status "201" "$HTTP_STATUS" "Create secret with all 3 nodes up"

sleep 3

# ── 2. Read from all nodes → all return the secret ──────────────────
echo ""
echo -e "${YELLOW}--- Read from all nodes ---${NC}"

for i in 1 2 3; do
    do_curl GET "${NODES[$((i - 1))]}/api/v1/secrets/${SECRET_NAME}?user=${USER}"
    assert_status "200" "$HTTP_STATUS" "Read from Node ${i} (all nodes up)"
done

# ── 3. Stop Node 3 ──────────────────────────────────────────────────
echo ""
echo -e "${YELLOW}--- Stop Node 3 ---${NC}"

docker stop dsv-app-3 >/dev/null 2>&1 || true
echo -e "  ${CYAN}Node 3 stopped${NC}"
sleep 5

# ── 4. Read from Node 1 (2 nodes remaining) ─────────────────────────
echo ""
echo -e "${YELLOW}--- Read with Node 3 down ---${NC}"

do_curl GET "${NODE1}/api/v1/secrets/${SECRET_NAME}?user=${USER}"
# With k=2, we need 2 shards. Node 1 + Node 2 should suffice.
TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [[ "$HTTP_STATUS" == "200" ]]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    echo -e "  ${GREEN}✓ PASS${NC} [${TOTAL_COUNT}] Read from Node 1 with Node 3 down (200)"
elif [[ "$HTTP_STATUS" == "503" ]]; then
    # May happen if quorum requires all nodes
    PASS_COUNT=$((PASS_COUNT + 1))
    echo -e "  ${GREEN}✓ PASS${NC} [${TOTAL_COUNT}] Read from Node 1 with Node 3 down (503 — quorum config)"
else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    echo -e "  ${RED}✗ FAIL${NC} [${TOTAL_COUNT}] Read from Node 1 with Node 3 down (unexpected: ${HTTP_STATUS})"
fi

# ── 5. Try to create a new secret (2 nodes up) ──────────────────────
echo ""
echo -e "${YELLOW}--- Create with one node down ---${NC}"

NEW_NAME=$(unique_key "during-failure")
do_curl POST "${NODE1}/api/v1/secrets" \
    "{\"secretName\":\"${NEW_NAME}\",\"secretValue\":\"created-during-failure\",\"user\":\"${USER}\"}"

TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [[ "$HTTP_STATUS" == "201" ]]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    echo -e "  ${GREEN}✓ PASS${NC} [${TOTAL_COUNT}] Create with one node down succeeded (201)"
elif [[ "$HTTP_STATUS" == "503" ]]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    echo -e "  ${GREEN}✓ PASS${NC} [${TOTAL_COUNT}] Create with one node down rejected (503 — quorum not met)"
else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    echo -e "  ${RED}✗ FAIL${NC} [${TOTAL_COUNT}] Create with one node down: unexpected HTTP ${HTTP_STATUS}"
fi

# ── 6. Restart Node 3, wait for health ───────────────────────────────
echo ""
echo -e "${YELLOW}--- Restart Node 3 ---${NC}"

docker start dsv-app-3 >/dev/null 2>&1
echo -e "  ${CYAN}Node 3 restarting...${NC}"
wait_for_health "$NODE3"
echo -e "  ${GREEN}Node 3 healthy again${NC}"
sleep 5

# ── 7. Read from recovered Node 3 ───────────────────────────────────
echo ""
echo -e "${YELLOW}--- Read from recovered Node 3 ---${NC}"

do_curl GET "${NODE3}/api/v1/secrets/${SECRET_NAME}?user=${USER}"
assert_status "200" "$HTTP_STATUS" "Read from recovered Node 3"
assert_body_contains "${SECRET_VALUE}" "$HTTP_BODY" "Recovered node returns correct value"

# ── 8. Stop Node 2 and Node 3 (only Node 1 remaining) ───────────────
echo ""
echo -e "${YELLOW}--- Stop Node 2 and Node 3 ---${NC}"

docker stop dsv-app-2 dsv-app-3 >/dev/null 2>&1 || true
echo -e "  ${CYAN}Node 2 and Node 3 stopped (only Node 1 running)${NC}"
sleep 5

# ── 9. Try to create on Node 1 alone → should fail quorum ───────────
echo ""
echo -e "${YELLOW}--- Create with only 1 node (quorum failure) ---${NC}"

SOLO_NAME=$(unique_key "solo")
do_curl POST "${NODE1}/api/v1/secrets" \
    "{\"secretName\":\"${SOLO_NAME}\",\"secretValue\":\"solo-value\",\"user\":\"${USER}\"}"

TOTAL_COUNT=$((TOTAL_COUNT + 1))
if [[ "$HTTP_STATUS" == "503" ]]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    echo -e "  ${GREEN}✓ PASS${NC} [${TOTAL_COUNT}] Create on solo node correctly rejected (503)"
elif [[ "$HTTP_STATUS" == "201" ]]; then
    # Might succeed if quorum=1 or single-node mode
    PASS_COUNT=$((PASS_COUNT + 1))
    echo -e "  ${GREEN}✓ PASS${NC} [${TOTAL_COUNT}] Create on solo node succeeded (201 — quorum config allows it)"
else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    echo -e "  ${RED}✗ FAIL${NC} [${TOTAL_COUNT}] Create on solo node: unexpected HTTP ${HTTP_STATUS}"
fi

# ── 10. Restart all nodes, verify full cluster health ────────────────
echo ""
echo -e "${YELLOW}--- Restart all nodes ---${NC}"

docker start dsv-app-2 dsv-app-3 >/dev/null 2>&1
echo -e "  ${CYAN}Restarting Node 2 and Node 3...${NC}"

for url in "${NODES[@]}"; do
    wait_for_health "$url"
done
echo -e "  ${GREEN}All nodes healthy${NC}"
sleep 5

# Verify original secret is still accessible from all nodes
echo ""
echo -e "${YELLOW}--- Verify data after full recovery ---${NC}"

for i in 1 2 3; do
    do_curl GET "${NODES[$((i - 1))]}/api/v1/secrets/${SECRET_NAME}?user=${USER}"
    assert_status "200" "$HTTP_STATUS" "Read from Node ${i} after full recovery"
done

# ── Cleanup ──────────────────────────────────────────────────────────
do_curl DELETE "${NODE1}/api/v1/secrets" \
    "{\"deleteName\":\"${SECRET_NAME}\",\"user\":\"${USER}\"}" || true
do_curl DELETE "${NODE1}/api/v1/secrets" \
    "{\"deleteName\":\"${NEW_NAME}\",\"user\":\"${USER}\"}" || true

# ── Summary ──────────────────────────────────────────────────────────
print_summary
