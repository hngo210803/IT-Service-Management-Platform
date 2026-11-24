#!/usr/bin/env bash
# Backend becomes unready when PostgreSQL stops and recovers when it returns.
set -euo pipefail

kube() {
  kubectl --context kind-itsm -n itsm "$@"
}

kube wait --for=condition=Ready pod -l app=backend --timeout=30s
if [[ "$(kube get statefulset postgres -o jsonpath='{.status.readyReplicas}')" != 1 ||
      "$(kube get statefulset postgres -o jsonpath='{.spec.replicas}')" != 1 ]]; then
  echo "Start the local PostgreSQL StatefulSet with one ready replica before running the test." >&2
  exit 1
fi

test_dir=$(mktemp -d)
restore_postgres=false
cleanup() {
  local result=$?
  trap - EXIT
  if [[ "$restore_postgres" == true ]]; then
    if ! kube scale statefulset/postgres --replicas=1 ||
       ! kube rollout status statefulset/postgres --timeout=120s; then
      echo "Could not restore PostgreSQL in kind-itsm." >&2
      result=1
    fi
  fi
  rm -rf -- "$test_dir"
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

read_tickets() {
  kube exec deployment/frontend -c frontend -- wget -qO- http://127.0.0.1/api/tickets
}

check_health() {
  local probe=$1 expected=$2 response
  response=$(kube exec deployment/backend -c backend -- wget -T 40 -S -O /dev/null \
    "http://127.0.0.1:8080/actuator/health/$probe" 2>&1) || true
  if ! grep -Eq "HTTP/[0-9.]+ $expected([[:space:]]|$)" <<< "$response"; then
    printf 'Expected HTTP %s from %s, got:\n%s\n' "$expected" "$probe" "$response" >&2
    return 1
  fi
  printf '%s: HTTP %s\n' "$probe" "$expected"
}

read_tickets > "$test_dir/before.json"
python3 -c 'import json, sys; tickets = json.load(sys.stdin); sys.exit(0 if tickets else "Create a ticket in kind-itsm before running this test.")' < "$test_dir/before.json"
backend_before=$(kube get pod -l app=backend -o jsonpath='{.items[0].metadata.uid}:{.items[0].status.containerStatuses[0].restartCount}')
check_health readiness 200

restore_postgres=true
kube scale statefulset/postgres --replicas=0
kube wait --for=condition=Ready=false pod -l app=backend --timeout=120s
check_health liveness 200
check_health readiness 503

kube scale statefulset/postgres --replicas=1
kube rollout status statefulset/postgres --timeout=120s
restore_postgres=false
kube wait --for=condition=Ready pod -l app=backend --timeout=120s
check_health readiness 200
backend_after=$(kube get pod -l app=backend -o jsonpath='{.items[0].metadata.uid}:{.items[0].status.containerStatuses[0].restartCount}')
if [[ "$backend_after" != "$backend_before" ]]; then
  echo "The backend restarted or its pod was replaced during the test." >&2
  exit 1
fi

read_tickets > "$test_dir/after.json"
python3 - "$test_dir/before.json" "$test_dir/after.json" <<'PY'
import json
import sys

with open(sys.argv[1]) as before_file, open(sys.argv[2]) as after_file:
    before = json.load(before_file)
    after = {ticket["id"]: ticket for ticket in json.load(after_file)}

for ticket in before:
    if after.get(ticket["id"]) != ticket:
        sys.exit(f"Ticket #{ticket['id']} was lost or changed.")
print(f"Verified {len(before)} existing ticket(s) after PostgreSQL restarted.")
PY
echo "PASS: Backend becomes unready when PostgreSQL stops and recovers when it returns"
