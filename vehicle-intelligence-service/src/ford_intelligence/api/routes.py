from http import HTTPStatus

from fastapi import APIRouter, Depends, HTTPException, Request, status

from ford_intelligence.api.problems import ProblemDetails
from ford_intelligence.application.services import IntelligenceService
from ford_intelligence.domain.models import Recommendation, TelemetryInput
from ford_intelligence.security import Principal, require_roles

router = APIRouter(
    prefix="/api/v1",
    responses={
        code: {
            "description": HTTPStatus(code).phrase,
            "content": {"application/problem+json": {"schema": ProblemDetails.model_json_schema()}},
        }
        for code in (401, 403, 404, 422, 500)
    },
)


def get_service(request: Request) -> IntelligenceService:
    return request.app.state.intelligence_service


@router.post("/telemetry", response_model=Recommendation, status_code=status.HTTP_202_ACCEPTED)
async def ingest_telemetry(
    payload: TelemetryInput,
    _: Principal = Depends(require_roles("VEHICLE", "ADMIN")),
    service: IntelligenceService = Depends(get_service),
) -> Recommendation:
    return await service.evaluate(payload)


@router.post("/recommendations/evaluate", response_model=Recommendation)
async def evaluate(
    payload: TelemetryInput,
    _: Principal = Depends(require_roles("ADVISER", "ADMIN", "SERVICE")),
    service: IntelligenceService = Depends(get_service),
) -> Recommendation:
    return await service.evaluate(payload)


@router.get("/vehicles/{vin}/risk", response_model=Recommendation)
async def latest_risk(
    vin: str,
    _: Principal = Depends(require_roles("CUSTOMER", "ADVISER", "ADMIN", "SERVICE")),
    service: IntelligenceService = Depends(get_service),
) -> Recommendation:
    result = await service.latest(vin)
    if result is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="vehicle risk not found")
    return result
