#!/usr/bin/env bash
# High concurrency against the same small key namespace across several users.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/dsvclient-k8s-helpers.sh"

USERS="${USERS:-5}"
KEYS="${KEYS:-8}"
REQUESTS_PER_USER="${REQUESTS_PER_USER:-80}"
PARALLELISM="${PARALLELISM:-60}"

setup_suite
section "Five users writing the same key names concurrently"

RESULTS_DIR="${WORK_DIR}/same-key-namespaces"
mkdir -p "$RESULTS_DIR"

# Pre-create the shared key names for each user, so the heavy phase can hammer updates/reads.
for u in $(seq 1 "$USERS"); do
    user="namespace-user-${u}-${RUN_ID}"
    for k in $(seq 1 "$KEYS"); do
        name="shared-key-${k}"
        dsvc "$user" create "$name" "initial-${u}-${k}" >"${RESULTS_DIR}/seed-${u}-${k}.log" 2>&1 || true
    done
done

op_id=0
for u in $(seq 1 "$USERS"); do
    user="namespace-user-${u}-${RUN_ID}"
    for r in $(seq 1 "$REQUESTS_PER_USER"); do
        op_id=$((op_id + 1))
        key_index=$((((r - 1) % KEYS) + 1))
        name="shared-key-${key_index}"
        (
            case $((r % 4)) in
                0) out="$(dsvc "$user" get "$name" 2>&1)" ;;
                1) out="$(dsvc "$user" update "$name" "user-${u}-write-${r}" 2>&1)" ;;
                2) out="$(dsvc "$user" get "$name" --all 2>&1)" ;;
                3) out="$(dsvc "$user" get "$name" --version 1 2>&1)" ;;
            esac
            printf "%s\n" "$out" > "${RESULTS_DIR}/op-${op_id}.log"
            if printf "%s" "$out" | grep -Eq "Secret updated|initial-|user-${u}-write-|\\{|\\["; then
                write_status "${RESULTS_DIR}/status-${op_id}" "PASS"
            else
                write_status "${RESULTS_DIR}/status-${op_id}" "CHECK"
            fi
        ) &
        if (( op_id % PARALLELISM == 0 )); then
            wait
        fi
    done
done
wait

passed="$(count_status "$RESULTS_DIR" PASS)"
check="$(count_status "$RESULTS_DIR" CHECK)"
if (( check == 0 )); then
    pass "Same-key namespace load completed: users=${USERS}, keys=${KEYS}, operations=${passed}"
else
    fail "Same-key namespace load needs review: pass=${passed}, check=${check}. Logs: ${RESULTS_DIR}"
fi

print_summary

