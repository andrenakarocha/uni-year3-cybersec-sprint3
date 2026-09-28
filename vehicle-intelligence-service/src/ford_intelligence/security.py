from dataclasses import dataclass

import jwt
from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from ford_intelligence.config import Settings, get_settings

bearer = HTTPBearer(auto_error=False)


# Equipe e integração entre serviços atendem qualquer veículo; CUSTOMER só os do próprio token.
STAFF_ROLES = frozenset({"ADVISER", "ADMIN", "SERVICE"})


@dataclass(frozen=True, slots=True)
class Principal:
    subject: str
    roles: frozenset[str]
    vins: frozenset[str] = frozenset()

    def can_read_vehicle(self, vin: str) -> bool:
        """Autorização em nível de objeto (OWASP API1:2023): posse do VIN via claim "vins"."""
        return not self.roles.isdisjoint(STAFF_ROLES) or vin.upper() in self.vins


def client_ip(request: Request) -> str:
    # X-Real-IP é sobrescrito pelo gateway; confiável porque o serviço não publica porta.
    fallback = request.client.host if request.client else "unknown"
    return request.headers.get("x-real-ip") or fallback


def authenticated(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer),
    settings: Settings = Depends(get_settings),
) -> Principal:
    if credentials is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="missing bearer token")
    try:
        claims = jwt.decode(
            credentials.credentials,
            settings.jwt_secret,
            algorithms=["HS256"],
            audience="ford-api",
            issuer="ford-zero-touch",
            # PyJWT só valida exp quando presente; sem "require", token sem exp nunca expira.
            options={"require": ["exp", "iat", "sub"]},
        )
        return Principal(
            subject=claims["sub"],
            roles=frozenset(claims.get("roles", [])),
            vins=frozenset(vin.upper() for vin in claims.get("vins", [])),
        )
    except (jwt.PyJWTError, KeyError) as error:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid or expired token"
        ) from error


def require_roles(*allowed: str):
    def authorize(principal: Principal = Depends(authenticated)) -> Principal:
        if principal.roles.isdisjoint(allowed):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN, detail="insufficient permissions"
            )
        return principal

    return authorize
