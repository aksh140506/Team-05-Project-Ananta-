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
                "replyHi": f"{amt} रुपये निकालने के लिए असली आधार कार्ड और फोन लेकर पास के बैंक मित्र केंद्र जाएं। RBI नियम से कोई अलग शुल्क नहीं है; छपी रसीद जरूर लें।",
                "task": "WITHDRAW",
                "suggestedAmountInr": amt,
                "isScamAlert": False,
                "abstained": False,
            }
            records.append(build_assistant_record(q, ctx, target, "withdraw_bc_le_10k", reg))
            if reg in {"en", "hinglish"}:
                noisy = apply_whisper_asr_noise(q, rng)
                if normalize_key(noisy) != normalize_key(q):
                    records.append(build_assistant_record(noisy, ctx, target, "withdraw_bc_le_10k", f"{reg}_asr"))
    return records


def generate_withdraw_gt_10k_pool(rng: random.Random) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    amounts = [10001, 11000, 12000, 15000, 18000, 20000, 25000, 30000, 35000, 40000, 45000, 50000, 75000, 100000]
    templates = [
        ("en", "How do I withdraw Rs {amt} cash for tractor or crop harvest?"),
        ("en", "Can a village BC agent give me {amt} rupees in one AePS withdrawal?"),
        ("en", "What should I pack to take out Rs {amt} from the bank safely?"),
        ("en", "I need {amt} rupees cash today. Should I go to BC point or Bank Branch?"),
        ("hi", "मुझे {amt} रुपये नकद निकालने हैं, क्या बैंक मित्र एक बार में दे देगा?"),
        ("hi", "फसल कटाई के लिए {amt} रुपये चाहिए, बैंक शाखा जाऊं या एटीएम?"),
        ("hi", "{amt} रुपये निकालने के लिए कौन से दस्तावेज साथ ले जाने होंगे?"),
        ("hinglish", "Mujhe {amt} rupaye nikalne hain bank se, BC centre dega ya branch jana hoga?"),
        ("hinglish", "khata se {amt} rs cash chahiye kya passbook aur atm card le kar branch jaun"),
        ("hinglish", "kheti ke kaam ke liye {amt} rupaye chahiye kahan se jaldi milega"),
    ]
    for amt in amounts:
        for reg, tmpl in templates:
            q = tmpl.format(amt=amt)
            target = {
                "replyEn": f"Rs {amt} exceeds the Rs 10,000 BC limit. Visit your Bank Branch or ATM with your passbook, original Aadhaar, and debit card.",
                "replyHi": f"{amt} रुपये बीसी केंद्र की 10,000 रुपये सीमा से अधिक हैं। पासबुक, असली आधार और एटीएम कार्ड लेकर बैंक शाखा या एटीएम जाएं।",
                "task": "WITHDRAW",
                "suggestedAmountInr": amt,
                "isScamAlert": False,
                "abstained": False,
            }
            records.append(build_assistant_record(q, CTX_DEFAULT, target, "withdraw_branch_gt_10k", reg))
            if reg in {"en", "hinglish"}:
                noisy = apply_whisper_asr_noise(q, rng)
                if normalize_key(noisy) != normalize_key(q):
                    records.append(build_assistant_record(noisy, CTX_DEFAULT, target, "withdraw_branch_gt_10k", f"{reg}_asr"))
    return records


