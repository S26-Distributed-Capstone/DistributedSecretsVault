#!/usr/bin/env bash
# High concurrency against the same small key namespace across several users.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/dsvclient-cluster-helpers.sh"
parse_gateway_arg "$@"

USERS="${USERS:-10}"
KEYS="${KEYS:-20}"
REQUESTS_PER_USER="${REQUESTS_PER_USER:-500}"
PARALLELISM="${PARALLELISM:-200}"
PROGRESS_INTERVAL="${PROGRESS_INTERVAL:-200}"

setup_suite
section "Five users writing the same key names concurrently"

RESULTS_DIR="${WORK_DIR}/same-key-namespaces"
mkdir -p "$RESULTS_DIR"

# Pre-create the shared key names for each user, so the heavy phase can hammer updates/reads.
for u in $(seq 1 "$USERS"); do
    user="namespace-user-${u}-${RUN_ID}"
    for k in $(seq 1 "$KEYS"); do
        name="shared-key-${k}"
        out="$(dsvc "$user" create "$name" "initial-${u}-${k}" 2>&1)" || true
        printf "%s\n" "$out" >"${RESULTS_DIR}/seed-${u}-${k}.log"
        if printf "%s" "$out" | grep -Fq "Secret created"; then
            write_status "${RESULTS_DIR}/status-seed-${u}-${k}" "PASS"
        else
            write_status "${RESULTS_DIR}/status-seed-${u}-${k}" "CHECK"
        fi
    done
    progress_status "Namespace seed progress ${u}/${USERS} users" "$RESULTS_DIR" "status-seed-*"
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
                write_status "${RESULTS_DIR}/status-op-${op_id}" "PASS"
            else
                write_status "${RESULTS_DIR}/status-op-${op_id}" "CHECK"
            fi
        ) &
        if (( op_id % PARALLELISM == 0 )); then
            wait
            if (( op_id % PROGRESS_INTERVAL == 0 )); then
                progress_status "Namespace load progress ${op_id}/$((USERS * REQUESTS_PER_USER))" "$RESULTS_DIR" "status-op-*"
            fi
        fi
    done
done
wait
progress_status "Namespace load final progress ${op_id}/${op_id}" "$RESULTS_DIR" "status-op-*"

passed="$(count_status "$RESULTS_DIR" PASS)"
check="$(count_status "$RESULTS_DIR" CHECK)"
if (( check == 0 )); then
    pass "Same-key namespace load completed: users=${USERS}, keys=${KEYS}, operations=${passed}"
else
    fail "Same-key namespace load needs review: pass=${passed}, check=${check}. Logs: ${RESULTS_DIR}"
fi

print_summary

