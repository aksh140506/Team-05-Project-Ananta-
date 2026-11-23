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
            target = {
                "replyEn": f"Only KCC loans up to Rs 2,00,000 are collateral-free under RBI rules. For Rs {amt}, visit your Bank Branch with Khatauni, KYC copies, passbook, and photos for security assessment.",
                "replyHi": f"RBI नियम के तहत केवल 2,00,000 रुपये तक का KCC लोन बिना गारंटी मिलता है। {amt} रुपये के लिए खतौनी, केवाईसी, पासबुक व फोटो लेकर बैंक शाखा में बात करें।",
                "task": "LOAN_KCC",
                "suggestedAmountInr": amt,
                "isScamAlert": False,
                "abstained": False,
            }
            records.append(build_assistant_record(q, CTX_KCC, target, "loan_kcc", reg))

    return records


def generate_assistant_scam_pool(rng: random.Random) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    vectors = [
        ("en", "Someone called claiming to be from SBI and asked for my 6-digit OTP to stop account block."),
        ("en", "The BC agent said my thumbprint failed and told me to scan twice without giving a receipt."),
        ("en", "Caller asked me to install AnyDesk remote app to credit my pending PM-Kisan refund."),
        ("en", "Got an SMS with a bit.ly link saying PAN KYC expired today—should I tap it?"),
        ("en", "Someone wants my RuPay ATM PIN and CVV over phone to verify DBT transfer."),
        ("en", "Fraudster sent a .apk file on WhatsApp for fast KYC update, should I install it?"),
        ("en", "Money was debited after I shared an OTP on a fake lottery call. How many days do I have?"),
        ("en", "Caller told me to enter UPI PIN in PhonePe to receive Rs 15,000 crop compensation."),
        ("hi", "फोन पर बैंक अधिकारी बनकर मेरा एटीएम पिन और ओटीपी मांग रहा है, क्या बता दूं?"),
        ("hi", "SMS आया है कि खाता बंद हो जाएगा, इस bit.ly लिंक पर क्लिक करें। क्या करूं?"),
        ("hi", "बैंक मित्र ने अंगूठा लगवाया, पैसे नहीं दिए और रसीद भी नहीं दी, दुबारा अंगूठा मांग रहा है।"),
        ("hi", "कॉलर बोल रहा है TeamViewer या AnyDesk ऐप डालकर 9 अंक का कोड बताओ तो सब्सिडी आएगी।"),
        ("hi", "नकली केवाईसी लिंक पर क्लिक करने से मेरे खाते से पैसे कट गए, अब कितने दिन में रिपोर्ट करूं?"),
        ("hi", "व्हाट्सएप पर अनजान .apk फाइल भेजकर केवाईसी करने को कह रहा है, क्या डाल लूं?"),
        ("hinglish", "Phone par lottery jeetne ka call aaya aur UPI PIN dalne ko bol raha hai."),
        ("hinglish", "Mere account se bina bataye paise kat gaye fraud link se, ab kya karun?"),
        ("hinglish", "aadmi bol raha hai otp batao tabhi kisan samman nidhi ka paisa aayega"),
        ("hinglish", "bc agent ne raseed nahi diya aur dobara thumb scan karne bol raha hai"),
        ("hinglish", "quicksupport app download karwa ke atm card number pooch raha hai"),
        ("hinglish", "bank manager bol ke cvv aur pin pooch raha hai phone par kya karun"),
    ]
    for reg, q in vectors:
        target = {
            "replyEn": "ScamGuard Alert: Never share OTP, PIN, CVV, install screen-share apps, or scan your thumb twice without a printed receipt. Hang up, call 1930, and inform your bank within 3 working days.",
            "replyHi": "स्कैम गार्ड चेतावनी: कभी भी OTP, पिन, स्क्रीन-शेयर ऐप न दें और बिना रसीद दो बार अंगूठा न लगाएं। तुरंत फोन काटें, 1930 पर कॉल करें और 3 दिन में बैंक को बताएं।",
            "task": "NONE",
            "suggestedAmountInr": 0,
            "isScamAlert": True,
            "abstained": False,
        }
        records.append(build_assistant_record(q, CTX_ZERO_LIAB, target, "assistant_scam_alert", reg))
        noisy = apply_whisper_asr_noise(q, rng)
        if normalize_key(noisy) != normalize_key(q):
            records.append(build_assistant_record(noisy, CTX_ZERO_LIAB, target, "assistant_scam_alert", f"{reg}_asr"))
    return records


