#!/usr/bin/env python3
"""
QLoRA Fine-Tuning & Weight Merging for BankSathi Gemma-2B (Hindi + English + RBI Rules).

Designed to run on a single 16 GB GPU (Google Colab T4 / L4 / A10G).
Trains ONLY on the model JSON completion tokens using DataCollatorForCompletionOnlyLM
so the base model preserves general instruction following while locking onto
BankSathi's JSON schema, bilingual tone, ScamGuard alerts, and safe abstention.

Usage:
  1. Login to Hugging Face (accept Gemma license first):
       huggingface-cli login
  2. Train + merge weights:
       python train_gemma2b_qlora.py \
         --model-id google/gemma-2-2b-it \
         --train-file data/train.jsonl \
         --eval-file data/eval.jsonl \
         --output-dir outputs/gemma2-2b-banksathi
"""

from __future__ import annotations

import argparse
from pathlib import Path

import torch
from datasets import load_dataset
from peft import AutoPeftModelForCausalLM, LoraConfig
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    BitsAndBytesConfig,
    TrainingArguments,
)
from trl import DataCollatorForCompletionOnlyLM, SFTTrainer


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Fine-tune Gemma-2B with QLoRA for BankSathi.")
    parser.add_argument("--model-id", default="google/gemma-2-2b-it", help="HF model ID (gemma-2-2b-it or gemma-2b-it)")
    parser.add_argument("--train-file", default="data/train.jsonl")
    parser.add_argument("--eval-file", default="data/eval.jsonl")
    parser.add_argument("--output-dir", default="outputs/gemma2-2b-banksathi")
    parser.add_argument("--epochs", type=int, default=4)
    parser.add_argument("--batch-size", type=int, default=2)
    parser.add_argument("--grad-accum", type=int, default=8)
    parser.add_argument("--lr", type=float, default=2e-4)
    parser.add_argument("--max-seq-len", type=int, default=512)
    parser.add_argument("--lora-r", type=int, default=16)
    parser.add_argument("--lora-alpha", type=int, default=32)
    parser.add_argument("--skip-merge", action="store_true", help="Skip merging LoRA adapter into fp16 weights")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    out_dir = Path(args.output_dir)
    adapter_dir = out_dir / "lora_adapter"
    merged_dir = out_dir / "merged_fp16"

    use_bf16 = torch.cuda.is_available() and torch.cuda.is_bf16_supported()
    compute_dtype = torch.bfloat16 if use_bf16 else torch.float16

    bnb_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=compute_dtype,
        bnb_4bit_use_double_quant=True,
    )

    print(f"Loading tokenizer & 4-bit base model: {args.model_id}")
    tokenizer = AutoTokenizer.from_pretrained(args.model_id)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token
    tokenizer.padding_side = "right"

    model = AutoModelForCausalLM.from_pretrained(
        args.model_id,
        quantization_config=bnb_config,
        device_map="auto",
        attn_implementation="eager",  # Recommended by Google for Gemma-2 training stability
    )
    model.config.use_cache = False

    lora_config = LoraConfig(
