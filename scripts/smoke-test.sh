#!/usr/bin/env bash
set -euo pipefail

gateway="${GATEWAY_URL:-http://localhost:8088}"
problem_file="$(mktemp)"
trap 'rm -f "$problem_file"' EXIT
trap 'echo "FAIL smoke at line $LINENO" >&2' ERR

request_json() {
  local expected="$1" method="$2" path="$3" token="$4" payload="${5:-}"
  local args=(--silent --show-error --max-time 20 --request "$method"
    --header "Authorization: Bearer $token" --output "$problem_file" --write-out '%{http_code}')
  if test -n "$payload"; then args+=(--header 'Content-Type: application/json' --data "$payload"); fi
  local status
  status="$(curl "${args[@]}" "$gateway$path")"
  if test "$status" != "$expected"; then
    echo "FAIL $method $path expected $expected, received $status" >&2
    cat "$problem_file" >&2
    return 1
  fi
  cat "$problem_file"
}

wait_for() {
  local name="$1" url="$2"
  for attempt in $(seq 1 30); do
    if curl --silent --fail --max-time 3 "$url" >/dev/null; then
      echo "PASS health $name"
      return
    fi
    sleep 2
  done
  echo "FAIL health $name" >&2
  exit 1
}

login() {
  local username="$1"
  curl --silent --show-error --fail --max-time 10 --request POST "$gateway/journey/api/v1/auth/token" \
    --header 'Content-Type: application/json' \
    --data "{\"username\":\"$username\",\"password\":\"Ford@123\"}" | jq -er '.accessToken'
}

check_problem() {
  local expected="$1" method="$2" path="$3" token="${4:-}" payload="${5:-}"
  local args=(--silent --show-error --max-time 20 --request "$method" --output "$problem_file"
    --write-out '%{http_code} %{content_type}')
  if test -n "$token"; then args+=(--header "Authorization: Bearer $token"); fi
  if test -n "$payload"; then args+=(--header 'Content-Type: application/json' --data "$payload"); fi
  local metadata content_type
  metadata="$(curl "${args[@]}" "$gateway$path")"
  content_type="${metadata#* }"
  test "${metadata%% *}" = "$expected"
  test "${content_type%%;*}" = "application/problem+json"
  jq -e --argjson expected "$expected" '
    (keys | sort) == ["detail", "instance", "status", "title", "type"]
    and .status == $expected
    and .type == ("https://ford.example/problems/" + ($expected | tostring))
    and (.title | type == "string" and length > 0)
    and (.detail | type == "string" and length > 0)
    and (.instance | startswith("/api/v1/"))
  ' "$problem_file" >/dev/null
  echo "PASS Problem Details $expected $method $path"
}

wait_for journey "$gateway/journey/actuator/health"
wait_for intelligence "$gateway/intelligence/health"
wait_for workshop "$gateway/workshop/health"

admin_token="$(login admin@ford.com)"
vehicle_token="$(login vehicle@ford.com)"
customer_token="$(login customer@ford.com)"
adviser_token="$(login adviser@ford.com)"
technician_token="$(login technician@ford.com)"
echo "PASS authentication profiles"

check_problem 401 GET /journey/api/v1/journeys/00000000-0000-0000-0000-000000000000
check_problem 401 GET /intelligence/api/v1/vehicles/1FMCU9GDXMUA99999/risk
check_problem 401 GET /workshop/api/v1/work-orders/00000000-0000-0000-0000-000000000000

vin="1FM$(date +%s%N | tail -c 15)"
customer_id="$(tr -d '\n' </proc/sys/kernel/random/uuid)"
journey_json="$(request_json 201 POST /journey/api/v1/journeys "$adviser_token" \
  "{\"customerId\":\"$customer_id\",\"vin\":\"$vin\"}")"
journey_id="$(jq -er '.id' <<<"$journey_json")"
jq -e '.status == "DETECTED"' <<<"$journey_json" >/dev/null
echo "PASS create journey $journey_id"
request_json 200 GET "/journey/api/v1/journeys/$journey_id" "$customer_token" |
  jq -e '.status == "DETECTED"' >/dev/null
echo "PASS warm journey cache before recommendation"

telemetry="{\"vin\":\"$vin\",\"odometer_km\":42420,\"oil_life_percent\":12,\"battery_voltage\":11.0,\"engine_temperature_c\":120,\"diagnostic_codes\":[\"P0217\",\"P0562\"]}"
healthy_telemetry="$(jq '.oil_life_percent = 100 | .battery_voltage = 12.6 |
  .engine_temperature_c = 90 | .diagnostic_codes = []' <<<"$telemetry")"