def generate_abstention_pool(rng: random.Random) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    # Mix of live private banking data, unverified rumor traps, out-of-domain queries, and empty/missing-context probes
    traps = [
        ("en", "How much exact balance is in my SBI account number 30491 right now?"),
        ("en", "Did Ramesh Verma's NEFT transfer of Rs 4000 clear at 2 PM today?"),
        ("en", "What is my forgotten 4-digit RuPay ATM PIN?"),
        ("en", "Tell me today's live interest rate of XYZ Private Microfinance App."),
        ("en", "Will the government double PM-Kisan to Rs 12000 next month?"),
        ("en", "Can you check my personal CIBIL credit score offline right now?"),
        ("en", "Is the Rampur Bank Mitra shop physically open at this exact minute?"),
        ("en", "What is the live share price of SBI and HDFC today?"),
        ("en", "What medicine should I take for high fever and joint pain?"),
        ("en", "Which private chit fund doubles money in 90 days?"),
        ("en", "Tell me today's gold price and rain forecast for Sitapur."),
        ("en", "Did RBI announce a brand new loan waiver scheme 10 minutes ago?"),
        ("hi", "मेरे बैंक खाते में अभी ठीक-ठीक कितने रुपये बचे हैं, बता दो?"),
        ("hi", "आज मेरे गाँव की मंडी में गेहूं और धान का लाइव भाव क्या है?"),
        ("hi", "क्या अगले हफ्ते नया सरकारी कर्ज माफी नियम आने वाला है?"),
        ("hi", "मेरे गाँव का बैंक मित्र अभी इस समय दुकान खोलकर बैठा है या बंद है?"),
        ("hi", "मेरे एटीएम कार्ड का भुला हुआ 4 अंक का पिन क्या है, अभी बताओ?"),
        ("hi", "बुखार और खांसी के लिए कौन सी दवाई खाना सही रहेगा?"),
        ("hi", "पिछले महीने मेरी भैंस बीमा क्लेम की फाइल पास हुई या नहीं, अभी जांच कर बताओ?"),
        ("hinglish", "Mera pichhla RTGS transaction fail kyon hua, live status check karo."),
        ("hinglish", "mere khate me abhi kitna balance bacha hai exact batao"),
        ("hinglish", "kal sarkar naya 5000 rupaye wala yojana shuru karegi kya"),
        ("hinglish", "private unknown app se 5 minute mein 50000 loan lena sahi hai kya"),
        ("hinglish", "mera atm pin bhul gaya hun abhi naya pin bata do"),
        ("hinglish", "aaj mandi mein sarso ka bhav kya chal raha hai"),
        ("hinglish", "konsa share kharidein jo 1 mahine me paisa double kar de"),
    ]
    for reg, q in traps:
        target = {
            "replyEn": "I am not sure about this and cannot verify live personal or unverified facts offline. BankSathi never guesses—please visit your nearest verified Bank Branch or call 14448 / 1930.",
            "replyHi": "मुझे इसकी पक्की जानकारी नहीं है क्योंकि लाइव खाता या असत्यापित डेटा ऑफलाइन नहीं देखा जा सकता। कृपया नज़दीकी बैंक शाखा में पूछें या 14448 / 1930 पर कॉल करें।",
            "task": "NONE",
            "suggestedAmountInr": 0,
            "isScamAlert": False,
            "abstained": True,
        }
        records.append(build_assistant_record(q, CTX_DEFAULT, target, "safe_abstention", reg))
        noisy = apply_whisper_asr_noise(q, rng)
        if normalize_key(noisy) != normalize_key(q):
            records.append(build_assistant_record(noisy, CTX_DEFAULT, target, "safe_abstention", f"{reg}_asr"))
    return records


