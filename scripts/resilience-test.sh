#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
gateway="http://localhost:8088"
result_file="$(mktemp)"
restore_needed=false

restore_dependency() {
  docker compose start --wait --wait-timeout 90 vehicle-intelligence >&2 || return 1
  docker compose restart gateway >&2 || return 1
  for attempt in $(seq 1 30); do
    if curl --silent --fail --max-time 3 "$gateway/intelligence/health" >/dev/null; then
      return 0
    fi
    sleep 1
  done
  echo "FAIL gateway readiness after dependency recovery" >&2
  return 1
}

cleanup() {
  local status=$?
  trap - EXIT
  if "$restore_needed"; then
    if ! restore_dependency; then
      echo "FAIL restore vehicle-intelligence; run docker compose start vehicle-intelligence" >&2
      status=1
    fi
  fi
  rm -f "$result_file"
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'echo "FAIL resilience at line $LINENO" >&2' ERR

request() {
  local method="$1" path="$2" token="$3" payload="${4:-}"
  local args=(--silent --show-error --max-time 20 --request "$method"
    --output "$result_file" --write-out '%{http_code} %{content_type}')
  if test -n "$token"; then args+=(--header "Authorization: Bearer $token"); fi
  if test -n "$payload"; then args+=(--header 'Content-Type: application/json' --data "$payload"); fi
  curl "${args[@]}" "$gateway$path"
}

assert_unavailable() {
  local metadata="$1"
  test "${metadata%% *}" = 503
  local content_type="${metadata#* }"
  test "${content_type%%;*}" = application/problem+json
  jq -e --arg path "/api/v1/journeys/$journey_id/recommendation" '
    (keys | sort) == ["detail", "instance", "status", "title", "type"]
    and .status == 503 and .type == "https://ford.example/problems/503"
    and .title == "Dependency unavailable" and .instance == $path
    and (.detail | type == "string" and length > 0)' "$result_file" >/dev/null
}

for path in journey/actuator/health intelligence/health workshop/health; do
  curl --silent --show-error --fail --max-time 5 "$gateway/$path" >/dev/null
done
test -n "$(docker compose ps --status running --quiet vehicle-intelligence)"
echo "PASS all services running before fault injection"

metadata="$(request POST /journey/api/v1/auth/token '' \
  '{"username":"adviser@ford.com","password":"Ford@123"}')"
test "${metadata%% *}" = 201
adviser_token="$(jq -er '.accessToken' "$result_file")"
vin="1FR$(date +%s%N | tail -c 15)"
customer_id="$(tr -d '\n' </proc/sys/kernel/random/uuid)"
metadata="$(request POST /journey/api/v1/journeys "$adviser_token" \
  "{\"customerId\":\"$customer_id\",\"vin\":\"$vin\"}")"
test "${metadata%% *}" = 201
journey_id="$(jq -er '.id' "$result_file")"
metadata="$(request GET "/journey/api/v1/journeys/$journey_id" "$adviser_token")"
test "${metadata%% *}" = 200
before="$(cat "$result_file")"
jq -e '.status == "DETECTED"' <<<"$before" >/dev/null
payload="{\"vin\":\"$vin\",\"odometerKm\":42420,\"oilLifePercent\":12,\"batteryVoltage\":11,\"engineTemperatureC\":120,\"diagnosticCodes\":[\"P0217\"]}"
echo "PASS create and read DETECTED journey before dependency failure"

# Set the recovery guard before stopping, including partial stop failures.
restore_needed=true
docker compose stop --timeout 5 vehicle-intelligence >&2
test -z "$(docker compose ps --status running --quiet vehicle-intelligence)"
echo "PASS real Python container stopped"
for attempt in 1 2 3 4 5; do
  metadata="$(request POST "/journey/api/v1/journeys/$journey_id/recommendation" "$adviser_token" "$payload")"
  assert_unavailable "$metadata"
  echo "PASS dependency failure $attempt returns 503 Problem Details"
done

# Evict only this synthetic journey to verify PostgreSQL state, not a cached snapshot.
docker compose exec -T redis redis-cli DEL "journeys::$journey_id" >/dev/null
metadata="$(request GET "/journey/api/v1/journeys/$journey_id" "$adviser_token")"
test "${metadata%% *}" = 200
jq -e --argjson before "$before" '. == $before' "$result_file" >/dev/null
curl --silent --show-error --fail --max-time 5 "$gateway/workshop/health" >/dev/null
echo "PASS persisted journey unchanged after failures; workshop remains available"

restore_dependency
restore_needed=false
echo "PASS Python restored and gateway refreshed"

recovered=false
deadline=$((SECONDS + 45))
while test "$SECONDS" -lt "$deadline"; do
  metadata="$(request POST "/journey/api/v1/journeys/$journey_id/recommendation" "$adviser_token" "$payload")"
  if test "${metadata%% *}" = 200; then
    recovered=true
    break
  fi
  assert_unavailable "$metadata"
  sleep 1
done
"$recovered"
recommendation="$(cat "$result_file")"
jq -e --arg vin "$vin" '.vin == $vin and .riskLevel == "HIGH" and (.action | length > 0)' \
  <<<"$recommendation" >/dev/null
echo "PASS recommendation succeeds after dependency recovery"

# A second successful probe permits the configured half-open circuit to close.
metadata="$(request POST "/journey/api/v1/journeys/$journey_id/recommendation" "$adviser_token" "$payload")"
test "${metadata%% *}" = 200
jq -e --argjson expected "$recommendation" '. == $expected' "$result_file" >/dev/null
metadata="$(request GET "/journey/api/v1/journeys/$journey_id" "$adviser_token")"
test "${metadata%% *}" = 200
jq -e --arg action "$(jq -r '.action' <<<"$recommendation")" '
  .status == "RECOMMENDED" and .nextAction == $action' "$result_file" >/dev/null
metadata="$(request GET "/intelligence/api/v1/vehicles/$vin/risk" "$adviser_token")"
test "${metadata%% *}" = 200
jq -e --argjson expected "$recommendation" '
  .vin == $expected.vin and .risk_level == $expected.riskLevel
  and .risk_score == $expected.riskScore and .action == $expected.action' "$result_file" >/dev/null
echo "PASS stable recovery, persisted recommendation and updated journey cache"
echo "ALL RESILIENCE TESTS PASSED"
