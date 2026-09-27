from fastapi import Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel


class ProblemDetails(BaseModel):
    type: str
    title: str
    status: int
    detail: str
    instance: str


def problem_response(
    request: Request, status: int, title: str, detail: str, headers: dict[str, str] | None = None
) -> JSONResponse:
    problem = ProblemDetails(
        type=f"https://ford.example/problems/{status}",
        title=title,
        status=status,
        detail=detail,
        instance=request.url.path,
    )
    return JSONResponse(
        status_code=status,
        content=problem.model_dump(),
        media_type="application/problem+json",
        headers=headers,
    )
