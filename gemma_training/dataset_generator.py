#!/usr/bin/env python3
"""
BankSathi Production Gemma-2B Dataset Generator (1,000 - 3,000+ Stratified Rows).

Generates schema-locked SFT datasets matching the exact prompts and JSON contracts
in HybridAiService.kt (queryGemmaLocal & analyzeScamWithGemmaLocal).

Key Production Quality Rules Implemented:
  1. Stratified class balance (~22% WITHDRAW, ~14% TRANSFER, ~14% PASSBOOK_DBT,
     ~14% LOAN_KCC, ~20% SCAM_GUARD [Assistant + SMS/Call], ~16% SAFE_ABSTENTION).
  2. Multi-register linguistic coverage: Plain English, Devanagari Hindi, and
     Romanized Hinglish / Whisper-tiny ASR noisy transcripts.
  3. Exact RBI/NPCI threshold boundary cases (Rs 9,999 vs 10,000 vs 10,001;
     KCC Rs 2,00,000 collateral-free cap vs > Rs 2,00,000 branch security check).
  4. Dual prompt grounding: Verified Context RAG snippets ([SOURCE_ID | Verified])
     plus fallback Core-Rule prompts.
  5. Strict normalized query deduplication & zero train/eval prompt leakage.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import random
import re
from pathlib import Path
from typing import Any

ASSISTANT_SYSTEM_TEMPLATE = """You are BankSathi, an offline-first rural Indian banking assistant.
Rephrase ONLY from the Verified Context below. Never invent facts. If unsure, set abstained=true and task="NONE".
Return JSON ONLY with keys: replyEn, replyHi, task (WITHDRAW|TRANSFER|PASSBOOK_DBT|LOAN_KCC|NONE), suggestedAmountInr (integer), isScamAlert (boolean), abstained (boolean).
Core Rules: Cash <= 10000 -> BC_POINT; > 10000 -> BRANCH/ATM; KCC Loan up to 2L collateral-free -> BRANCH; Transfer -> IPPB/BC_POINT; OTP/KYC scam -> 1930.
Verified Context: {context}
User query: "{query}\""""

SCAM_SYSTEM_TEMPLATE = """Analyze this banking SMS or call scenario for rural fraud in India. Advise calling 1930 and reporting within 3 days on HIGH_RISK.
Return JSON ONLY with keys: riskLevel (SAFE|CAUTION|HIGH_RISK), explanationEn, explanationHi, actionTipEn, actionTipHi.
Input: "{snippet}\""""

VALID_TASKS = {"WITHDRAW", "TRANSFER", "PASSBOOK_DBT", "LOAN_KCC", "NONE"}
VALID_RISKS = {"SAFE", "CAUTION", "HIGH_RISK"}
DEVANAGARI_RE = re.compile(r"[\u0900-\u097F]")

# Verified RBI / NPCI Context Snippets aligned with CACHED_FAQ in LocalDatabase.kt
CTX_DEFAULT = "Use only core RBI/BC rules above."
CTX_BC_FEE = "[RBI_BC_BSBDA_01 | Verified 2026-11-14] No. Under RBI Business Correspondent rules, a Bank Mitra cannot demand extra fees or commission from customers. Always take a printed receipt."
CTX_KCC = "[RBI_KCC_MASTER_01 | Verified 2026-11-14] Under RBI guidelines, Kisan Credit Card (KCC) crop and allied loans up to Rs 2,00,000 do not require collateral security. Visit a bank branch with Khatauni and KYC."
CTX_BSBDA = "[RBI_BSBDA_DBT_02 | Verified 2026-11-14] No. PMJDY / BSBDA accounts are zero-balance accounts with unlimited deposits and at least 4 free withdrawals per month."
CTX_ZERO_LIAB = "[RBI_ZERO_LIABILITY_1930 | Verified 2026-11-14] Call 1930 immediately, report on cybercrime.gov.in, and inform your bank within 3 working days to claim RBI Zero Liability protection."
CTX_TRANSFER = "[RBI_BC_TRANSFER_03 | Verified 2026-11-14] Domestic deposits and transfers are supported at Post Office (IPPB) and Bank Mitra centers with Aadhaar, receiver account/IFSC, and printed receipt."


def normalize_key(text: str) -> str:
    return re.sub(r"[\s,?.!।\"'`~:;()\-_/]+", " ", text.lower()).strip()


