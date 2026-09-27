import pytest
from pydantic import ValidationError

from ford_intelligence.config import Settings


def test_settings_require_jwt_secret(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("JWT_SECRET", raising=False)
    with pytest.raises(ValidationError, match="jwt_secret"):
        Settings(_env_file=None)


def test_settings_reject_secret_shorter_than_256_bits() -> None:
    with pytest.raises(ValidationError, match="at least 32 characters"):
        Settings(jwt_secret="short-secret", _env_file=None)
