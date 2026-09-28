#!/usr/bin/env bash
# Gera o .env local com segredos aleatórios. Cada ambiente tem seus próprios segredos e nenhum
# valor conhecido fica versionado. Nunca sobrescreve um .env existente.
set -euo pipefail

env_file="$(git rev-parse --show-toplevel)/.env"
if test -e "$env_file"; then
  echo ".env já existe, mantido"
  exit 0
fi

# Senhas vão dentro de URLs e connection strings: remove caracteres com significado nelas.
password() { openssl rand -base64 32 | tr -d '\n/+='; }

umask 077
cat >"$env_file" <<EOF
# Gerado por scripts/security/generate-env.sh em $(date -u +%FT%TZ). Não versionar.
JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
FIELD_ENCRYPTION_KEY=$(openssl rand -base64 32 | tr -d '\n')
POSTGRES_USER=ford
POSTGRES_PASSWORD=$(password)
MONGO_USER=ford
MONGO_PASSWORD=$(password)
EOF
echo ".env criado com segredos aleatórios (permissão 600)"
