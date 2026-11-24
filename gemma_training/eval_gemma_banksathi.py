#!/usr/bin/env python3
"""
BankSathi Gemma-2B Safety & Bilingual Evaluation Suite.

Supports two modes:
  1) `--dataset-only`: Audits train.jsonl / eval.jsonl schema, bilingual word counts,
     ScamGuard coverage, and Abstention balance (runs anywhere with zero GPU).
  2) `--model-dir <path>`: Runs live inference on a fine-tuned Hugging Face / LoRA
     checkpoint and scores the 5 BankSathi production KPIs:
       - Valid JSON Parse Rate (target >= 99%)
       - Intent Task Accuracy (WITHDRAW/TRANSFER/PASSBOOK_DBT/LOAN_KCC/NONE)
       - ScamGuard Recall (must never miss OTP/AnyDesk/double-thumb -> 1930)
       - Safe Abstention Accuracy (must abstain on live balance / unverified queries)
       - Bilingual Devanagari + <=45-word TTS compliance
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any

DEVANAGARI_RE = re.compile(r"[\u0900-\u097F]")


def extract_json_object(raw_text: str) -> str:
    start = raw_text.find("{")
    end = raw_text.rfind("}")
    if start >= 0 and end > start:
        return raw_text[start : end + 1]
    return ""


def audit_dataset(jsonl_path: Path) -> dict[str, Any]:
    rows = [json.loads(line) for line in jsonl_path.read_text(encoding="utf-8").splitlines() if line.strip()]
    total = len(rows)
    valid_json = 0
    has_devanagari = 0
    concise_tts = 0
    categories: dict[str, int] = {}
    registers: dict[str, int] = {}
    hashes: set[str] = set()

    for row in rows:
        categories[row["category"]] = categories.get(row["category"], 0) + 1
        reg = row.get("register", "unknown")
        registers[reg] = registers.get(reg, 0) + 1
        if "prompt_sha256" in row:
            hashes.add(row["prompt_sha256"])
        try:
            obj = json.loads(row["completion"])
            valid_json += 1
        except Exception:
            continue

        if row["type"] == "assistant":
            hi_ok = bool(DEVANAGARI_RE.search(obj.get("replyHi", "")))
            len_ok = len(obj.get("replyEn", "").split()) <= 45 and len(obj.get("replyHi", "").split()) <= 48
        else:
            hi_ok = bool(DEVANAGARI_RE.search(obj.get("explanationHi", "")))
            len_ok = len(obj.get("explanationEn", "").split()) <= 50 and len(obj.get("explanationHi", "").split()) <= 50

        if hi_ok:
            has_devanagari += 1
        if len_ok:
            concise_tts += 1

    return {
        "file": str(jsonl_path),
        "total_rows": total,
        "unique_prompts": len(hashes) if hashes else total,
        "valid_json_pct": round(100.0 * valid_json / max(1, total), 2),
        "hindi_devanagari_pct": round(100.0 * has_devanagari / max(1, total), 2),
        "tts_concise_pct": round(100.0 * concise_tts / max(1, total), 2),
        "registers": registers,
        "categories": categories,
        "_hashes": hashes,
    }


def evaluate_live_model(model_dir: Path, eval_path: Path) -> dict[str, Any]:
    import torch
    from transformers import AutoModelForCausalLM, AutoTokenizer

    rows = [json.loads(line) for line in eval_path.read_text(encoding="utf-8").splitlines() if line.strip()]
    tokenizer = AutoTokenizer.from_pretrained(str(model_dir))
    model = AutoModelForCausalLM.from_pretrained(
        str(model_dir),
        torch_dtype=torch.float16 if torch.cuda.is_available() else torch.float32,
        device_map="auto",
    )
    model.eval()

