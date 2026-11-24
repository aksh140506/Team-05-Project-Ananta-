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
    from ai_edge_torch.generative.examples.gemma import gemma1, gemma2
    from ai_edge_torch.generative.utilities import converter as edge_converter
    from ai_edge_torch.generative.quantize import quant_recipes
    import mediapipe as mp  # noqa: F401
    from mediapipe.tasks.python.genai import bundler

    print(f"Building {args.model_family} INT4 TFLite graph from {args.merged_dir}...")
    if args.model_family == "gemma2":
        pytorch_model = gemma2.build_2b_model(str(args.merged_dir))
    else:
        pytorch_model = gemma1.build_2b_model(str(args.merged_dir))

    tflite_path = args.work_dir / "banksathi_gemma2b_int4.tflite"
    edge_converter.convert_to_tflite(
        pytorch_model,
        output_path=str(args.work_dir),
        output_name_prefix="banksathi_gemma2b_int4",
        prefill_seq_len=args.context_len,
        kv_cache_max_len=args.context_len,
        quantize=quant_recipes.full_int8_dynamic_recipe()
        if hasattr(quant_recipes, "full_int8_dynamic_recipe")
        else True,
    )

    # 2. Bundle TFLite + Gemma SentencePiece tokenizer.model into a single .task file
    tokenizer_model = args.merged_dir / "tokenizer.model"
    if not tokenizer_model.exists():
        raise FileNotFoundError(f"Expected SentencePiece tokenizer.model inside {args.merged_dir}")

    # Pick generated .tflite file
    generated_tflites = list(args.work_dir.glob("*.tflite"))
    if not generated_tflites:
        raise FileNotFoundError(f"No .tflite file produced in {args.work_dir}")
    tflite_file = generated_tflites[0]

    config = bundler.BundleConfig(
        tflite_model=str(tflite_file),
        tokenizer_model=str(tokenizer_model),
        start_token="<bos>",
        stop_tokens=["<eos>", "<end_of_turn>"],
        output_filename=str(args.output_task),
        enable_bytes_to_unicode_mapping=False,
    )
    bundler.create_bundle(config)

    print(f"Success! MediaPipe GenAI bundle created at: {args.output_task}")
    print("Install on Android:")
    print(f"  1) Via BankSathi app Settings -> Import Gemma-2B (.task / .bin): select {args.output_task.name}")
    print(f"  2) Or via ADB: adb push {args.output_task} /data/local/tmp/llm/gemma2-2b-it-cpu-int4.task")


if __name__ == "__main__":
    main()
