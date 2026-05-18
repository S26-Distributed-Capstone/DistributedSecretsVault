#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────
# test-crud-lifecycle.sh
# Full CRUD lifecycle test against a real 3-node DSV cluster.
# Tests: create → read → update → read versions → delete → recreate
# ──────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/test-helpers.sh"

echo -e "${CYAN}════════════════════════════════════════════════${NC}"
echo -e "${CYAN} CRUD Lifecycle Integration Tests${NC}"
echo -e "${CYAN}════════════════════════════════════════════════${NC}"

setup_test_cluster

# Generate unique key names to avoid collisions
SECRET_NAME=$(unique_key "crud-test")
USER="crud-test-user"
SECRET_VALUE="my-super-secret-password-123"
UPDATED_VALUE="my-updated-password-456"

# ── 1. Create a secret on Node 1 ────────────────────────────────────
echo ""
echo -e "${YELLOW}--- Create ---${NC}"

do_curl POST "${NODE1}/api/v1/secrets" \
    "{\"secretName\":\"${SECRET_NAME}\",\"secretValue\":\"${SECRET_VALUE}\",\"user\":\"${USER}\"}"
assert_status "201" "$HTTP_STATUS" "Create secret on Node 1"

# Brief settle time for Kafka commit propagation
sleep 3

# ── 2. Read secret from Node 2 ──────────────────────────────────────
echo ""
echo -e "${YELLOW}--- Read from other nodes ---${NC}"

do_curl GET "${NODE2}/api/v1/secrets/${SECRET_NAME}?user=${USER}"
assert_status "200" "$HTTP_STATUS" "Read secret from Node 2"
assert_body_contains "${SECRET_VALUE}" "$HTTP_BODY" "Node 2 returns correct value"

# ── 3. Read secret from Node 3 ──────────────────────────────────────
do_curl GET "${NODE3}/api/v1/secrets/${SECRET_NAME}?user=${USER}"
assert_status "200" "$HTTP_STATUS" "Read secret from Node 3"
assert_body_contains "${SECRET_VALUE}" "$HTTP_BODY" "Node 3 returns correct value"

# ── 4. Update secret on Node 2 ──────────────────────────────────────
echo ""
echo -e "${YELLOW}--- Update ---${NC}"

do_curl PUT "${NODE2}/api/v1/secrets" \
    "{\"secretCurrentName\":\"${SECRET_NAME}\",\"secretUpdatedValue\":\"${UPDATED_VALUE}\",\"user\":\"${USER}\"}"
assert_status "200" "$HTTP_STATUS" "Update secret on Node 2"

sleep 3

# ── 5. Read latest from Node 1 (should be updated value) ────────────
echo ""
echo -e "${YELLOW}--- Read after update ---${NC}"

do_curl GET "${NODE1}/api/v1/secrets/${SECRET_NAME}?user=${USER}"
assert_status "200" "$HTTP_STATUS" "Read latest from Node 1"
assert_body_contains "${UPDATED_VALUE}" "$HTTP_BODY" "Node 1 returns updated value"

# ── 6. Read version 1 from Node 3 ───────────────────────────────────
do_curl GET "${NODE3}/api/v1/secrets/${SECRET_NAME}?user=${USER}&version=1"
assert_status "200" "$HTTP_STATUS" "Read version 1 from Node 3"
assert_body_contains "${SECRET_VALUE}" "$HTTP_BODY" "Version 1 still has original value"

# ── 7. Read all versions from Node 1 ────────────────────────────────
echo ""
echo -e "${YELLOW}--- All versions ---${NC}"

do_curl GET "${NODE1}/api/v1/secrets/${SECRET_NAME}/all?user=${USER}"
assert_status "200" "$HTTP_STATUS" "Read all versions from Node 1"
assert_body_contains "${SECRET_VALUE}" "$HTTP_BODY" "All versions contains original"
assert_body_contains "${UPDATED_VALUE}" "$HTTP_BODY" "All versions contains updated"

# ── 8. Delete secret from Node 3 ────────────────────────────────────
echo ""
echo -e "${YELLOW}--- Delete ---${NC}"

do_curl DELETE "${NODE3}/api/v1/secrets" \
    "{\"deleteName\":\"${SECRET_NAME}\",\"user\":\"${USER}\"}"
assert_status "204" "$HTTP_STATUS" "Delete secret from Node 3"

sleep 3

# ── 9. Read after delete from Node 1 → 404 ──────────────────────────
echo ""
echo -e "${YELLOW}--- Verify deletion ---${NC}"

do_curl GET "${NODE1}/api/v1/secrets/${SECRET_NAME}?user=${USER}"
assert_status "404" "$HTTP_STATUS" "Read after delete returns 404"

# ── 10. Create again after delete → fresh version ───────────────────
echo ""
echo -e "${YELLOW}--- Recreate after delete ---${NC}"

do_curl POST "${NODE1}/api/v1/secrets" \
    "{\"secretName\":\"${SECRET_NAME}\",\"secretValue\":\"recreated-secret\",\"user\":\"${USER}\"}"
assert_status "201" "$HTTP_STATUS" "Recreate secret after delete"

sleep 3

do_curl GET "${NODE2}/api/v1/secrets/${SECRET_NAME}?user=${USER}"
assert_status "200" "$HTTP_STATUS" "Read recreated secret from Node 2"
assert_body_contains "recreated-secret" "$HTTP_BODY" "Recreated value is correct"

# ── 11. Delete non-existent secret → 404 ────────────────────────────
echo ""
echo -e "${YELLOW}--- Error cases ---${NC}"

FAKE_NAME=$(unique_key "nonexistent")
do_curl DELETE "${NODE2}/api/v1/secrets" \
    "{\"deleteName\":\"${FAKE_NAME}\",\"user\":\"${USER}\"}"
assert_status "404" "$HTTP_STATUS" "Delete non-existent secret returns 404"

# ── 12. Update non-existent secret → 404 ────────────────────────────
do_curl PUT "${NODE1}/api/v1/secrets" \
    "{\"secretCurrentName\":\"${FAKE_NAME}\",\"secretUpdatedValue\":\"x\",\"user\":\"${USER}\"}"
assert_status "404" "$HTTP_STATUS" "Update non-existent secret returns 404"

# ── Cleanup ──────────────────────────────────────────────────────────
do_curl DELETE "${NODE1}/api/v1/secrets" \
    "{\"deleteName\":\"${SECRET_NAME}\",\"user\":\"${USER}\"}" || true

# ── Summary ──────────────────────────────────────────────────────────
print_summary