def generate_transfer_pool(rng: random.Random) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    amounts = [500, 1000, 1500, 2000, 2500, 3000, 4500, 5000, 7500, 8000, 10000, 15000, 20000]
    contexts = [CTX_DEFAULT, CTX_TRANSFER]
    templates = [
        ("en", "I want to send Rs {amt} to my son studying in the city. What documents are needed?"),
        ("en", "How can I deposit Rs {amt} into my relative's bank account from my village?"),
        ("en", "Can Post Office IPPB transfer {amt} rupees safely if I do not have a smartphone?"),
        ("en", "What details of the receiver should I carry to send Rs {amt}?"),
        ("hi", "मुझे बेटे के खाते में {amt} रुपये भेजने हैं, कहाँ जाऊं और क्या ले जाऊं?"),
        ("hi", "गाँव से {amt} रुपये दूसरे के खाते में जमा करने के लिए कौन से कागज लगेंगे?"),
        ("hi", "क्या डाकघर IPPB या बैंक मित्र से {amt} रुपये सुरक्षित भेजे जा सकते हैं?"),
        ("hinglish", "Mujhe {amt} rupaye bete ko bhejne hain, Post Office IPPB se ho jayega kya?"),
        ("hinglish", "rishtedar ke khate mein {amt} rs jama karna hai kya kya likh ke le jaun"),
        ("hinglish", "gaon se shahar {amt} rupaye transfer karne ka sabse aasan rasta batao"),
    ]
    for amt in amounts:
        for reg, tmpl in templates:
            q = tmpl.format(amt=amt)
            ctx = rng.choice(contexts)
            target = {
                "replyEn": f"Visit your Post Office (IPPB) or Bank Mitra to send Rs {amt}. Carry original Aadhaar, receiver account number with IFSC, and always take a printed receipt.",
                "replyHi": f"{amt} रुपये भेजने के लिए डाकघर (IPPB) या बैंक मित्र केंद्र जाएं। असली आधार, पाने वाले का खाता नंबर व IFSC ले जाएं और छपी रसीद लें।",
                "task": "TRANSFER",
                "suggestedAmountInr": amt,
                "isScamAlert": False,
                "abstained": False,
            }
            records.append(build_assistant_record(q, ctx, target, "transfer_ippb_bc", reg))
            if reg in {"en", "hinglish"}:
                noisy = apply_whisper_asr_noise(q, rng)
                if normalize_key(noisy) != normalize_key(q):
                    records.append(build_assistant_record(noisy, ctx, target, "transfer_ippb_bc", f"{reg}_asr"))
    return records