def format_gemma_turn(user_prompt: str, assistant_json: dict[str, Any]) -> str:
    payload = json.dumps(assistant_json, ensure_ascii=False, separators=(",", ":"))
    return (
        f"<start_of_turn>user\n{user_prompt}<end_of_turn>\n"
        f"<start_of_turn>model\n{payload}<end_of_turn>"
    )


def build_assistant_record(
    query: str,
    context: str,
    target: dict[str, Any],
    category: str,
    register: str,
) -> dict[str, Any]:
    clean_q = query.replace('"', "'").strip()
    prompt = ASSISTANT_SYSTEM_TEMPLATE.format(
        context=context or CTX_DEFAULT,
        query=clean_q,
    )
    return {
        "id": "",
        "type": "assistant",
        "category": category,
        "register": register,
        "dedup_key": f"assistant::{normalize_key(clean_q)}",
        "prompt": prompt,
        "completion": json.dumps(target, ensure_ascii=False, separators=(",", ":")),
        "text": format_gemma_turn(prompt, target),
        "target": target,
    }


def build_scam_record(
    snippet: str,
    target: dict[str, Any],
    category: str,
    register: str,
) -> dict[str, Any]:
    clean_s = snippet.replace('"', "'").strip()
    prompt = SCAM_SYSTEM_TEMPLATE.format(snippet=clean_s)
    return {
        "id": "",
        "type": "scam_guard",
        "category": category,
        "register": register,
        "dedup_key": f"scam::{normalize_key(clean_s)}",
        "prompt": prompt,
        "completion": json.dumps(target, ensure_ascii=False, separators=(",", ":")),
        "text": format_gemma_turn(prompt, target),
        "target": target,
    }


def apply_whisper_asr_noise(query: str, rng: random.Random) -> str:
    """Apply realistic rural Whisper-tiny ASR surface variations without altering meaning."""
    variants = [
        lambda s: s.rstrip("?।!."),
        lambda s: s.lower().rstrip("?।!."),
        lambda s: re.sub(r"\bRs\.?\s*", "rupees ", s, flags=re.IGNORECASE),
        lambda s: s.replace("Aadhaar", "aadhar").replace("passbook", "pasbuk"),
        lambda s: s.replace("KCC", "kcc card").replace("Branch", "bank branch"),
        lambda s: f"bhaiya {s[0].lower() + s[1:]}" if len(s) > 4 and s.isascii() else s,
        lambda s: f"sir please tell {s[0].lower() + s[1:]}" if len(s) > 4 and s.isascii() else s,
    ]
    fn = rng.choice(variants)
    return fn(query).strip()


def generate_withdraw_le_10k_pool(rng: random.Random) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    amounts = [300, 500, 800, 1000, 1500, 2000, 2500, 3000, 4000, 5000, 6000, 7500, 8000, 9000, 9500, 9999, 10000]
    contexts = [CTX_DEFAULT, CTX_BC_FEE]
    templates = [
        ("en", "I need to withdraw Rs {amt} cash urgently. Where should I go?"),
        ("en", "Can I withdraw {amt} rupees using Aadhaar thumbprint at the village BC point?"),
        ("en", "What documents should I carry to take out Rs {amt} from Bank Mitra?"),
        ("en", "Does Bank Mitra charge any extra fee to withdraw Rs {amt}?"),
        ("hi", "मुझे {amt} रुपये नकद निकालने हैं, कौन से कागज लेकर कहाँ जाऊं?"),
        ("hi", "गाँव के बैंक मित्र केंद्र से {amt} रुपये अंगूठा लगाकर निकालने के लिए क्या चाहिए?"),
        ("hi", "क्या {amt} रुपये निकालने पर बैंक मित्र अलग से फीस ले सकता है?"),
        ("hi", "अर्जेंट {amt} रुपये नकद चाहिए, बीसी केंद्र जाऊं या मुख्य शाखा?"),
        ("hinglish", "Bhaiya mujhe {amt} rupaye nikalne hain, BC point par mil jayega kya?"),
        ("hinglish", "{amt} rs cash nikalna hai aadhaar se, kitna fee lagega?"),
        ("hinglish", "gaon ke bank mitra se {amt} rupaye nikalne ke liye kya kagaz le jaun"),
        ("hinglish", "mere pass atm card nahi hai sirf aadhaar se {amt} rs nikal sakte hain kya"),
    ]
    for amt in amounts:
        for reg, tmpl in templates:
            q = tmpl.format(amt=amt)
            ctx = rng.choice(contexts)
            target = {
                "replyEn": f"For Rs {amt}, visit your nearest Bank Mitra (BC Point) with original Aadhaar and phone. No extra fee is allowed under RBI rules; always take a printed receipt.",
