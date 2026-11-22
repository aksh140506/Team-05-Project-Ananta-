from functools import lru_cache
import secrets
from typing import List
from pydantic_settings import BaseSettings, SettingsConfigDict

_EPHEMERAL_RUNTIME_JWT_SECRET = secrets.token_urlsafe(32)


class Settings(BaseSettings):
    app_name: str = "BankSathi Rural Banking & ScamGuard API"
    app_version: str = "1.0.0"
    app_env: str = "development"
    host: str = "0.0.0.0"
    port: int = 8000

    database_url: str = "sqlite:///./banksathi.db"
    sync_verify_key: str = "SYNC_VERIFY_KEY_DEFAULT_VALUE"
    firebase_project_id: str = "gen-lang-client-0766244733"

    # Session JWT settings for Supabase PostgreSQL DB authentication
    jwt_secret_key: str = ""
    jwt_algorithm: str = "HS256"
    jwt_expire_minutes: int = 1440
    supabase_jwt_secret: str = ""

    admin_emails: str = ""
    verifier_emails: str = ""
    firebase_allow_unverified_offline_demo: bool = False
    require_app_check: bool = False

    allowed_origins: str = "http://localhost:8000,http://127.0.0.1:8000,http://10.0.2.2:8000"
    rate_limit_per_min: int = 60

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    @property
    def cors_origins(self) -> List[str]:
        origins = [item.strip() for item in self.allowed_origins.split(",") if item.strip()]
        # Never allow wildcard CORS alongside credentials/auth headers
        return [o for o in origins if o != "*"] or ["http://127.0.0.1:8000"]

    @property
    def admin_email_set(self) -> set[str]:
        return {e.strip().lower() for e in self.admin_emails.split(",") if e.strip()}

    @property
    def verifier_email_set(self) -> set[str]:
        return {e.strip().lower() for e in self.verifier_emails.split(",") if e.strip()}

    @property
    def is_postgres(self) -> bool:
        lower = (self.database_url or "").lower()
        return lower.startswith("postgresql") or lower.startswith("postgres://")

    @property
    def is_hmac_key_configured(self) -> bool:
        k = (self.sync_verify_key or "").strip()
        return bool(k) and not k.endswith("_DEFAULT_VALUE") and not k.startswith("YOUR_")

    @property
    def effective_jwt_secret(self) -> str:
        raw = (self.jwt_secret_key or "").strip()
        if raw and not raw.endswith("_DEFAULT_VALUE") and not raw.startswith("YOUR_"):
            return raw
        return _EPHEMERAL_RUNTIME_JWT_SECRET


@lru_cache
def get_settings() -> Settings:
    return Settings()
