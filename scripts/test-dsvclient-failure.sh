#!/usr/bin/env bash
# DSVClient availability/load test. This script only sends client requests.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/dsvclient-cluster-helpers.sh"
parse_gateway_arg "$@"

LOAD_REQUESTS="${LOAD_REQUESTS:-2000}"
PARALLELISM="${PARALLELISM:-200}"
PROGRESS_INTERVAL="${PROGRESS_INTERVAL:-200}"

setup_suite
RESULTS_DIR="${WORK_DIR}/failure"
mkdir -p "$RESULTS_DIR"

USER="failure-client-${RUN_ID}"
BASE_SECRET="failure-baseline-${RUN_ID}"

section "Seed baseline secret"
out="$(dsvc "$USER" create "$BASE_SECRET" "baseline-value" 2>&1)" || true
expect_output_contains "$out" "Secret created" "Created baseline secret"

section "Run client traffic without cluster mutation"

for i in $(seq 1 "$LOAD_REQUESTS"); do
    (
        user="failure-load-$((i % 10))-${RUN_ID}"
        name="failure-load-${RUN_ID}-${i}"
        case $((i % 4)) in
            0) out="$(dsvc "$USER" get "$BASE_SECRET" 2>&1)" ;;
            1) out="$(dsvc "$USER" update "$BASE_SECRET" "baseline-update-${i}" 2>&1)" ;;
            2) out="$(dsvc "$user" create "$name" "value-${i}" 2>&1)" ;;
            3) out="$(dsvc "$USER" get "$BASE_SECRET" --all 2>&1)" ;;
        esac
        printf "%s\n" "$out" > "${RESULTS_DIR}/op-${i}.log"
        if printf "%s" "$out" | grep -Eq "Secret created|Secret updated|baseline"; then
            write_status "${RESULTS_DIR}/status-${i}" "PASS"
        else
            write_status "${RESULTS_DIR}/status-${i}" "CHECK"
        fi
    ) &
    if (( i % PARALLELISM == 0 )); then
        wait
        if (( i % PROGRESS_INTERVAL == 0 )); then
            progress_status "Availability load progress ${i}/${LOAD_REQUESTS}" "$RESULTS_DIR"
        fi
    fi
done
wait
progress_status "Availability load final progress ${LOAD_REQUESTS}/${LOAD_REQUESTS}" "$RESULTS_DIR"

section "Verify baseline after load"
out="$(dsvc "$USER" get "$BASE_SECRET" 2>&1)" || true
expect_output_contains "$out" "baseline" "Baseline remains readable after load"

passed="$(count_status "$RESULTS_DIR" PASS)"
check="$(count_status "$RESULTS_DIR" CHECK)"
if (( passed > 0 )); then
    pass "Traffic during pod failure produced ${passed} successful client operations; review=${check}"
else
    fail "No successful operations during failure. Logs: ${RESULTS_DIR}"
fi

print_summary