def generate_scam_guard_sms_pool(rng: random.Random) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []

    high_risk = [
        ("en", "Dear customer your SBI YONO account will be blocked today. Update PAN KYC immediately at http://bit.ly/sbi-kyc-99 or call 98xxxxxxx.",
         "Fake KYC warning SMS with a shortened link. Real banks never threaten account closure via bit.ly links.",
         "यह नकली KYC लिंक वाला धोखाधड़ी संदेश है। असली बैंक कभी ऐसे छोटे लिंक भेजकर खाता बंद करने की धमकी नहीं देते।",
         "Do not click the link. Delete the SMS and call 1930 or your official bank branch if you shared details.",
         "लिंक पर बिल्कुल क्लिक न करें। यदि कोई जानकारी दे दी है तो तुरंत 1930 पर कॉल करें और 3 कार्य दिवस में बैंक को सूचित करें।"),
        ("en", "Caller says: Sir I am calling from RBI Mumbai, your PM-Kisan Rs 6000 is stuck. Tell me the 6-digit OTP on your phone to release it.",
         "High-risk OTP vishing scam. RBI and government offices never call citizens asking for OTPs to release subsidies.",
         "यह गंभीर OTP ठगी कॉल है। RBI या सरकारी अधिकारी कभी किस्त भेजने के लिए फोन पर ओटीपी नहीं मांगते।",
         "Hang up immediately. Never share OTP. Report the number on 1930 and alert your bank within 3 working days.",
         "तुरंत फोन काट दें और ओटीपी न बताएं। ठगी होने पर 1930 पर कॉल करें और 3 कार्य दिवस में बैंक को बताएं।"),
        ("en", "BC agent says machine failed after first thumb scan, asks me to press thumb again without giving a printed receipt.",
         "Danger of unauthorized AePS double withdrawal. Never scan your fingerprint twice without verifying a printed failure receipt.",
         "बिना रसीद दोबारा अंगूठा लगवाना AePS ठगी का खतरा है। पहली जांच की छपी रसीद और बैंक SMS देखे बिना दोबारा अंगूठा न लगाएं।",
         "Refuse a second scan until you get a printed receipt and check your SMS balance. Report fraud to 1930 within 3 working days.",
         "बिना छपी रसीद और बैलेंस SMS देखे दोबारा अंगूठा न लगाएं। शक होने पर 1930 पर कॉल करें और 3 कार्य दिवस में बैंक में शिकायत करें।"),
        ("en", "Download QuickSupport_KYC.apk from WhatsApp and share the 9-digit screen code to unblock your ATM card.",
         "Screen-sharing malware trap! Sharing AnyDesk or QuickSupport codes lets fraudsters steal money from your phone.",
         "यह स्क्रीन-शेयरिंग ऐप का खतरनाक जाल है। QuickSupport या AnyDesk कोड देने से ठग आपके खाते से पैसे निकाल सकते हैं।",
         "Never install .apk files from WhatsApp. Delete immediately and call 1930; inform your bank within 3 working days.",
         "व्हाट्सएप से कोई .apk फाइल या स्क्रीन-शेयर ऐप न डालें। गलती से हो जाए तो तुरंत 1930 पर कॉल करें और 3 दिन में बैंक को बताएं।"),
        ("en", "Congratulations! You won Rs 25 Lakh KBC lottery. Enter your UPI PIN in PhonePe to receive the prize money.",
         "UPI PIN scam! You never need to enter your UPI PIN to receive money—PIN is only for sending money.",
         "यह लॉटरी ठगी है। पैसे प्राप्त करने के लिए कभी भी UPI पिन डालने की जरूरत नहीं होती, पिन केवल पैसे भेजने में लगता है।",
         "Block the sender immediately and report on 1930. Contact your bank within 3 working days if money was debited.",
         "तुरंत नंबर ब्लॉक करें। पैसे कटने पर 1930 पर शिकायत करें और RBI नियम के अनुसार 3 कार्य दिवस में बैंक को बताएं।"),
        ("hinglish", "Aapka bank khata aaj raat band ho jayega, abhi AnyDesk app download karke OTP batayein.",
         "High-risk fraud combining fake account block threat, AnyDesk remote access, and OTP theft.",
         "यह गंभीर धोखाधड़ी है जिसमें खाता बंद करने का डर दिखाकर AnyDesk और OTP मांगा जा रहा है।",
         "Do not install AnyDesk or share OTP. Call 1930 immediately and report to your bank within 3 working days.",
         "AnyDesk ऐप न डालें और ओटीपी न बताएं। तुरंत 1930 पर कॉल करें और 3 कार्य दिवस में बैंक को सूचित करें।"),
        ("hi", "बिजली बिल और केवाईसी अपडेट के लिए इस tinyurl लिंक पर 10 रुपये का भुगतान करें वरना कनेक्शन और खाता बंद होगा।",
         "Phishing link scam pretending to be an urgent bill/KYC payment to steal card or UPI credentials.",
         "यह नकली बिल और KYC भुगतान लिंक ठगी है। 10 रुपये के नाम पर आपका पूरा बैंक खाता खाली किया जा सकता है।",
         "Never click tinyurl links for KYC or bills. Call 1930 and alert your bank within 3 working days if money was lost.",
         "ऐसे लिंक पर बिल्कुल क्लिक न करें। पैसे कटने पर तुरंत 1930 पर कॉल करें और 3 दिन में बैंक में शिकायत दर्ज करें।"),
        ("hi", "व्हाट्सएप पर कॉल करके बोला कि आपका सिम और बैंक खाता 2 घंटे में बंद होगा, अभी RustDesk ऐप डालो।",
         "High-risk remote access scam using fake SIM/bank deactivation fear to install RustDesk.",
         "यह सिम या खाता बंद होने का डर दिखाकर RustDesk स्क्रीन-शेयर ऐप डलवाने की गंभीर ठगी है।",
         "Hang up and never install remote apps. Call 1930 and inform your bank within 3 working days if shared.",
         "फोन काटें और कोई रिमोट ऐप न डालें। धोखाधड़ी होने पर 1930 पर कॉल करें और 3 दिन में बैंक को बताएं।"),
    ]
    for reg, snip, ex_en, ex_hi, tip_en, tip_hi in high_risk:
        records.append(
            build_scam_record(
                snip,
                {
                    "riskLevel": "HIGH_RISK",
                    "explanationEn": ex_en,
                    "explanationHi": ex_hi,
                    "actionTipEn": tip_en,
                    "actionTipHi": tip_hi,
                },
                "scam_high_risk",
                reg,
            )
        )

    caution = [
        ("en", "Local Bank Mitra is demanding Rs 50 extra commission for withdrawing Rs 2000 via Aadhaar thumb scan.",
         "Caution: Under RBI Business Correspondent rules, Bank Mitras cannot demand extra unofficial fees from customers.",
         "सावधानी: RBI नियमों के अनुसार बैंक मित्र (BC) निकासी के लिए ग्राहक से अलग से शुल्क या कमीशन नहीं मांग सकता।",
         "Insist on a printed receipt and report extra-fee demands to your main Bank Branch or RBI helpline 14448.",
         "छपी हुई रसीद जरूर लें और अतिरिक्त शुल्क की शिकायत मुख्य बैंक शाखा या RBI हेल्पलाइन 14448 पर करें।"),
        ("en", "SMS received: Your loan pre-approval offer expires tonight. Call private agent number 9911xxxx00 urgently.",
         "Caution: Urgent loan offers from private mobile numbers often hide processing-fee scams.",
         "सावधानी: निजी मोबाइल नंबर से आने वाले जल्दबाजी वाले लोन ऑफर अक्सर प्रोसेसिंग फीस ठगी का तरीका होते हैं।",
         "Do not pay any advance processing fee. Verify loan schemes directly at your official Bank Branch.",
         "पहले से कोई प्रोसेसिंग फीस न दें। लोन की जानकारी सीधे अपनी सरकारी या मुख्य बैंक शाखा से ही लें।"),
        ("en", "Unknown caller claims to be from bank head office asking me to visit a link tomorrow to check subsidy status.",
         "Caution: Unverified external links can steal personal details even if no OTP was asked yet.",
         "सावधानी: अनजान लिंक पर क्लिक करने से आपकी निजी जानकारी चोरी हो सकती है, चाहे अभी ओटीपी न मांगा गया हो।",
         "Ignore unknown links. Check subsidy status safely at your Bank Mitra or official Bank Branch.",
         "अनजान लिंक न खोलें। सब्सिडी की जांच बैंक मित्र केंद्र या बैंक शाखा से ही कराएं।"),
        ("hinglish", "Bank Mitra kehta hai 5000 nikalne par 30 rupaye alag charge lagega, bina raseed ke.",
         "Caution: RBI forbids Business Correspondents from charging extra cash fees or refusing printed receipts.",
         "सावधानी: RBI नियमों के खिलाफ बैंक मित्र अलग से नकद शुल्क मांग रहा है और छपी रसीद नहीं दे रहा है।",
         "Refuse extra cash charges, demand a printed receipt, or visit another verified BC / Branch and call 14448.",
         "अतिरिक्त शुल्क न दें, छपी रसीद मांगें या मुख्य बैंक शाखा में जाएं और 14448 पर शिकायत करें।"),
        ("en", "WhatsApp message offering instant Mudra loan in 10 minutes if I send Rs 999 registration fee first.",
         "Caution: Official Mudra and KCC loans never ask for advance registration fees via WhatsApp.",
         "सावधानी: सरकारी मुद्रा या KCC लोन के लिए व्हाट्सएप पर पहले से रजिस्ट्रेशन फीस नहीं मांगी जाती।",
         "Do not send any advance fee. Apply only through your official Bank Branch or JanSamarth portal.",
         "कोई अग्रिम फीस न भेजें। लोन के लिए केवल अपनी असली बैंक शाखा में ही आवेदन करें।"),
        ("hi", "एक अनजान एजेंट कह रहा है कि 500 रुपये देने पर केसीसी लोन बिना कागज 2 दिन में पास करा देगा।",
         "Caution: Official KCC loans never require cash bribes or agent fees to bypass branch document checks.",
         "सावधानी: असली केसीसी लोन में एजेंट को नकद पैसे देने की जरूरत नहीं होती; केवल बैंक शाखा में ही आवेदन करें।",
         "Do not pay middlemen. Visit your official Bank Branch directly with Khatauni and KYC documents.",
         "किसी दलाल को पैसे न दें। खतौनी और केवाईसी लेकर सीधे अपनी बैंक शाखा में जाएं।"),
    ]
    for reg, snip, ex_en, ex_hi, tip_en, tip_hi in caution:
        records.append(
            build_scam_record(
                snip,
                {
                    "riskLevel": "CAUTION",
                    "explanationEn": ex_en,
                    "explanationHi": ex_hi,
                    "actionTipEn": tip_en,
                    "actionTipHi": tip_hi,
                },
                "scam_caution",
                reg,
            )
        )

    safe = [
        ("en", "Your A/c X4512 is credited with Rs 2,000.00 on 14-Nov-26 via DBT PM-KISAN. Avl Bal Rs 5,420.50 - State Bank of India.",
         "Safe official credit alert: This is a standard DBT subsidy credit SMS with no suspicious link, phone number, or OTP request.",
         "सुरक्षित सूचना: यह सरकारी पीएम-किसान किस्त जमा होने का सामान्य बैंक SMS है। इसमें कोई संदिग्ध लिंक या ओटीपी मांग नहीं है।",
         "No action needed. Keep your passbook updated during your next branch visit.",
         "यह संदेश सुरक्षित है। अगली बार बैंक जाने पर अपनी पासबुक अपडेट करा लें।"),
        ("en", "Dear Customer, your Bank Mitra withdrawal of Rs 1,500 is successful. Ref No 631892. Avl Bal Rs 3,100 - IPPB.",
         "Safe transaction confirmation SMS matching your withdrawal. No link or personal credential request is present.",
         "सुरक्षित संदेश: यह आपकी निकासी की आधिकारिक पुष्टि वाला SMS है। इसमें कोई संदिग्ध लिंक या पिन नहीं मांगा गया है।",
         "Verify the amount matches the cash you received and keep your printed receipt safely.",
         "मिलाएं कि निकाली गई राशि सही है और बैंक मित्र से मिली छपी रसीद संभाल कर रखें।"),
