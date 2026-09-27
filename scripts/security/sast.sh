#!/usr/bin/env bash
# SAST com Semgrep: rulesets públicos + regras próprias do projeto (.semgrep/).
# Mesmo comando no CI e na máquina do dev. Sai com 1 se houver achado de severidade ERROR.
#
#   scripts/security/sast.sh            # repo inteiro
#   scripts/security/sast.sh file ...   # só os arquivos informados (pre-commit)
set -euo pipefail

SEMGREP_IMAGE="${SEMGREP_IMAGE:-semgrep/semgrep:1.177.0}"
root="$(git rev-parse --show-toplevel)"
reports="$root/reports"
mkdir -p "$reports"

configs=(
  --config p/security-audit --config p/owasp-top-ten --config p/secrets --config p/jwt
  --config p/java --config p/python --config p/csharp
  --config p/dockerfile --config p/docker-compose --config p/nginx
  --config .semgrep/
)

docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -v "$root:/src" -w /src "$SEMGREP_IMAGE" \
  semgrep scan --metrics=off --quiet "${configs[@]}" \
  --json-output=reports/semgrep.json --sarif-output=reports/semgrep.sarif "$@" >/dev/null

jq -r '
  .results
  | "Semgrep: \(length) achado(s) — \([.[] | select(.extra.severity == "ERROR")] | length) bloqueante(s) (ERROR)",
    (.[] | "  [\(.extra.severity)] \(.check_id | split(".") | last)  \(.path):\(.start.line)")
' "$reports/semgrep.json"

errors="$(jq '[.results[] | select(.extra.severity == "ERROR")] | length' "$reports/semgrep.json")"
test "$errors" -eq 0
