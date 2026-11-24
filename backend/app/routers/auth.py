from typing import List
from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session

from app.core.config import Settings, get_settings
from app.core.security import AuthenticatedUser, ROLE_ADMIN, get_current_user, require_role
from app.db.database import get_db
from app.schemas.contracts import (
    AuditLogEntryDto,
    AuthLoginRequest,
    AuthMeResponse,
    AuthRegisterRequest,
    AuthTokenResponse,
)
from app.services.auth_service import (
    authenticate_user,
    list_recent_audit_logs,
    permissions_for_role,
    register_user,
)

router = APIRouter(prefix="/v1", tags=["Auth & RBAC"])


@router.post(
    "/auth/register",
    response_model=AuthTokenResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Register a new user in Supabase PostgreSQL DB and return a Bearer JWT",
)
def register(
    payload: AuthRegisterRequest,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
):
    return register_user(db, payload, settings)


@router.post(
    "/auth/login",
    response_model=AuthTokenResponse,
    summary="Authenticate user credentials against Supabase PostgreSQL DB and return a Bearer JWT",
)
def login(
    payload: AuthLoginRequest,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
):
    return authenticate_user(db, payload, settings)


@router.get("/auth/me", response_model=AuthMeResponse, summary="Get authenticated user identity & role")
def get_me(user: AuthenticatedUser = Depends(get_current_user)):
    return AuthMeResponse(
        uid=user.uid,
        email=user.email,
        full_name=user.full_name,
        role=user.role,
        auth_provider=user.auth_mode,
        permissions=permissions_for_role(user.role),
    )


@router.get(
    "/admin/audit-logs",
    response_model=List[AuditLogEntryDto],
    summary="List security & verification audit logs (ADMIN only)",
)
def get_audit_logs(
    limit: int = 50,
    db: Session = Depends(get_db),
    _: AuthenticatedUser = Depends(require_role(ROLE_ADMIN)),
):
    return list_recent_audit_logs(db, limit=max(1, min(limit, 200)))