def generate_passbook_dbt_pool(rng: random.Random) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    schemes = [
        ("PM-Kisan installment", "पीएम-किसान किस्त", "pm kisan kist"),
        ("old-age pension DBT", "वृद्धावस्था पेंशन डीबीटी", "budhapa pension"),
        ("MGNREGA wage credit", "मनरेगा मजदूरी पैसा", "narega mazdoori"),
        ("LPG gas subsidy", "गैस सब्सिडी पैसा", "gas subsidy"),
        ("widow pension subsidy", "विधवा पेंशन किस्त", "vidhwa pension"),
        ("scholarship DBT", "छात्रवृत्ति डीबीटी", "scholarship paisa"),
    ]
    bsbda_variants = [
        ("en", "Is minimum balance required in my Jan Dhan account?", "No. Jan Dhan (BSBDA) accounts are zero-balance with unlimited deposits and at least 4 free withdrawals per month.", "नहीं। जन धन (BSBDA) जीरो-बैलेंस खाता है। इसमें जमा की सीमा नहीं है और महीने में कम से कम 4 निकासी मुफ्त हैं।"),
        ("en", "How many free withdrawals per month are permitted under RBI BSBDA rules?", "Under RBI BSBDA rules, you get at least 4 free withdrawals per month, including ATM withdrawals, with zero minimum balance.", "RBI BSBDA नियमों के अनुसार जीरो-बैलेंस खाते में महीने में कम से कम 4 निकासी मुफ्त होती हैं।"),
        ("en", "Can the bank fine me if my PMJDY zero balance account has Rs 0?", "No penalty applies on a genuine PMJDY / BSBDA zero-balance account, and monthly deposits are unlimited.", "जन धन या BSBDA जीरो-बैलेंस खाते में 0 रुपये रहने पर कोई जुर्माना नहीं लगता और जमा की सीमा नहीं है।"),
        ("en", "Is there any limit on deposits in a Basic Savings Bank Deposit Account?", "No. BSBDA / Jan Dhan accounts allow unlimited cash or DBT deposits with zero minimum balance penalty.", "नहीं। जन धन (BSBDA) खाते में महीने में पैसे जमा करने की कोई सीमा नहीं है।"),
        ("hi", "क्या जन धन खाते में मिनिमम बैलेंस न रखने पर जुर्माना लगता है?", "कोई जुर्माना नहीं लगता। जन धन (BSBDA) जीरो-बैलेंस खाता है और महीने में 4 निकासी मुफ्त मिलती हैं।", "कोई जुर्माना नहीं लगता। जन धन (BSBDA) जीरो-बैलेंस खाता है और महीने में 4 निकासी मुफ्त मिलती हैं।"),
        ("hi", "जन धन खाते से महीने में कितनी बार मुफ्त पैसे निकाल सकते हैं?", "RBI नियमों के तहत जन धन (BSBDA) खाते में महीने में कम से कम 4 निकासी मुफ्त होती हैं।", "RBI नियमों के तहत जन धन (BSBDA) खाते में महीने में कम से कम 4 निकासी मुफ्त होती हैं।"),
        ("hinglish", "Jan dhan khate me kam se kam kitna paisa rakhna zaroori hai?", "Zero minimum balance is needed in a Jan Dhan (BSBDA) account, and you get at least 4 free monthly withdrawals.", "जन धन (BSBDA) खाते में न्यूनतम बैलेंस रखने की जरूरत नहीं है। महीने में कम से कम 4 निकासी मुफ्त होती हैं।"),
        ("hinglish", "mera zero balance PMJDY khata hai usme paisa jama karne ki limit hoti hai kya", "No deposit limit applies on a PMJDY / BSBDA zero-balance account, and 4 monthly withdrawals are free.", "जन धन जीरो-बैलेंस खाते में जमा करने की कोई सीमा नहीं है और महीने में 4 निकासी मुफ्त हैं।"),
    ]
    for reg, q, r_en, r_hi in bsbda_variants:
        if reg == "hi":
            r_en_final = "No penalty applies. PMJDY / BSBDA accounts have zero minimum balance, unlimited deposits, and 4 free monthly withdrawals."
        else:
            r_en_final = r_en
        target = {
            "replyEn": r_en_final,
            "replyHi": r_hi,
            "task": "PASSBOOK_DBT",
            "suggestedAmountInr": 0,
            "isScamAlert": False,
            "abstained": False,
        }
        records.append(build_assistant_record(q, CTX_BSBDA, target, "passbook_dbt_bsbda", reg))
        noisy = apply_whisper_asr_noise(q, rng)
        if normalize_key(noisy) != normalize_key(q):
            records.append(build_assistant_record(noisy, CTX_BSBDA, target, "passbook_dbt_bsbda", f"{reg}_asr"))

    for en_s, hi_s, hg_s in schemes:
        dbt_prompts = [
            ("en", f"How do I check if my {en_s} arrived and update my passbook?"),
            ("en", f"Where should I go to verify my {en_s} credit and print entries?"),
            ("en", f"Can Bank Mitra check my {en_s} mini-statement or print the passbook?"),
            ("hi", f"मेरी {hi_s} खाते में आई या नहीं, और पासबुक प्रिंट कहाँ होगी?"),
            ("hi", f"{hi_s} की जांच करने के लिए कौन से कागज लेकर कहाँ जाऊं?"),
            ("hinglish", f"meri {hg_s} aayi ya nahi aur passbook entry kahan chhapegi?"),
            ("hinglish", f"{hg_s} check karne ke liye bc point jaun ya branch passbook le kar"),
        ]
        for reg, q in dbt_prompts:
            target = {
                "replyEn": f"Check your {en_s} mini-statement quickly at a Bank Mitra (BC Point) with Aadhaar, or visit your Bank Branch with your physical passbook for printing.",
                "replyHi": f"{hi_s} की मिनी-स्टेटमेंट जांच बीसी केंद्र पर आधार से तुरंत हो जाएगी। पूरी पासबुक छपवाने के लिए बैंक शाखा जाएं।",
                "task": "PASSBOOK_DBT",
                "suggestedAmountInr": 0,
                "isScamAlert": False,
                "abstained": False,
            }
            records.append(build_assistant_record(q, CTX_DEFAULT, target, "passbook_dbt_bsbda", reg))
            if reg in {"en", "hinglish"}:
                noisy = apply_whisper_asr_noise(q, rng)
                if normalize_key(noisy) != normalize_key(q):
                    records.append(build_assistant_record(noisy, CTX_DEFAULT, target, "passbook_dbt_bsbda", f"{reg}_asr"))

    return records


