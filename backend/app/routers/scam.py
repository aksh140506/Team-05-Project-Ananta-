from typing import Optional
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.core.security import AuthenticatedUser, get_optional_user
from app.db.database import get_db
from app.schemas.contracts import RemoteScamCheckRequest, RemoteScamCheckResponse
from app.services.scam_service import evaluate_scam_snippet

router = APIRouter(prefix="/v1/scam", tags=["ScamGuard"])


@router.post(
    "/check",
    response_model=RemoteScamCheckResponse,
    summary="Check SMS, call claim, or URL against verified rural banking scam rules",
)
def check_scam(
    payload: RemoteScamCheckRequest,
    db: Session = Depends(get_db),
    _: Optional[AuthenticatedUser] = Depends(get_optional_user),
):
    return evaluate_scam_snippet(db, payload.snippet, payload.lang)
