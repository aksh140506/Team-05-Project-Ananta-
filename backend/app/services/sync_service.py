import hashlib
import hmac
import json
from typing import Dict, List
from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from app.core.config import Settings
from app.db.models import AuditLogORM, UpdatePackageORM
from app.schemas.contracts import PublishSyncPackageRequest, SignedUpdatePackageDto


def canonical_payload(
    package_id: str,
    district_code: str,
    table_name: str,
    version: int,
    rows_payload_json: str,
) -> str:
    """Matches DeltaSyncVerifier.canonicalPayload in Android Kotlin 1:1."""
    return f"{package_id}|{district_code}|{table_name}|{version}|{rows_payload_json}"


def sign_update_package(
    package_id: str,
    district_code: str,
    table_name: str,
    version: int,
    rows_payload_json: str,
    settings: Settings,
) -> str:
    payload_bytes = canonical_payload(
        package_id, district_code, table_name, version, rows_payload_json
    ).encode("utf-8")
    if settings.is_hmac_key_configured:
        mac = hmac.new(
            settings.sync_verify_key.encode("utf-8"),
            payload_bytes,
            hashlib.sha256,
        ).hexdigest()
        return f"hmac:{mac}"
    digest = hashlib.sha256(payload_bytes).hexdigest()
    return f"sha256:{digest}"


def fetch_delta_packages(
    db: Session,
    district_code: str,
    since_versions: Dict[str, int],
    settings: Settings,
) -> List[SignedUpdatePackageDto]:
    packages = (
        db.query(UpdatePackageORM)
        .filter(UpdatePackageORM.district_code == district_code)
        .order_by(UpdatePackageORM.version.asc())
        .all()
    )
    out: List[SignedUpdatePackageDto] = []
    for pkg in packages:
        client_ver = since_versions.get(pkg.table_name, 0)
        if pkg.version > client_ver:
            sig = sign_update_package(
                pkg.package_id,
                pkg.district_code,
                pkg.table_name,
                pkg.version,
                pkg.rows_payload_json,
                settings,
            )
            out.append(
                SignedUpdatePackageDto(
                    package_id=pkg.package_id,
                    district_code=pkg.district_code,
                    table_name=pkg.table_name,
                    version=pkg.version,
                    rows_payload_json=pkg.rows_payload_json,
                    signature=sig,
                )
            )
    return out


def publish_signed_package(
    db: Session,
    req: PublishSyncPackageRequest,
    actor_uid: str,
    actor_role: str,
    settings: Settings,
) -> SignedUpdatePackageDto:
    try:
        parsed = json.loads(req.rows_payload_json)
        if not isinstance(parsed, list):
            raise ValueError("rows_payload_json must be a JSON array.")
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Invalid rows_payload_json: {exc}",
        ) from exc

    existing = db.query(UpdatePackageORM).filter(UpdatePackageORM.package_id == req.package_id).first()
    if existing is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=f"Package '{req.package_id}' already exists.",
        )

    pkg = UpdatePackageORM(
        package_id=req.package_id,
        district_code=req.district_code,
        table_name=req.table_name,
        version=req.version,
        rows_payload_json=req.rows_payload_json,
        created_by_uid=actor_uid,
    )
    db.add(pkg)
    db.add(
        AuditLogORM(
            actor_uid=actor_uid,
            actor_role=actor_role,
            action="PUBLISH_UPDATE_PACKAGE",
            target_id=req.package_id,
            details=f"table={req.table_name}, district={req.district_code}, version={req.version}",
        )
    )
    db.commit()

    sig = sign_update_package(
        pkg.package_id,
        pkg.district_code,
        pkg.table_name,
        pkg.version,
        pkg.rows_payload_json,
        settings,
    )
    return SignedUpdatePackageDto(
        package_id=pkg.package_id,
        district_code=pkg.district_code,
        table_name=pkg.table_name,
        version=pkg.version,
        rows_payload_json=pkg.rows_payload_json,
        signature=sig,
    )
