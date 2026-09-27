from structlog.testing import capture_logs


def test_request_id_is_propagated_when_safe(client):
    response = client.get("/health", headers={"X-Request-ID": "gw-5f2c9a1e-trace"})
    assert response.headers["X-Request-ID"] == "gw-5f2c9a1e-trace"


def test_forged_request_id_is_replaced(client):
    response = client.get("/health", headers={"X-Request-ID": "forged level=ERROR"})
    assert "forged" not in response.headers["X-Request-ID"]
    assert len(response.headers["X-Request-ID"]) == 36


def test_rejected_token_is_audited(client, telemetry):
    with capture_logs() as logs:
        client.post("/api/v1/telemetry", json=telemetry, headers={"X-Real-IP": "203.0.113.7"})
    event = next(item for item in logs if item["event"] == "auth.token.rejected")
    assert event["source.ip"] == "203.0.113.7"
    assert event["url.path"] == "/api/v1/telemetry"


def test_role_denial_is_audited(client, telemetry, token):
    with capture_logs() as logs:
        client.post(
            "/api/v1/telemetry", json=telemetry,
            headers={"Authorization": f"Bearer {token('CUSTOMER')}"},
        )
    assert any(item["event"] == "authz.denied" for item in logs)


def test_inference_is_logged_without_vin(client, telemetry, token):
    with capture_logs() as logs:
        client.post(
            "/api/v1/telemetry", json=telemetry,
            headers={"Authorization": f"Bearer {token('VEHICLE')}"},
        )
    event = next(item for item in logs if item["event"] == "ml.inference")
    assert event["model_version"] == "rules-v1"
    assert telemetry["vin"] not in str(event)
