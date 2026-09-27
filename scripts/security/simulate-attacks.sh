#!/usr/bin/env bash
# Simula os ataques que os controles da Etapa 2 bloqueiam e que as regras de alerta da Etapa 3
# detectam. Roda contra a stack local (make up) e imprime esperado × obtido de cada cenário.
# Os eventos gerados aparecem em `docker compose logs` (logger security.audit, access log JSON).
set -euo pipefail

gateway="${GATEWAY_URL:-http://localhost:8088}"
body="$(mktemp)"
trap 'rm -f "$body"' EXIT

status() { curl --silent --output "$body" --write-out '%{http_code}' --max-time 10 "$@"; }
expect() {
  local expected="$1" got="$2" label="$3"
  if test "$expected" = "$got"; then echo "  ok   $label → $got"; else echo "  FAIL $label → esperado $expected, obtido $got" >&2; exit 1; fi
}
login() {
  status --request POST "$gateway/journey/api/v1/auth/token" --header 'Content-Type: application/json' \
    --data "{\"username\":\"$1\",\"password\":\"$2\"}"
}
token() { login "$1" Ford@123 >/dev/null && jq -er '.accessToken' "$body"; }

echo "1. Força bruta contra uma conta (bloqueio após 5 falhas; dura 15 min, estado em memória)"
for attempt in 1 2 3 4 5; do expect 401 "$(login technician@ford.com "guess-$attempt")" "senha errada #$attempt"; done
expect 429 "$(login technician@ford.com Ford@123)" "senha CORRETA com a conta bloqueada"

echo "2. Credential stuffing em rajada (rate limit do gateway, 5 req/min por IP)"
throttled=0
for attempt in $(seq 1 12); do
  test "$(login "user$attempt@example.com" leaked-password)" = 429 && throttled=$((throttled + 1))
done
test "$throttled" -gt 0 && echo "  ok   $throttled de 12 tentativas barradas no gateway (429)"
jq -e '.status == 429 and .type == "https://ford.example/problems/429"' "$body" >/dev/null &&
  echo "  ok   429 do gateway no formato Problem Details"
echo "     aguardando a janela do rate limit (60s) para os próximos cenários..."
sleep 60

echo "3. Token forjado (assinatura inválida) e token sem assinatura (alg=none)"
forged="$(token adviser@ford.com)"
forged="${forged%.*}.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
expect 401 "$(status --header "Authorization: Bearer $forged" "$gateway/journey/api/v1/journeys/00000000-0000-0000-0000-000000000000")" "assinatura adulterada"
none="$(printf '{"alg":"none"}' | base64 | tr -d '=\n' | tr '/+' '_-').$(printf '{"sub":"x","roles":["ADMIN"],"iss":"ford-zero-touch","aud":"ford-api","exp":4102444800}' | base64 | tr -d '=\n' | tr '/+' '_-')."
expect 401 "$(status --header "Authorization: Bearer $none" "$gateway/workshop/api/v1/work-orders/00000000-0000-0000-0000-000000000000")" "alg=none no .NET"

echo "4. BOLA: cliente tenta ler a jornada de outro cliente trocando o ID"
adviser="$(token adviser@ford.com)"
vin="1FV$(date +%s%N | tail -c 15)"
status --request POST "$gateway/journey/api/v1/journeys" --header "Authorization: Bearer $adviser" \
  --header 'Content-Type: application/json' \
  --data "{\"customerId\":\"$(cat /proc/sys/kernel/random/uuid)\",\"vin\":\"$vin\"}" >/dev/null
foreign="$(jq -er '.id' "$body")"
customer="$(token customer@ford.com)"
expect 404 "$(status --header "Authorization: Bearer $customer" "$gateway/journey/api/v1/journeys/$foreign")" "jornada alheia"
expect 404 "$(status --header "Authorization: Bearer $customer" "$gateway/intelligence/api/v1/vehicles/$vin/risk")" "risco de veículo alheio"
expect 403 "$(status --request POST --header "Authorization: Bearer $customer" --header 'Content-Type: application/json' \
  --data '{"target":"Completed"}' "$gateway/workshop/api/v1/work-orders/$foreign/transitions")" "cliente tentando mudar ordem de serviço"

echo "5. Acesso direto aos serviços, contornando o gateway"
for port in 8080 8000 5000; do
  if curl --silent --max-time 3 "http://localhost:$port/" >/dev/null 2>&1; then
    echo "  FAIL porta $port acessível" >&2; exit 1
  fi
  echo "  ok   porta $port fechada no host"
done

echo "6. Dado pessoal em repouso: o que um dump do banco revela"
docker compose exec -T postgres psql -U "${POSTGRES_USER:-ford}" -d journey -At \
  -c "select substr(vin, 1, 40) || '…', substr(vin_hash, 1, 16) || '…' from customer_journeys limit 2" |
  sed 's/^/  /'
echo "TODOS OS ATAQUES SIMULADOS FORAM BLOQUEADOS"
echo "(technician@ford.com segue bloqueado por 15 min; docker compose restart customer-journey libera)"
