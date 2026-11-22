import math
from datetime import datetime, timezone
from typing import List, Optional
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.core.config import Settings
from app.core.security import sanitize_text_input
from app.db.models import AuditLogORM, FacilityORM
from app.schemas.contracts import RemoteFacilityDto


def haversine_meters(lat1: float, lon1: float, lat2: float, lon2: float) -> int:
    r = 6_371_000.0
    p1 = math.radians(lat1)
    p2 = math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(max(0.0, 1.0 - a)))
    return int(round(r * c))


def facility_to_dto(fac: FacilityORM, origin_lat: float, origin_lon: float) -> RemoteFacilityDto:
    return RemoteFacilityDto(
        id=fac.id,
        name_en=fac.name_en,
        name_hi=fac.name_hi,
        bank_name=fac.bank_name,
        facility_type=fac.facility_type,
        village_or_area=fac.village_or_area,
        district_code=fac.district_code,
        distance_meters=haversine_meters(origin_lat, origin_lon, fac.lat, fac.lon),
        lat=fac.lat,
        lon=fac.lon,
        is_open=fac.is_open,
        cash_status=fac.cash_status,
        reports_count=fac.reports_count,
        verified_date=fac.verified_date,
    )


def find_nearby_facilities(
    db: Session,
    settings: Settings,
    lat: float,
    lon: float,
    radius_km: int,
    district_code: Optional[str],
) -> List[RemoteFacilityDto]:
    radius_m = max(1, radius_km) * 1000

    # Dual-mode geospatial query: PostGIS ST_DWithin on PostgreSQL, parameterized Haversine on SQLite
    if settings.is_postgres:
        try:
            stmt = text(
                """
                SELECT id
                FROM facilities
                WHERE is_active = TRUE
                  AND (:district IS NULL OR district_code = :district)
                  AND ST_DWithin(
                        ST_SetSRID(ST_MakePoint(lon, lat), 4326)::geography,
                        ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                        :radius_m
                  )
                """
            )
            matched_ids = [
                row[0]
                for row in db.execute(
                    stmt,
                    {
                        "lat": lat,
                        "lon": lon,
                        "radius_m": radius_m,
                        "district": district_code or None,
                    },
                ).fetchall()
            ]
            if matched_ids:
                rows = db.query(FacilityORM).filter(FacilityORM.id.in_(matched_ids)).all()
                dtos = [facility_to_dto(r, lat, lon) for r in rows]
                return sorted(dtos, key=lambda item: item.distance_meters)
        except Exception:
            # Gracefully fall through to ORM Haversine if PostGIS extension isn't active yet
            pass

    query = db.query(FacilityORM).filter(FacilityORM.is_active.is_(True))
    if district_code:
        district_rows = query.filter(FacilityORM.district_code == district_code).all()
        rows = district_rows if district_rows else query.all()
    else:
        rows = query.all()

    dtos: List[RemoteFacilityDto] = []
    for fac in rows:
        dist = haversine_meters(lat, lon, fac.lat, fac.lon)
        if radius_km <= 0 or dist <= radius_m or (district_code and fac.district_code == district_code):
            dtos.append(
                RemoteFacilityDto(
                    id=fac.id,
                    name_en=fac.name_en,
                    name_hi=fac.name_hi,
                    bank_name=fac.bank_name,
                    facility_type=fac.facility_type,
                    village_or_area=fac.village_or_area,
                    district_code=fac.district_code,
                    distance_meters=dist,
                    lat=fac.lat,
                    lon=fac.lon,
                    is_open=fac.is_open,
                    cash_status=fac.cash_status,
                    reports_count=fac.reports_count,
                    verified_date=fac.verified_date,
                )
            )
    return sorted(dtos, key=lambda item: item.distance_meters)


def verify_facility_record(
    db: Session,
    facility_id: str,
    cash_status: str,
    is_open: bool,
    notes: str,
    actor_uid: str,
    actor_role: str,
) -> Optional[FacilityORM]:
    fac = db.query(FacilityORM).filter(FacilityORM.id == facility_id).first()
    if fac is None:
        return None

    today_str = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    safe_notes = sanitize_text_input(notes, max_len=250)

    fac.cash_status = cash_status
    fac.is_open = is_open
    fac.verified_date = today_str
    fac.verified_by_uid = actor_uid
    fac.reports_count = (fac.reports_count or 0) + 1

    db.add(
        AuditLogORM(
            actor_uid=actor_uid,
            actor_role=actor_role,
            action="VERIFY_FACILITY",
            target_id=facility_id,
            details=f"cash_status={cash_status}, is_open={is_open}, notes={safe_notes}"[:500],
        )
    )
    db.commit()
    db.refresh(fac)
    return fac