def generate_kcc_loan_pool(rng: random.Random) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    # Up to 2,00,000 -> collateral-free at Branch
    collateral_free_amts = [25000, 50000, 75000, 100000, 120000, 150000, 160000, 180000, 200000]
    cf_templates = [
        ("en", "How do I apply for a Rs {amt} Kisan Credit Card loan without land mortgage?"),
        ("en", "What documents should I pack before leaving home for a Rs {amt} KCC loan?"),
        ("en", "Can a village BC point approve my Rs {amt} KCC agricultural loan?"),
        ("hi", "बिना जमीन गिरवी रखे {amt} रुपये तक का केसीसी कृषि लोन कैसे और कहाँ मिलेगा?"),
        ("hi", "मुझे खेती के लिए {amt} रुपये का केसीसी लोन लेना है, कौन से कागज ले जाऊं?"),
        ("hinglish", "KCC loan {amt} rs lene ke liye kaunse kagaz chahiye aur branch jana hoga kya?"),
        ("hinglish", "bina zameen girvi rakhe {amt} rupaye ka kisan credit card mil jayega kya"),
    ]
    for amt in collateral_free_amts:
        for reg, tmpl in cf_templates:
            q = tmpl.format(amt=amt)
            target = {
                "replyEn": f"Under RBI rules, KCC agricultural loans up to Rs 2,00,000 are collateral-free and must be processed at a Bank Branch. Carry Khatauni, 2 Aadhaar+PAN copies, passbook, and 3 photos.",
                "replyHi": "RBI नियमों के तहत 2,00,000 रुपये तक का केसीसी (KCC) कृषि लोन बिना गारंटी बैंक शाखा से मिलता है। खतौनी, आधार-पैन की 2 कॉपी, पासबुक और 3 फोटो ले जाएं।",
                "task": "LOAN_KCC",
                "suggestedAmountInr": amt,
                "isScamAlert": False,
                "abstained": False,
            }
            records.append(build_assistant_record(q, CTX_KCC, target, "loan_kcc", reg))
            if reg in {"en", "hinglish"}:
                noisy = apply_whisper_asr_noise(q, rng)
                if normalize_key(noisy) != normalize_key(q):
                    records.append(build_assistant_record(noisy, CTX_KCC, target, "loan_kcc", f"{reg}_asr"))

    # Above 2,00,000 -> Branch assessment + collateral/security rules apply above 2L cap
    above_2l_amts = [220000, 250000, 300000, 400000, 500000]
    above_templates = [
        ("en", "Do I need land security or collateral for a Rs {amt} KCC farm loan?"),
        ("hi", "{amt} रुपये के बड़े केसीसी लोन के लिए क्या जमीन के कागज और गारंटी लगेगी?"),
        ("hinglish", "mujhe {amt} rupaye ka kcc loan chahiye kya 2 lakh se upar bina guarantee milega"),
    ]
    for amt in above_2l_amts:
        for reg, tmpl in above_templates:
            q = tmpl.format(amt=amt)
