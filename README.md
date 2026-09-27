# Ford Zero Touch · DevSecOps (Sprint 3 · Cybersecurity)

**FIAP · Challenge Ford 2026 · Desafio 02: VIN Share na América do Sul**

| Integrante | RM |
|---|---|
| André Nakamatsu Rocha | RM555004 |
| João Marcelo Furtado Romero | RM555199 |
| Matheus Rivera Montovaneli | RM555499 |

Entrega de Cybersecurity da Sprint 3: a segurança da solução Ford Zero Touch no modelo DevSecOps, rodando junto com o código. Pipeline com **Semgrep** e **Trufflehog**, correções reais nos três microsserviços, trilha de auditoria para alertas e resposta a incidentes, e mapeamento OWASP.

📄 **Documento da entrega (PDF):** [`docs/entrega/entrega-cyber-sprint3.pdf`](docs/entrega/entrega-cyber-sprint3.pdf)

## Resultado

| | Baseline | Depois |
|---|---|---|
| Achados Semgrep (rulesets públicos + regras do projeto) | 22 (20 bloqueantes) | **0** |
| Segredos no histórico (Trufflehog, verified/unknown) | 2 falsos positivos triados | **0** |
| Cliente lê dados de outro cliente (BOLA) | sim, nos 3 serviços | **bloqueado (404)** |
| VIN no banco | texto puro | **AES-256-GCM** |
| Testes automatizados | — | **149 verdes** (Java 60 · Python 41 · .NET 48) |
| Ataques simulados na stack real | — | **6/6 bloqueados** |

O código de partida são os microsserviços da entrega de SOA da Sprint 3, importados sem alteração no primeiro commit (`b4568d9`) para servir de "antes". Os componentes de segurança da nossa entrega de SOA + Cyber da Sprint 1 (criptografia de campo, trilha de auditoria) foram trazidos e melhorados. Cada correção está em um commit próprio, com teste, na branch `fix/security-hardening`.

## Arquitetura

```
Internet ──▶ Gateway Nginx (127.0.0.1:8088) ──▶ customer-journey      Java 17 · Spring Boot · emite JWT
            rate limit · login 5/min por IP     vehicle-intelligence  Python 3.12 · FastAPI · modelo de risco
            headers · corpo ≤ 1 MB · request_id workshop-operations   .NET 8 · ASP.NET Core
                                                PostgreSQL (VIN cifrado) · MongoDB · Redis
```

Só o gateway é publicado, e apenas no loopback; os serviços não têm porta no host. Cada serviço valida o JWT por conta própria.

## Controles de segurança

| Controle | Onde |
|---|---|
| SAST com 10 regras próprias (BOLA, segredos, PII sem cifra, auditoria de login…) | [`.semgrep/ford-rules.yml`](.semgrep/ford-rules.yml) |
| Secret scanning do histórico completo, com triagem documentada | [`scripts/security/secret-scan.sh`](scripts/security/secret-scan.sh) |
| Pipeline: gates + testes das 3 stacks + Trivy (SCA, imagens, Dockerfiles) | [`.github/workflows/devsecops.yml`](.github/workflows/devsecops.yml) |
| Pre-commit local (Trufflehog + Semgrep nos arquivos staged) | [`.githooks/pre-commit`](.githooks/pre-commit) |
| Segredos obrigatórios e aleatórios por ambiente; boot recusa chave fraca | `compose.yml`, [`scripts/security/generate-env.sh`](scripts/security/generate-env.sh) |
| Bloqueio de conta após 5 falhas + rate limit de login no gateway | `LoginAttemptGuard`, [`infra/nginx/nginx.conf`](infra/nginx/nginx.conf) |
| JWT: TTL 15 min, `jti`, `exp` obrigatório, somente HS256, `iss`/`aud` | `SecurityConfig`, `security.py`, `Program.cs` |
| Criptografia de campo AES-256-GCM + blind index HMAC-SHA256 | `FieldEncryption`, `V2__encrypt_vin.sql` |
| RBAC + autorização por objeto (`customer_id` / `vins` no token) | `Ownership`, `routes.py`, `WorkOrdersController` |
| Trilha `security.audit` em JSON com `request_id` nos 3 serviços e no gateway | `SecurityAuditLogger`, `main.py`, `SecurityAudit.cs` |

## Como rodar

Requisitos: Docker Compose, `make`, `jq`, `curl`, `openssl`.

```bash
make up                                  # gera .env com segredos aleatórios (make env) e sobe a stack
./scripts/smoke-test.sh                  # fluxo completo + checagens de BOLA → ALL SMOKE TESTS PASSED
./scripts/security/simulate-attacks.sh   # 6 cenários de ataque → TODOS BLOQUEADOS
docker compose logs customer-journey | grep security.audit
```

Sem `.env`, o `docker compose` recusa subir: não existe mais segredo padrão. APIs pelo gateway em `http://127.0.0.1:8088/{journey,intelligence,workshop}/`; login em `POST /journey/api/v1/auth/token` com `{"username":"admin@ford.com","password":"Ford@123"}` (usuários de demonstração: `admin`, `adviser`, `technician`, `customer`, `vehicle`, todos `@ford.com`).

### Scans de segurança (mesmo comando do CI)

```bash
make hooks      # ativa o pre-commit
make security   # Trufflehog no histórico + Semgrep; relatórios em reports/
make test       # suítes Java, Python e .NET
```

## Evidências

| Pasta | Conteúdo |
|---|---|
| [`docs/evidence/baseline/`](docs/evidence/baseline) | Scans do código de partida: Semgrep só com regras públicas (0), com regras do projeto (22), Trufflehog |
| [`docs/evidence/after/`](docs/evidence/after) | Scans finais, compose sem segredos, smoke, resiliência, ataques simulados, pre-commit barrando chave privada |
| [`docs/evidence/after/logs/`](docs/evidence/after/logs) | Logs reais de segurança dos 3 serviços e do gateway, com exemplo de correlação por `request_id` |
| [`docs/evidence/mobile/`](docs/evidence/mobile) | Semgrep no app Pitlane (commit `4dddb2f`) para o OWASP Mobile Top 10 |
