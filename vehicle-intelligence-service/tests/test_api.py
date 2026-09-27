from datetime import UTC, datetime

import jwt
import pytest

from ford_intelligence.config import get_settings


def headers(token, *roles):
    return {"Authorization": f"Bearer {token(*roles)}"}


def assert_problem(response, status):
    assert response.status_code == status
    assert response.headers["content-type"] == "application/problem+json"
    body = response.json()
    assert set(body) == {"type", "title", "status", "detail", "instance"}
    assert body["type"] == f"https://ford.example/problems/{status}"
    assert body["status"] == status
    assert body["title"] and body["detail"]
    assert body["instance"] == response.request.url.path


def test_health_is_public(client):
    assert client.get("/health").json() == {"status": "UP"}


def test_telemetry_requires_authentication(client, telemetry):
    response = client.post("/api/v1/telemetry", json=telemetry)
    assert_problem(response, 401)


def test_invalid_and_expired_tokens_are_rejected(client, telemetry, token):
    invalid = client.post(
        "/api/v1/telemetry", json=telemetry, headers={"Authorization": "Bearer nope"}
    )
    expired = client.post(
        "/api/v1/telemetry",
        json=telemetry,
        headers=headers(token, "VEHICLE")
        | {"Authorization": f"Bearer {token('VEHICLE', expired=True)}"},
    )
    assert_problem(invalid, 401)
    assert_problem(expired, 401)


def test_token_without_expiration_is_rejected(client, telemetry):
    never_expires = jwt.encode(
        {"sub": "attacker", "iss": "ford-zero-touch", "aud": "ford-api",
         "iat": datetime.now(UTC), "roles": ["VEHICLE"]},
        get_settings().jwt_secret,
        algorithm="HS256",
    )
    response = client.post(
        "/api/v1/telemetry", json=telemetry, headers={"Authorization": f"Bearer {never_expires}"}
    )
    assert_problem(response, 401)


def test_role_is_enforced(client, telemetry, token):
    response = client.post("/api/v1/telemetry", json=telemetry, headers=headers(token, "CUSTOMER"))
    assert_problem(response, 403)


def test_ingest_and_read_latest_risk(client, telemetry, token):
    created = client.post("/api/v1/telemetry", json=telemetry, headers=headers(token, "VEHICLE"))
    owner = token("CUSTOMER", vins=[telemetry["vin"]])
    found = client.get(
        f"/api/v1/vehicles/{telemetry['vin']}/risk", headers={"Authorization": f"Bearer {owner}"}
    )
    assert created.status_code == 202
    assert found.status_code == 200
    assert found.json()["risk_level"] in {"MEDIUM", "HIGH", "CRITICAL"}


def test_evaluate_endpoint_and_validation(client, telemetry, token):
    valid = client.post(
        "/api/v1/recommendations/evaluate", json=telemetry, headers=headers(token, "SERVICE")
    )
    invalid = client.post(
        "/api/v1/recommendations/evaluate",
        json=telemetry | {"vin": "invalid"},
        headers=headers(token, "ADMIN"),
    )
    assert valid.status_code == 200
    assert_problem(invalid, 422)


@pytest.mark.parametrize("vin", [None, 123, [], {}])
@pytest.mark.parametrize("path", ["/api/v1/telemetry", "/api/v1/recommendations/evaluate"])
def test_non_string_vin_returns_validation_error(client, telemetry, token, vin, path):
    response = client.post(
        path, json=telemetry | {"vin": vin}, headers=headers(token, "ADMIN")
    )
    assert_problem(response, 422)


def test_missing_risk_returns_404(client, token):
    response = client.get(
        "/api/v1/vehicles/1FMCU9GDXMUA99999/risk", headers=headers(token, "ADVISER")
    )
    assert_problem(response, 404)


def test_unhandled_errors_are_standardized(client, telemetry, token, service):
    async def fail(_):
        raise RuntimeError("database unavailable")

    service.evaluate = fail
    response = client.post("/api/v1/telemetry", json=telemetry, headers=headers(token, "VEHICLE"))
    assert_problem(response, 500)
    assert "database unavailable" not in response.json()["detail"]


def test_unknown_endpoint_returns_problem_details(client):
    assert_problem(client.get("/api/v1/missing"), 404)


def test_openapi_documents_problem_details(client):
    operation = client.get("/openapi.json").json()["paths"]["/api/v1/telemetry"]["post"]
    for status in (401, 403, 422, 500):
        content = operation["responses"][str(status)]["content"]
        schema = content["application/problem+json"]["schema"]
        assert set(schema["required"]) == {"type", "title", "status", "detail", "instance"}
