#!/usr/bin/env bash
# Secret scanning com Trufflehog sobre TODO o histórico git: um segredo apagado num commit
# posterior continua exposto no histórico.
#
# Política:
#   verified   → credencial ativa confirmada no provedor       → BLOQUEIA
#   unknown    → detector casou, verificação não foi possível   → BLOQUEIA (na dúvida, trata como real)
#   unverified → detector casou, provedor recusou a credencial → relatório para triagem
set -euo pipefail

TRUFFLEHOG_IMAGE="${TRUFFLEHOG_IMAGE:-trufflesecurity/trufflehog:3.97.9}"
root="$(git rev-parse --show-toplevel)"
reports="$root/reports"
mkdir -p "$reports"

trufflehog() {
  docker run --rm -v "$root:/repo" "$TRUFFLEHOG_IMAGE" git file:///repo --no-update "$@"
}

trufflehog --results=verified,unverified,unknown --json 2>/dev/null >"$reports/trufflehog.json"

jq -rs '
  "Trufflehog: \(length) achado(s) — \([.[] | select(.Verified)] | length) verificado(s)",
  (.[] | "  [\(if .Verified then "VERIFIED" else "unverified" end)] \(.DetectorName)  \(.SourceMetadata.Data.Git.file):\(.SourceMetadata.Data.Git.line)  commit \(.SourceMetadata.Data.Git.commit[0:7])")
' "$reports/trufflehog.json"

echo "Gate (verified + unknown):"
trufflehog --results=verified,unknown --fail
echo "  nenhum segredo verificado ou não verificável"
