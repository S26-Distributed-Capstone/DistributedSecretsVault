#!/usr/bin/env bash
# Mixed operation concurrency: creates, reads, updates, deletes, and version reads in parallel.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/dsvclient-cluster-helpers.sh"
parse_gateway_arg "$@"

SEED_COUNT="${SEED_COUNT:-250}"
ROUNDS="${ROUNDS:-12}"
PARALLELISM="${PARALLELISM:-200}"
USER_COUNT="${USER_COUNT:-25}"
PROGRESS_INTERVAL="${PROGRESS_INTERVAL:-200}"

setup_suite
section "Seeding mixed-concurrency data"

RESULTS_DIR="${WORK_DIR}/mixed-concurrency"
mkdir -p "$RESULTS_DIR"

for i in $(seq 1 "$SEED_COUNT"); do
    user="mixed-user-$((i % USER_COUNT))-${RUN_ID}"
    name="mixed-secret-${RUN_ID}-${i}"
    out="$(dsvc "$user" create "$name" "seed-${i}" 2>&1)" || true
    printf "%s\n" "$out" > "${RESULTS_DIR}/seed-${i}.log"
    if printf "%s" "$out" | grep -Fq "Secret created"; then
        write_status "${RESULTS_DIR}/status-seed-${i}" "PASS"
    else
        write_status "${RESULTS_DIR}/status-seed-${i}" "CHECK"
    fi
    if (( i % PROGRESS_INTERVAL == 0 )); then
        progress_status "Mixed seed progress ${i}/${SEED_COUNT}" "$RESULTS_DIR" "status-seed-*"
    fi
done
progress_status "Mixed seed final progress ${SEED_COUNT}/${SEED_COUNT}" "$RESULTS_DIR" "status-seed-*"

section "Running mixed concurrent load"

op_id=0
for round in $(seq 1 "$ROUNDS"); do
    for i in $(seq 1 "$SEED_COUNT"); do
        user="mixed-user-$((i % USER_COUNT))-${RUN_ID}"
        name="mixed-secret-${RUN_ID}-${i}"
        op_id=$((op_id + 1))
        (
            case $((op_id % 6)) in
                0) out="$(dsvc "$user" get "$name" 2>&1)" ;;
                1) out="$(dsvc "$user" update "$name" "round-${round}-value-${i}" 2>&1)" ;;
                2) out="$(dsvc "$user" get "$name" --all 2>&1)" ;;
                3) out="$(dsvc "$user" get "$name" --version 1 2>&1)" ;;
                4)
                    new_name="mixed-new-${RUN_ID}-${round}-${i}"
                    out="$(dsvc "$user" create "$new_name" "new-${round}-${i}" 2>&1)"
                    ;;
                5)
                    delete_name="mixed-delete-${RUN_ID}-${round}-${i}"
                    create_out="$(dsvc "$user" create "$delete_name" "delete-me-${round}-${i}" 2>&1)"
                    delete_out="$(dsvc "$user" delete "$delete_name" 2>&1)"
                    out="${create_out}"$'\n'"${delete_out}"
                    ;;
            esac
            printf "%s\n" "$out" > "${RESULTS_DIR}/op-${op_id}.log"
            if printf "%s" "$out" | grep -Eq "Secret created|Secret updated|Delete succeeded|seed-|round-|new-|\\{|\\["; then
                write_status "${RESULTS_DIR}/status-op-${op_id}" "PASS"
            else
                write_status "${RESULTS_DIR}/status-op-${op_id}" "CHECK"
            fi
        ) &
        if (( op_id % PARALLELISM == 0 )); then
            wait
            if (( op_id % PROGRESS_INTERVAL == 0 )); then
                progress_status "Mixed load progress ${op_id}/$((SEED_COUNT * ROUNDS))" "$RESULTS_DIR" "status-op-*"
            fi
        fi
    done
done
wait
progress_status "Mixed load final progress ${op_id}/${op_id}" "$RESULTS_DIR" "status-op-*"

passed="$(count_status "$RESULTS_DIR" PASS)"
check="$(count_status "$RESULTS_DIR" CHECK)"
if (( passed > 0 && check == 0 )); then
    pass "Mixed concurrent load completed with ${passed} successful operations"
else
    fail "Mixed concurrent load needs review: pass=${passed}, check=${check}. Logs: ${RESULTS_DIR}"
fi

print_summary
