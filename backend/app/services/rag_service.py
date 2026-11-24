import re
from typing import List, Optional, Tuple
from sqlalchemy.orm import Session

from app.core.security import is_prompt_injection, mask_sensitive_pii, sanitize_text_input
from app.db.models import CachedFaqORM, VerifiedDocORM
from app.schemas.contracts import RagAskRequest, RagAskResponse

_BANKING_ALLOW_TOKENS = {
    "bank", "cash", "atm", "bc", "mitra", "aeps", "aadhaar", "passbook", "kcc",
    "kisan", "loan", "subsidy", "dbt", "pm-kisan", "pmjdy", "jan", "dhan", "bsbda",
    "balance", "ippb", "post", "office", "branch", "deposit", "transfer", "withdraw",
    "money", "upi", "pin", "otp", "kyc", "scam", "fraud", "receipt", "1930", "14448",
    "khatauni", "pan", "fee", "commission", "pincode", "npci", "seeding", "crop",
    "पैसे", "निकासी", "बैंक", "खाता", "पासबुक", "केसीसी", "लोन", "किस्त", "ठगी",
    "ओटीपी", "डाकघर", "रसीद", "कड्ढ", "केंदर", "किश्त",
}

_HI_DOGRI_MAP = {
    "RBI_BC_BSBDA_01": (
        "₹10,000 तक निकासी के लिए अपने नजदीकी बैंक मित्र (BC केंद्र) पर असली आधार कार्ड लेकर जाएं। RBI नियमों के तहत बैंक मित्र कोई अतिरिक्त शुल्क नहीं ले सकता — हमेशा छपी रसीद लें।",
        "₹10,000 तक्कर कड्ढने आस्तै अपने नेड़े दे बैंक मित्र (BC केंदर) पर असली आधार कार्ड लेई के जाओ। RBI नियमें मुजब बैंक मित्र कोई बद्ध फीस नेईं लेई सकदा — हमेशा छपी रसीद लेओ।",
    ),
    "RBI_KCC_MASTER_01": (
        "RBI नियमों के तहत ₹2,00,000 तक का केसीसी (KCC) कृषि ऋण बिना गारंटी मिल सकता है। असली खतौनी + 2 कॉपी, आधार व पैन की 2 कॉपी, पासबुक और 3 फोटो लेकर बैंक शाखा जाएं।",
        "RBI नियमें मुजब ₹2,00,000 तक्कर दा KCC खेती लोन बिना गारंटी मिल्दा ऐ। असली खतौनी ते 2 कॉपी, आधार-पैन दी 2 कॉपी, पासबुक ते 3 फोटो लेई के बैंक शाखा जाओ।",
    ),
    "RBI_BSBDA_DBT_02": (
        "जन धन (BSBDA) जीरो-बैलेंस खाता है। PM-Kisan या सब्सिडी की किस्त पाने के लिए अपनी बैंक शाखा या डाकघर (IPPB) में आधार कार्ड व पासबुक के साथ NPCI आधार सीडिंग जांचें।",
        "जन धन (BSBDA) जीरो-बैलेंस खाता ऐ। PM-Kisan जां सब्सिडी आस्तै अपनी बैंक शाखा जां डाकघर (IPPB) च आधार कार्ड ते पासबुक लेई के NPCI आधार सीडिंग कराओ।",
    ),
    "NPCI_IPPB_TRANSFER_03": (
        "पैसे सुरक्षित भेजने के लिए नजदीकी डाकघर (IPPB) या बैंक मित्र केंद्र पर आधार कार्ड और प्राप्तकर्ता का खाता/IFSC विवरण लेकर जाएं। हमेशा छपी रसीद अपने पास रखें।",
        "पैसे सुरक्षित भेजने आस्तै नेड़े दे डाकघर (IPPB) जां बैंक मित्र केंदर पर आधार कार्ड ते खाते दा विवरण लेई के जाओ। हमेशा छपी रसीद रक्खो।",
    ),
    "RBI_ZERO_LIABILITY_1930": (
        "कभी भी OTP, UPI पिन या स्क्रीन-शेयरिंग ऐप साझा न करें। ठगी होने पर तुरंत 1930 पर कॉल करें, cybercrime.gov.in पर रिपोर्ट करें और 3 कार्य दिवस के भीतर बैंक को सूचित करें।",
        "कदें बी OTP, UPI पिन जां स्क्रीन-शेयरिंग ऐप नेईं दस्सो। ठगी होए तां फौरन 1930 पर कॉल करो ते 3 दिनें दे अंदर बैंक गी दस्सो।",
    ),
}


