"""OWASP API1:2023 — um cliente não consulta o risco do veículo de outro trocando o VIN."""

from structlog.testing import capture_logs


def bearer(value: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {value}"}


def test_customer_cannot_read_risk_of_vehicle_they_do_not_own(client, telemetry, token):
    client.post("/api/v1/telemetry", json=telemetry, headers=bearer(token("VEHICLE")))
    stranger = token("CUSTOMER", vins=["1FMCU9GDXMUA00000"])

    with capture_logs() as logs:
        response = client.get(f"/api/v1/vehicles/{telemetry['vin']}/risk", headers=bearer(stranger))

    assert response.status_code == 404
    assert any(item["event"] == "authz.object.denied" for item in logs)


def test_owner_and_staff_can_read_vehicle_risk(client, telemetry, token):
    client.post("/api/v1/telemetry", json=telemetry, headers=bearer(token("VEHICLE")))
    owner = token("CUSTOMER", vins=[telemetry["vin"].lower()])
    path = f"/api/v1/vehicles/{telemetry['vin']}/risk"

    assert client.get(path, headers=bearer(owner)).status_code == 200
    assert client.get(path, headers=bearer(token("ADVISER"))).status_code == 200
