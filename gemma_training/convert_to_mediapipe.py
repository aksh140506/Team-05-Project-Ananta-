#!/usr/bin/env python3
"""
Convert a merged BankSathi Gemma-2B Hugging Face checkpoint into an INT4
MediaPipe GenAI `.task` bundle compatible with HybridAiService.kt.

The BankSathi Android app automatically loads any `.task` or `.bin` file placed
in app files/models or imported via Settings -> "Import Gemma-2B (.task / .bin)",
or via ADB push to `/data/local/tmp/llm/gemma2-2b-it-cpu-int4.task`.

Prerequisites (run on Linux/Colab with >= 16 GB RAM):
  pip install ai-edge-torch mediapipe sentencepiece safetensors
"""

from __future__ import annotations

import argparse
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Convert merged Gemma-2B HF weights to MediaPipe .task (INT4).")
    parser.add_argument(
        "--merged-dir",
        type=Path,
        default=Path("outputs/gemma2-2b-banksathi/merged_fp16"),
        help="Directory containing merged HF safetensors + tokenizer",
    )
    parser.add_argument(
        "--work-dir",
        type=Path,
        default=Path("outputs/mediapipe_tflite"),
        help="Intermediate TFLite directory",
    )
    parser.add_argument(
        "--output-task",
        type=Path,
        default=Path("outputs/gemma2-2b-banksathi-cpu-int4.task"),
        help="Final MediaPipe .task bundle path",
    )
    parser.add_argument("--model-family", choices=["gemma2", "gemma1"], default="gemma2")
    parser.add_argument("--context-len", type=int, default=512, help="Matches setMaxTokens(512) in HybridAiService.kt")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if not args.merged_dir.exists():
        raise FileNotFoundError(
            f"Merged checkpoint not found at {args.merged_dir}. Run train_gemma2b_qlora.py first."
        )

    args.work_dir.mkdir(parents=True, exist_ok=True)
    args.output_task.parent.mkdir(parents=True, exist_ok=True)

    # 1. Convert Hugging Face Safetensors -> INT4 Dynamic Quantized TFLite via ai-edge-torch
