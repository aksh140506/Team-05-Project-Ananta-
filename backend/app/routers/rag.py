from typing import Optional
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.core.security import AuthenticatedUser, get_optional_user
from app.db.database import get_db
from app.schemas.contracts import RagAskRequest, RagAskResponse
from app.services.rag_service import answer_rag_query

router = APIRouter(prefix="/v1/rag", tags=["Grounded RAG Assistant"])


@router.post(
    "/ask",
    response_model=RagAskResponse,
    summary="Answer rural banking question using verified circulars + safety guardrails",
)
def ask_rag(
    payload: RagAskRequest,
    db: Session = Depends(get_db),
    _: Optional[AuthenticatedUser] = Depends(get_optional_user),
):
    return answer_rag_query(db, payload)
