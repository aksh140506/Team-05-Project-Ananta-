from typing import Optional
from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session

from app.core.config import Settings, get_settings
from app.core.security import AuthenticatedUser, ROLE_ADMIN, get_optional_user, require_role
from app.db.database import get_db
from app.schemas.contracts import (
    DeltaSyncRequest,
    DeltaSyncResponse,
    PublishSyncPackageRequest,
    SignedUpdatePackageDto,
)
from app.services.sync_service import fetch_delta_packages, publish_signed_package

router = APIRouter(tags=["Signed Delta-Sync"])


@router.post(
    "/v1/sync/delta",
    response_model=DeltaSyncResponse,
    summary="Pull cryptographically signed versioned district delta updates",
)
def pull_delta_sync(
    payload: DeltaSyncRequest,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
    _: Optional[AuthenticatedUser] = Depends(get_optional_user),
):
    packages = fetch_delta_packages(
        db=db,
        district_code=payload.district_code,
        since_versions=payload.since_versions,
        settings=settings,
    )
    return DeltaSyncResponse(packages=packages)


@router.post(
    "/v1/admin/sync/publish",
    response_model=SignedUpdatePackageDto,
    status_code=status.HTTP_201_CREATED,
    summary="Publish & sign a new versioned UPDATE_PACKAGE (ADMIN only)",
)
def admin_publish_package(
    payload: PublishSyncPackageRequest,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
    user: AuthenticatedUser = Depends(require_role(ROLE_ADMIN)),
):
    return publish_signed_package(
        db=db,
        req=payload,
        actor_uid=user.uid,
        actor_role=user.role,
        settings=settings,
    )
