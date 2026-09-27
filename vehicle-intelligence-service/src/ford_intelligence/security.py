from dataclasses import dataclass

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from ford_intelligence.config import Settings, get_settings

bearer = HTTPBearer(auto_error=False)


@dataclass(frozen=True, slots=True)
class Principal:
    subject: str
    roles: frozenset[str]


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
        return Principal(subject=claims["sub"], roles=frozenset(claims.get("roles", [])))
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
