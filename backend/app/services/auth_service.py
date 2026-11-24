from typing import List
import uuid
from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from app.core.config import Settings
from app.core.security import (
    ROLE_ADMIN,
    ROLE_CITIZEN,
    ROLE_VERIFIER,
    create_access_token,
    hash_password,
    resolve_user_role,
    sanitize_text_input,
    verify_password,
)
from app.db.models import AuditLogORM, UserORM, UserRoleORM
from app.schemas.contracts import (
    AuditLogEntryDto,
    AuthLoginRequest,
    AuthMeResponse,
    AuthRegisterRequest,
    AuthTokenResponse,
)


def permissions_for_role(role: str) -> List[str]:
    base = ["rag:ask", "facilities:read", "sync:pull", "scam:check", "facilities:report"]
    if role == ROLE_VERIFIER:
        return base + ["facilities:verify"]
    if role == ROLE_ADMIN:
        return base + ["facilities:verify", "sync:publish", "audit:read"]
    return base


def register_user(db: Session, payload: AuthRegisterRequest, settings: Settings) -> AuthTokenResponse:
    clean_email = payload.email.strip().lower()
    safe_name = sanitize_text_input(payload.full_name, max_len=120)
    if len(safe_name) < 2:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Full name must be at least 2 characters.",
        )

    existing = db.query(UserORM).filter(UserORM.email == clean_email).first()
    if existing is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="An account with this email already exists.",
        )

    if clean_email in settings.admin_email_set:
        assigned_role = ROLE_ADMIN
    elif clean_email in settings.verifier_email_set:
        assigned_role = ROLE_VERIFIER
    else:
        assigned_role = ROLE_CITIZEN

    uid = f"usr_{uuid.uuid4().hex[:20]}"
    user = UserORM(
        uid=uid,
        email=clean_email,
        full_name=safe_name,
        password_hash=hash_password(payload.password),
        role=assigned_role,
        auth_provider="supabase_db",
        is_active=True,
    )
    db.add(user)
    db.add(UserRoleORM(uid=uid, email=clean_email, role=assigned_role))
    db.add(
        AuditLogORM(
            actor_uid=uid,
            actor_role=assigned_role,
            action="REGISTER_USER",
            target_id=uid,
            details=f"Registered {clean_email} via supabase_db",
        )
    )
    db.commit()

    token, expires_in = create_access_token(
        uid=uid,
        email=clean_email,
        role=assigned_role,
        settings=settings,
        full_name=safe_name,
    )
    return AuthTokenResponse(
        access_token=token,
        expires_in=expires_in,
        user=AuthMeResponse(
            uid=uid,
            email=clean_email,
            full_name=safe_name,
            role=assigned_role,
            auth_provider="supabase_db_jwt",
            permissions=permissions_for_role(assigned_role),
        ),
    )


def authenticate_user(db: Session, payload: AuthLoginRequest, settings: Settings) -> AuthTokenResponse:
    clean_email = payload.email.strip().lower()
    user = db.query(UserORM).filter(UserORM.email == clean_email).first()
    if user is None or not verify_password(payload.password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid email or password.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    if not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Account is deactivated.",
        )

    role = resolve_user_role(user.uid, user.email, db, settings)
    if user.role != role:
        user.role = role

    db.add(
        AuditLogORM(
            actor_uid=user.uid,
            actor_role=role,
            action="LOGIN_USER",
            target_id=user.uid,
            details=f"Signed in {clean_email} via supabase_db",
        )
    )
    db.commit()

    token, expires_in = create_access_token(
        uid=user.uid,
        email=user.email,
        role=role,
        settings=settings,
        full_name=user.full_name,
    )
    return AuthTokenResponse(
        access_token=token,
        expires_in=expires_in,
        user=AuthMeResponse(
            uid=user.uid,
            email=user.email,
            full_name=user.full_name,
            role=role,
            auth_provider="supabase_db_jwt",
            permissions=permissions_for_role(role),
        ),
    )


def list_recent_audit_logs(db: Session, limit: int = 50) -> List[AuditLogEntryDto]:
    rows = db.query(AuditLogORM).order_by(AuditLogORM.id.desc()).limit(limit).all()
    return [
        AuditLogEntryDto(
            id=row.id,
            actor_uid=row.actor_uid,
            actor_role=row.actor_role,
            action=row.action,
            target_id=row.target_id,
            details=row.details,
            created_at=row.created_at.isoformat() if row.created_at else "",
        )
        for row in rows
    ]