def _tokenize(text: str) -> set[str]:
    return {t for t in re.split(r"[^\w\u0900-\u097F]+", (text or "").lower()) if len(t) >= 2}


def _abstain_response(reason_en: str, reason_hi: str) -> RagAskResponse:
    return RagAskResponse(
        answer_en=reason_en,
        answer_hi=reason_hi,
        source_id="ABSTAIN_SAFETY_PATH",
        verified_on="2026-11-23",
        abstained=True,
        recommended_task=None,
        suggested_amount_inr=0,
    )


def answer_rag_query(db: Session, payload: RagAskRequest) -> RagAskResponse:
    safe_query = mask_sensitive_pii(sanitize_text_input(payload.query, max_len=600))

    if is_prompt_injection(safe_query):
        return _abstain_response(
            "I can only follow verified BankSathi rural banking and scam-safety rules. Please ask about cash withdrawal, KCC loans, DBT passbook, or suspicious messages.",
            "मैं केवल सत्यापित बैंकसाथी ग्रामीण बैंकिंग और सुरक्षा नियमों का पालन करता हूँ। कृपया निकासी, केसीसी लोन, पासबुक या ठगी जांच से जुड़ा सवाल पूछें।",
        )

    query_tokens = _tokenize(safe_query)
    has_banking_signal = bool(query_tokens & _BANKING_ALLOW_TOKENS) or bool(re.search(r"\d{2,}", safe_query))
    if not has_banking_signal and len(safe_query.split()) >= 3:
        return _abstain_response(
            "I am not sure about this outside rural banking. Please visit your nearest verified Bank Branch or call 14448 / 1930 for official guidance.",
            "यह ग्रामीण बैंकिंग विषय से बाहर है, इसलिए मैं पक्का उत्तर नहीं दे सकता। कृपया अपनी नजदीकी बैंक शाखा जाएं या 14448 / 1930 पर संपर्क करें।",
        )

    docs = db.query(VerifiedDocORM).all()
    best_doc: Optional[VerifiedDocORM] = None
    best_score = 0

    for doc in docs:
        doc_tokens = _tokenize(f"{doc.title} {doc.topic_tags} {doc.content}")
        score = len(query_tokens & doc_tokens)
        if score > best_score:
            best_score = score
            best_doc = doc

    # Optional ChromaDB enhancement hook when installed locally, gracefully backed by verified SQLite/Postgres docs
    if best_doc is None or best_score == 0:
        faqs = db.query(CachedFaqORM).all()
        best_faq: Optional[CachedFaqORM] = None
        faq_score = 0
        for faq in faqs:
            sc = len(query_tokens & _tokenize(f"{faq.question} {faq.answer}"))
            if sc > faq_score:
                faq_score = sc
                best_faq = faq
        if best_faq is not None and faq_score > 0:
            hi_pair = _HI_DOGRI_MAP.get(best_faq.source_id)
            hi_text = hi_pair[1] if (payload.lang == "doi" and hi_pair) else (hi_pair[0] if hi_pair else best_faq.answer)
            return RagAskResponse(
                answer_en=best_faq.answer,
                answer_hi=hi_text,
                source_id=best_faq.source_id,
                verified_on=best_faq.verified_on,
                abstained=False,
                recommended_task=None,
                suggested_amount_inr=0,
            )

        return _abstain_response(
            "I am not sure of an exact verified rule for that query. Check at your nearest bank branch or dial RBI/Cyber helpline 14448 / 1930.",
            "इस प्रश्न के लिए सत्यापित नियम उपलब्ध नहीं है। कृपया नजदीकी बैंक शाखा में पूछें या 14448 / 1930 पर कॉल करें।",
        )

    hi_text, doi_text = _HI_DOGRI_MAP.get(best_doc.source_id, (best_doc.content, best_doc.content))
    localized_hi = doi_text if payload.lang == "doi" else hi_text

    return RagAskResponse(
        answer_en=best_doc.content,
        answer_hi=localized_hi,
        source_id=best_doc.source_id,
        verified_on=best_doc.verified_on,
        abstained=False,
        recommended_task=best_doc.recommended_task,
        suggested_amount_inr=best_doc.suggested_amount_inr,
    )
