from fastapi import APIRouter, Depends
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.core.config import Settings, get_settings
from app.db.database import get_db

router = APIRouter(tags=["Health"])


@router.get("/", summary="Service metadata")
def root(settings: Settings = Depends(get_settings)):
    return {
        "service": settings.app_name,
        "version": settings.app_version,
        "docs": "/docs",
        "openapi": "/openapi.json",
        "database_mode": "postgresql_postgis" if settings.is_postgres else "sqlite_local",
    }


@router.get("/health", summary="Liveness & database connectivity check")
def health_check(
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
):
    db.execute(text("SELECT 1"))
    return {
        "status": "ok",
        "database_mode": "postgresql_postgis" if settings.is_postgres else "sqlite_local",
        "hmac_signing": "configured" if settings.is_hmac_key_configured else "sha256_fallback",
    }
