from contextlib import asynccontextmanager
import logging
from fastapi import FastAPI, HTTPException, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.core.config import Settings, get_settings
from app.core.logging_config import RequestTimingLoggingMiddleware, configure_logging
from app.core.security import RateLimitMiddleware
from app.db.database import Base, SessionLocal, engine
from app.db.seed import seed_database_if_empty
from app.routers import auth, facilities, health, rag, scam, sync

logger = logging.getLogger("banksathi.main")


def create_app(settings_override: Settings | None = None) -> FastAPI:
    configure_logging()
    settings = settings_override or get_settings()

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        Base.metadata.create_all(bind=engine)
        db = SessionLocal()
        try:
            seed_database_if_empty(db)
        finally:
            db.close()
        logger.info(
            "BankSathi backend ready (database_mode=%s)",
            "postgresql_postgis" if settings.is_postgres else "sqlite_local",
        )
        yield

    app = FastAPI(
        title=settings.app_name,
        version=settings.app_version,
        description=(
            "BankSathi offline-first rural banking backend: grounded RAG circulars, "
            "dual-mode spatial facility lookup (SQLite local / PostgreSQL+PostGIS), "
            "HMAC-SHA256 delta-sync packages, ScamGuard rule checks, and Firebase RBAC."
        ),
        lifespan=lifespan,
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_credentials=True,
        allow_methods=["GET", "POST", "OPTIONS"],
        allow_headers=["Authorization", "Content-Type", "X-Firebase-AppCheck"],
    )
    app.add_middleware(RateLimitMiddleware, max_requests_per_min=settings.rate_limit_per_min)
    app.add_middleware(RequestTimingLoggingMiddleware)

    @app.exception_handler(RequestValidationError)
    async def validation_exception_handler(_: Request, exc: RequestValidationError):
        return JSONResponse(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            content={
                "error": "validation_error",
                "detail": "Request validation failed. Please check field types and bounds.",
                "fields": [
                    {"loc": ".".join(str(x) for x in err.get("loc", [])), "msg": err.get("msg")}
                    for err in exc.errors()
                ],
            },
        )

    @app.exception_handler(HTTPException)
    async def http_exception_handler(_: Request, exc: HTTPException):
        return JSONResponse(
            status_code=exc.status_code,
            content={"error": "http_error", "detail": exc.detail},
            headers=exc.headers,
        )

    @app.exception_handler(Exception)
    async def unhandled_exception_handler(_: Request, exc: Exception):
        logger.exception("Unhandled error in BankSathi API: %s", exc)
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={
                "error": "internal_server_error",
                "detail": "An unexpected server error occurred. Please retry.",
            },
        )

    app.include_router(health.router)
    app.include_router(auth.router)
    app.include_router(rag.router)
    app.include_router(facilities.router)
    app.include_router(sync.router)
    app.include_router(scam.router)

    return app


app = create_app()
