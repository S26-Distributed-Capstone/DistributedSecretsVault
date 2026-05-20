#!/usr/bin/env bash
# High-concurrency DSVClient test: many independent create/get/update/get-all/delete flows.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/dsvclient-k8s-helpers.sh"

REQUESTS="${REQUESTS:-200}"
PARALLELISM="${PARALLELISM:-40}"
USER_COUNT="${USER_COUNT:-25}"

setup_suite
section "High concurrency independent client flows"

RESULTS_DIR="${WORK_DIR}/high-concurrency"
mkdir -p "$RESULTS_DIR"

run_flow() {
    local i="$1"
    local user="hc-user-$((i % USER_COUNT))-${RUN_ID}"
    local name="hc-secret-${RUN_ID}-${i}"
    local value="hc-value-${i}"
    local updated="hc-updated-${i}"
    local out

    out="$(dsvc "$user" create "$name" "$value" 2>&1)" || true
    printf "%s\n" "$out" > "${RESULTS_DIR}/create-${i}.log"
    [[ "$out" == *"Secret created"* ]] || { write_status "${RESULTS_DIR}/status-${i}" "FAIL"; return; }

    out="$(dsvc "$user" get "$name" 2>&1)" || true
    printf "%s\n" "$out" > "${RESULTS_DIR}/get1-${i}.log"
    [[ "$out" == *"$value"* ]] || { write_status "${RESULTS_DIR}/status-${i}" "FAIL"; return; }

    out="$(dsvc "$user" update "$name" "$updated" 2>&1)" || true
    printf "%s\n" "$out" > "${RESULTS_DIR}/update-${i}.log"
    [[ "$out" == *"Secret updated"* ]] || { write_status "${RESULTS_DIR}/status-${i}" "FAIL"; return; }

    out="$(dsvc "$user" get "$name" --all 2>&1)" || true
    printf "%s\n" "$out" > "${RESULTS_DIR}/all-${i}.log"
    [[ "$out" == *"$value"* && "$out" == *"$updated"* ]] || { write_status "${RESULTS_DIR}/status-${i}" "FAIL"; return; }

    out="$(dsvc "$user" delete "$name" 2>&1)" || true
    printf "%s\n" "$out" > "${RESULTS_DIR}/delete-${i}.log"
    [[ "$out" == *"Delete succeeded"* ]] || { write_status "${RESULTS_DIR}/status-${i}" "FAIL"; return; }

    write_status "${RESULTS_DIR}/status-${i}" "PASS"
}

for i in $(seq 1 "$REQUESTS"); do
    run_flow "$i" &
    if (( i % PARALLELISM == 0 )); then
        wait
    fi
done
wait

passed="$(count_status "$RESULTS_DIR" PASS)"
failed="$(count_status "$RESULTS_DIR" FAIL)"
if [[ "$passed" == "$REQUESTS" ]]; then
    pass "All ${REQUESTS} concurrent client flows completed"
else
    fail "High concurrency flows completed ${passed}/${REQUESTS}; failed=${failed}. Logs: ${RESULTS_DIR}"
fi

print_summary

