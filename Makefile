.PHONY: up down build test smoke resilience verify logs clean security sast secrets hooks env

up: .env
	docker compose up --build -d
	docker compose restart gateway

down:
	docker compose down

build:
	docker compose build

test:
	cd customer-journey-service && ./gradlew test jacocoTestReport
	cd vehicle-intelligence-service && uv sync --all-groups && uv run ruff check . && uv run pytest
	dotnet test workshop-operations-service/Ford.Workshop.sln --configuration Release

smoke:
	./scripts/smoke-test.sh

resilience:
	./scripts/resilience-test.sh

verify:
	$(MAKE) test
	$(MAKE) up
	$(MAKE) smoke
	$(MAKE) resilience

logs:
	docker compose logs -f --tail=200

clean:
	docker compose down --volumes --remove-orphans

security: secrets sast

sast:
	./scripts/security/sast.sh

secrets:
	./scripts/security/secret-scan.sh

hooks:
	git config core.hooksPath .githooks

env: .env

.env:
	./scripts/security/generate-env.sh
