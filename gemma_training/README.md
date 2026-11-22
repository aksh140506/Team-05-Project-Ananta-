# BankSathi Gemma-2B Offline Training & MediaPipe Pipeline

This directory contains the complete, schema-locked training pipeline for fine-tuning **Gemma-2B** (`google/gemma-2-2b-it`) to power **BankSathi**'s on-device offline assistant (`HybridAiService.kt`).

## What the Fine-Tuned Model Learns

The training data aligns 1-to-1 with the exact prompts and JSON contracts in `HybridAiService.kt`:

1. **Bilingual + Hinglish Understanding (`replyEn` + Devanagari `replyHi`)**
   - Handles English, Devanagari Hindi, and Romanized Hinglish rural speech transcripts (e.g., *"Bhaiya 5000 rupaye nikalne hain"*).
   - Enforces concise `<= 45` word answers optimized for `0.92x` TTS playback.
2. **RBI & NPCI Banking Rule Routing**
   - **Withdraw (`WITHDRAW`)**: `<= ₹10,000` -> `BC_POINT` (Aadhaar biometric, zero extra fee); `> ₹10,000` -> `BRANCH / ATM`.
   - **Transfer (`TRANSFER`)**: `IPPB / BC_POINT` with receiver Account + IFSC & printed receipt.
   - **Passbook / DBT (`PASSBOOK_DBT`)**: Jan Dhan / BSBDA zero minimum balance + 4 free monthly withdrawals; mini-statement at BC vs physical print at Branch.
   - **KCC Loan (`LOAN_KCC`)**: Collateral-free up to `₹2,00,000` at `BRANCH` with Khatauni, 2 Aadhaar+PAN copies, passbook, and 3 photos.
3. **ScamGuard (`isScamAlert=true` & `riskLevel`)**
   - Detects OTP/PIN/CVV vishing, `bit.ly` / `.apk` KYC traps, AnyDesk/QuickSupport screen-sharing, AePS double-thumbprint without receipt, and illegal BC fees.
   - Outputs `1930` Cyber Helpline and **RBI 3-working-day Zero Liability** rule.
4. **Safe Abstention (`abstained=true`, `task="NONE"`)**
   - Refuses to guess live bank balances, unverified schemes, or non-banking questions, routing instead to the nearest verified branch or `14448 / 1930`.

---

## Step-by-Step Workflow

### 1. Generate & Audit 1k-3k Production Dataset
```bash
cd gemma_training
python3 dataset_generator.py --out-dir data --target-rows 1500
python3 eval_gemma_banksathi.py --train-file data/train.jsonl --eval-file data/eval.jsonl
```
*(See `docs/BankSathi_Gemma2B_Training_and_Dataset_Guide.pdf` for the complete 8-page technical manual, vector diagrams, RBI grounding rules, and Colab T4 training walkthrough.)*

### 2. Fine-Tune with QLoRA (Google Colab T4 / 16 GB GPU)
Accept the Gemma license on Hugging Face, then run:
```bash
pip install -r requirements.txt
huggingface-cli login

python train_gemma2b_qlora.py \
  --model-id google/gemma-2-2b-it \
  --train-file data/train.jsonl \
  --eval-file data/eval.jsonl \
  --output-dir outputs/gemma2-2b-banksathi
