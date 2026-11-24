import re
from sqlalchemy.orm import Session

from app.core.security import mask_sensitive_pii, sanitize_text_input
from app.db.models import ScamRuleORM
from app.schemas.contracts import RemoteScamCheckResponse


def evaluate_scam_snippet(db: Session, snippet: str, lang: str = "en") -> RemoteScamCheckResponse:
    cleaned = mask_sensitive_pii(sanitize_text_input(snippet, max_len=800))
    lower = cleaned.lower()

    rules = db.query(ScamRuleORM).order_by(ScamRuleORM.severity.desc()).all()
    for rule in rules:
        if re.search(rule.trigger_pattern, lower, re.IGNORECASE):
            risk = "HIGH_RISK" if rule.severity >= 3 else "CAUTION"
            return RemoteScamCheckResponse(
                risk_level=risk,
                explanation_en=rule.explanation_en,
                explanation_hi=rule.explanation_hi,
                action_tip_en=rule.action_tip_en,
                action_tip_hi=rule.action_tip_hi,
            )

    return RemoteScamCheckResponse(
        risk_level="SAFE",
        explanation_en="No immediate OTP, screen-share, or phishing link trap found. Still never share OTP or PIN with anyone.",
        explanation_hi="इसमें तुरंत कोई OTP, स्क्रीन-शेयरिंग या नकली लिंक का खतरा नहीं दिखा, फिर भी कभी किसी को OTP या पिन न बताएं।",
        action_tip_en="Verify official SMS only on your registered mobile and take a printed receipt for every transaction.",
        action_tip_hi="केवल अपने पंजीकृत मोबाइल पर बैंक SMS जांचें और हर लेनदेन की छपी रसीद जरूर लें।",
    )