request_json 202 POST /intelligence/api/v1/telemetry "$vehicle_token" "$healthy_telemetry" |
  jq -e '.risk_level == "LOW"' >/dev/null
request_json 200 GET "/intelligence/api/v1/vehicles/$vin/risk" "$customer_token" |
  jq -e '.risk_level == "LOW"' >/dev/null
intelligence_json="$(request_json 202 POST /intelligence/api/v1/telemetry "$vehicle_token" "$telemetry")"
jq -e '.risk_level == "HIGH" or .risk_level == "CRITICAL"' <<<"$intelligence_json" >/dev/null
echo "PASS ingest telemetry and classify risk"
request_json 200 GET "/intelligence/api/v1/vehicles/$vin/risk" "$customer_token" |
  jq -e --argjson expected "$intelligence_json" '. == $expected' >/dev/null
echo "PASS latest risk replaces previously cached LOW risk"

recommendation_payload="$(jq '{
  vin,
  odometerKm: .odometer_km,
  oilLifePercent: .oil_life_percent,
  batteryVoltage: .battery_voltage,
  engineTemperatureC: .engine_temperature_c,
  diagnosticCodes: .diagnostic_codes
}' <<<"$telemetry")"
recommendation_json="$(request_json 200 POST "/journey/api/v1/journeys/$journey_id/recommendation" \
  "$adviser_token" "$recommendation_payload")"
jq -e --argjson expected "$intelligence_json" '
  .vin == $expected.vin and .riskLevel == $expected.risk_level
  and .riskScore == $expected.risk_score and .action == $expected.action
  and .reasons == $expected.reasons' <<<"$recommendation_json" >/dev/null
echo "PASS cross-service recommendation with JWT"

customer_view="$(request_json 200 GET "/journey/api/v1/journeys/$journey_id" "$customer_token")"
jq -e --arg action "$(jq -r '.action' <<<"$recommendation_json")" \
  '.status == "RECOMMENDED" and .nextAction == $action' <<<"$customer_view" >/dev/null
echo "PASS customer reads recommended journey"
request_json 200 POST "/journey/api/v1/journeys/$journey_id/transitions" "$adviser_token" \
  '{"target":"SCHEDULED","nextAction":"ARRIVE_AT_WORKSHOP"}' | jq -e '.status == "SCHEDULED"' >/dev/null
request_json 200 GET "/journey/api/v1/journeys/$journey_id" "$customer_token" |
  jq -e '.status == "SCHEDULED"' >/dev/null
echo "PASS schedule journey and invalidate cached state"

appointment_id="$(tr -d '\n' </proc/sys/kernel/random/uuid)"
work_order_payload="$(jq --arg appointment "$appointment_id" '{
  appointmentId: $appointment, vin, description: "Predictive inspection",
  riskLevel, vehicleImmobilized: false}' <<<"$recommendation_json")"
work_order_json="$(request_json 201 POST /workshop/api/v1/work-orders "$adviser_token" "$work_order_payload")"
work_order_id="$(jq -er '.id' <<<"$work_order_json")"
expected_priority="$(jq -r '.riskLevel | if . == "CRITICAL" then "Critical"
  elif . == "HIGH" then "Urgent" else "Normal" end' <<<"$recommendation_json")"
jq -e --arg priority "$expected_priority" --arg vin "$vin" --arg appointment "$appointment_id" \
  '.priority == $priority and .status == "Created" and .vin == $vin and .appointmentId == $appointment' \
  <<<"$work_order_json" >/dev/null
echo "PASS create prioritized work order $work_order_id"

work_order_before="$(request_json 200 GET "/workshop/api/v1/work-orders/$work_order_id" "$customer_token")"
check_problem 409 POST /workshop/api/v1/work-orders "$adviser_token" "$work_order_payload"
request_json 200 GET "/workshop/api/v1/work-orders/$work_order_id" "$customer_token" |
  jq -e --argjson original "$work_order_before" '. == $original' >/dev/null
check_problem 422 POST "/workshop/api/v1/work-orders/$work_order_id/transitions" "$technician_token" '{"target":"Completed"}'
check_problem 403 POST "/workshop/api/v1/work-orders/$work_order_id/transitions" "$adviser_token" '{"target":"Diagnosing"}'
request_json 200 GET "/workshop/api/v1/work-orders/$work_order_id" "$customer_token" |
  jq -e '.status == "Created"' >/dev/null
