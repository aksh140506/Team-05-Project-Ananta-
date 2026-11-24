from typing import Optional
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from app.core.config import Settings, get_settings
from app.core.security import (
    AuthenticatedUser,
    ROLE_ADMIN,
    ROLE_VERIFIER,
    get_optional_user,
    require_role,
)
from app.db.database import get_db
from app.schemas.contracts import (
    NearbyFacilitiesResponse,
    VerifyFacilityRequest,
    VerifyFacilityResponse,
)
from app.services.geo_service import (
    facility_to_dto,
    find_nearby_facilities,
    verify_facility_record,
)

router = APIRouter(prefix="/v1/facilities", tags=["Facilities & Geospatial"])


@router.get(
    "/nearby",
    response_model=NearbyFacilitiesResponse,
    summary="Query nearest verified Bank Branches, BCs, IPPB Post Offices, and ATMs",
)
def get_nearby_facilities(
    lat: float = Query(..., ge=-90.0, le=90.0),
    lon: float = Query(..., ge=-180.0, le=180.0),
    radius_km: int = Query(default=10, ge=0, le=200),
    district_code: str = Query(default="lucknow_rampur", min_length=2, max_length=64, pattern=r"^[a-zA-Z0-9_-]+$"),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
    _: Optional[AuthenticatedUser] = Depends(get_optional_user),
):
    facilities = find_nearby_facilities(
        db=db,
        settings=settings,
        lat=lat,
        lon=lon,
        radius_km=radius_km,
        district_code=district_code,
    )
    return NearbyFacilitiesResponse(district_code=district_code, facilities=facilities)


@router.post(
    "/verify",
    response_model=VerifyFacilityResponse,
    summary="Officially verify facility cash & open status (VERIFIER or ADMIN only)",
)
def verify_facility(
    payload: VerifyFacilityRequest,
    db: Session = Depends(get_db),
    user: AuthenticatedUser = Depends(require_role(ROLE_VERIFIER, ROLE_ADMIN)),
):
    updated = verify_facility_record(
        db=db,
        facility_id=payload.facility_id,
        cash_status=payload.cash_status,
        is_open=payload.is_open,
        notes=payload.notes,
        actor_uid=user.uid,
        actor_role=user.role,
    )
    if updated is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Facility '{payload.facility_id}' not found.",
        )

    dto = facility_to_dto(updated, updated.lat, updated.lon)
    return VerifyFacilityResponse(
        facility=dto,
        verified_by=user.email or user.uid,
        verified_date=updated.verified_date,
        message=f"Facility '{updated.name_en}' verified cleanly on {updated.verified_date}.",
    )