echo "PASS rejected duplicate and transitions preserve original order"

request_json 200 POST "/journey/api/v1/journeys/$journey_id/transitions" "$adviser_token" \
  '{"target":"IN_SERVICE","nextAction":"REPAIR"}' | jq -e '.status == "IN_SERVICE"' >/dev/null
request_json 200 GET "/journey/api/v1/journeys/$journey_id" "$customer_token" |
  jq -e '.status == "IN_SERVICE"' >/dev/null
for target in Diagnosing AwaitingApproval InProgress QualityCheck Completed; do
  request_json 200 POST "/workshop/api/v1/work-orders/$work_order_id/transitions" "$technician_token" \
    "{\"target\":\"$target\"}" | jq -e --arg target "$target" '.status == $target' >/dev/null
  request_json 200 GET "/workshop/api/v1/work-orders/$work_order_id" "$customer_token" |
    jq -e --arg target "$target" --arg priority "$expected_priority" --arg vin "$vin" \
      --arg appointment "$appointment_id" --arg id "$work_order_id" '
      .status == $target and .priority == $priority and .vin == $vin
      and .appointmentId == $appointment and .id == $id' >/dev/null
  echo "PASS technician transitions and customer reads work order $target"
done
request_json 200 POST "/journey/api/v1/journeys/$journey_id/transitions" "$adviser_token" \
  '{"target":"COMPLETED","nextAction":"SERVICE_FINISHED"}' | jq -e '.status == "COMPLETED"' >/dev/null
customer_view="$(request_json 200 GET "/journey/api/v1/journeys/$journey_id" "$customer_token")"
jq -e --arg id "$journey_id" --arg vin "$vin" --arg customer "$customer_id" '
  .status == "COMPLETED" and .nextAction == "SERVICE_FINISHED"
  and .id == $id and .vin == $vin and .customerId == $customer' <<<"$customer_view" >/dev/null
check_problem 422 POST "/journey/api/v1/journeys/$journey_id/transitions" "$adviser_token" \
  '{"target":"CONTACTED","nextAction":"INVALID"}'
request_json 200 GET "/journey/api/v1/journeys/$journey_id" "$customer_token" |
  jq -e --argjson original "$customer_view" '. == $original' >/dev/null
echo "PASS full journey and work order complete without terminal state regression"

check_problem 400 POST "/journey/api/v1/journeys/$journey_id/recommendation" "$adviser_token" \
  "$(jq '.batteryVoltage = -1' <<<"$recommendation_payload")"
check_problem 422 POST /intelligence/api/v1/telemetry "$vehicle_token" "$(jq '.vin = null' <<<"$telemetry")"

check_problem 403 POST /journey/api/v1/journeys "$customer_token" \
  "{\"customerId\":\"$customer_id\",\"vin\":\"$vin\"}"
check_problem 403 POST /intelligence/api/v1/telemetry "$customer_token" "$telemetry"
check_problem 403 POST /workshop/api/v1/work-orders "$customer_token" \
  "{\"appointmentId\":\"$appointment_id\",\"vin\":\"$vin\",\"description\":\"Inspection\",\"riskLevel\":\"HIGH\",\"vehicleImmobilized\":false}"
check_problem 400 POST /journey/api/v1/journeys "$admin_token" '{}'
check_problem 422 POST /intelligence/api/v1/telemetry "$admin_token" '{"vin":"invalid"}'
check_problem 400 POST /workshop/api/v1/work-orders "$admin_token" '{}'
check_problem 404 GET /journey/api/v1/journeys/00000000-0000-0000-0000-000000000000 "$admin_token"
missing_vin="1FN$(date +%s%N | tail -c 15)"
check_problem 404 GET "/intelligence/api/v1/vehicles/$missing_vin/risk" "$admin_token"
check_problem 404 GET /workshop/api/v1/work-orders/00000000-0000-0000-0000-000000000000 "$admin_token"

curl --silent --show-error --fail --max-time 10 "$gateway/journey/v3/api-docs" | jq -e '
  .paths["/api/v1/auth/token"].post.security == [] and .security[0].bearerAuth == []
' >/dev/null
curl --silent --show-error --fail --max-time 10 "$gateway/intelligence/openapi.json" >/dev/null
curl --silent --show-error --fail --max-time 10 "$gateway/workshop/swagger/v1/swagger.json" >/dev/null
echo "PASS OpenAPI for all services"
echo "ALL SMOKE TESTS PASSED"
