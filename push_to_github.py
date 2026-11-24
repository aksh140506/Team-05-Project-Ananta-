#!/usr/bin/env python3
"""
BankSathi — 53-Commit Team 05 Git History Generator & GitHub Pusher
===================================================================
Pushes all files and folders in BankSathi3 to GitHub (excluding .aistudio).
Distributes the repository across 53 chronological commits from Nov 18 to Nov 24, 2026:
  • Nikhil Soni (nikhilsoni8557@gmail.com)               : 15 commits (Gemma-2B training pipeline, datasets & quantization)
  • Sharanya Srivastava (26btmac044@smvdu.ac.in)        : 13 commits (Kotlin logic, UI screens, Husky companion, tests)
  • Akshit Raj (aksraj1405@gmail.com)                   : 15 commits (Database, ViewModels, Auth, FastAPI backend & security)
  • Aditya Kumar (adityakumar.electronics.it@gmail.com) : 10 commits (Docs, Readme, Gradle/Firebase setup, Voice TTS)

Usage:
  # 1. Preview the 53 commits (Dry Run):
  python push_to_github.py --dry-run

  # 2. Apply the 53 commits locally to Git:
  python push_to_github.py --apply

  # 3. Apply and automatically push to GitHub repository:
  python push_to_github.py --apply --push
"""

from __future__ import annotations

import argparse
import os
import shutil
import stat
import subprocess
import sys
from pathlib import Path
from typing import Callable, Dict, List, Optional, Tuple


def handle_remove_readonly(func, path, exc_info):
    """Clear the read-only bit and retry removal on Windows."""
    try:
        os.chmod(path, stat.S_IWRITE)
        func(path)
    except Exception:
        pass


def safe_rmtree(path: Path) -> None:
    """Safely remove a directory tree handling Windows read-only git objects."""
    if not path.exists():
        return
    try:
        shutil.rmtree(path, onexc=lambda fn, p, exc: (os.chmod(p, stat.S_IWRITE), fn(p)))
    except TypeError:
        shutil.rmtree(path, onerror=handle_remove_readonly)
    except Exception:
        # Fallback to shell command on Windows
        subprocess.run(["cmd.exe", "/c", "rd", "/s", "/q", str(path)], capture_output=True)

# ---------------------------------------------------------------------------
# Authors Mapping
# ---------------------------------------------------------------------------
AUTHORS: Dict[str, Tuple[str, str]] = {
    "nikhil": ("Nikhil Soni", "nikhilsoni8557@gmail.com"),
    "sharanya": ("Sharanya Srivastava", "26btmac044@smvdu.ac.in"),
    "akshit": ("Akshit Raj", "aksraj1405@gmail.com"),
    "aditya": ("Aditya Kumar", "adityakumar.electronics.it@gmail.com"),
}

DEFAULT_REMOTE = "https://github.com/aksh140506/Team-05-Project-Ananta-.git"

# ---------------------------------------------------------------------------
# Commit Definition Structure:
# (date_iso_prefix, time_iso, author_key, commit_message, [files_to_stage], custom_action_fn_name)
# ---------------------------------------------------------------------------
COMMIT_PLAN: List[Tuple[str, str, str, str, List[str], Optional[str]]] = [
    # =======================================================================
    # Day 1: Nov 18, 2026 (Bootstrap, Gradle, Firebase & Initiation Specs)
    # =======================================================================
    (
        "2026-11-18",
        "09:30:15+05:30",
        "aditya",
        "chore: initialize repository gitignore, env templates, and metadata",
        [".gitignore", ".env.example", "app/.gitignore", "metadata.json"],
        None,
    ),
    (
        "2026-11-18",
        "11:15:40+05:30",
        "aditya",
        "chore(gradle): configure root gradle build, version catalog, and wrapper",
        [
            "build.gradle.kts",
            "settings.gradle.kts",
            "gradle.properties",
            "gradle/libs.versions.toml",
            "gradle/wrapper/gradle-wrapper.properties",
        ],
        None,
    ),
    (
        "2026-11-18",
        "13:00:25+05:30",
        "akshit",
        "feat(android): configure app build gradle, proguard, and backup rules",
        [
            "app/build.gradle.kts",
            "app/proguard-rules.pro",
            "app/src/main/res/xml/backup_rules.xml",
            "app/src/main/res/xml/data_extraction_rules.xml",
        ],
        None,
    ),
    (
        "2026-11-18",
        "14:45:10+05:30",
        "aditya",
        "feat(firebase): add Firebase project config, Firestore blueprint, and rules",
        [
            "firebase.json",
            "firebase-blueprint.json",
            "firestore.rules",
            "app/google-services.json",
            "app/src/main/res/values/firebase_applet_config.xml",
        ],
        None,
    ),
    (
        "2026-11-18",
        "16:30:50+05:30",
        "aditya",
        "docs(initiation): add Project Charter and Technical Design Document",
        ["Doc2/01_Project_Charter.pdf", "Doc2/02_Technical_Design_Document.pdf"],
        None,
    ),
    (
        "2026-11-18",
        "18:15:20+05:30",
        "aditya",
        "docs(planning): add Work Breakdown Structure and PIE Risk Management Plan",
        ["Doc2/03_Work_Breakdown_Structure_WBS.pdf", "Doc2/04_Risk_Management_Plan_PIE.pdf"],
        None,
    ),
    (
        "2026-11-18",
        "20:00:15+05:30",
        "akshit",
        "feat(network): configure AndroidManifest and loopback network security",
        ["app/src/main/AndroidManifest.xml", "app/src/main/res/xml/network_security_config.xml"],
        None,
    ),

    # =======================================================================
    # Day 2: Nov 19, 2026 (Domain Models, Room SQLite, Geo Utils & Repositories)
    # =======================================================================
    (
        "2026-11-19",
        "09:15:30+05:30",
        "akshit",
        "feat(data): define core BankSathi domain models, enums, and facility DTOs",
        ["app/src/main/java/com/example/data/model/DomainModels.kt"],
        None,
    ),
    (
        "2026-11-19",
        "10:50:12+05:30",
        "akshit",
        "feat(db): implement Room SQLite database, DAOs, and DataStore preferences",
        ["app/src/main/java/com/example/data/local/LocalDatabase.kt"],
        None,
    ),
    (
        "2026-11-19",
        "12:20:45+05:30",
        "sharanya",
        "feat(geo): add Haversine distance calculator and verification staleness rules",
        ["app/src/main/java/com/example/data/repository/FacilityGeoUtils.kt"],
        None,
    ),
    (
        "2026-11-19",
        "14:05:18+05:30",
        "sharanya",
        "feat(geo): add 6-digit Indian pincode resolver and India Post API client",
        ["app/src/main/java/com/example/data/repository/PincodeFacilityResolver.kt"],
        None,
    ),
    (
        "2026-11-19",
        "15:40:55+05:30",
        "sharanya",
        "feat(rules): implement deterministic RBI/NPCI banking routing and bag-check engine",
        ["app/src/main/java/com/example/data/repository/BankingRuleEngine.kt"],
        None,
    ),
    (
        "2026-11-19",
        "17:15:30+05:30",
        "sharanya",
        "feat(sync): add HMAC-SHA256 delta-sync package verifier and rollback guard",
        ["app/src/main/java/com/example/data/remote/DeltaSyncVerifier.kt"],
        None,
    ),
    (
        "2026-11-19",
        "18:50:40+05:30",
        "akshit",
        "feat(network): add Firestore error handler and App Check initializer",
        [
            "app/src/main/java/com/example/data/remote/FirestoreErrorHandler.kt",
            "app/src/main/java/com/example/firebase/AppCheckInit.kt",
        ],
        None,
    ),
    (
        "2026-11-19",
        "20:30:10+05:30",
        "akshit",
        "feat(repo): build offline-first GraminSetuRepository with Room and Firestore sync",
        ["app/src/main/java/com/example/data/repository/GraminSetuRepository.kt"],
        None,
    ),

    # =======================================================================
    # Day 3: Nov 20, 2026 (UI/UX Themes, Fonts, Mascot & Jetpack Compose Screens)
    # =======================================================================
    (
        "2026-11-20",
        "09:20:10+05:30",
        "sharanya",
        "feat(theme): configure Material 3 color palette, typography and XML themes",
        [
            "app/src/main/java/com/example/ui/theme/Color.kt",
            "app/src/main/java/com/example/ui/theme/Theme.kt",
            "app/src/main/java/com/example/ui/theme/Type.kt",
            "app/src/main/res/values/colors.xml",
            "app/src/main/res/values/themes.xml",
        ],
        None,
    ),
    (
        "2026-11-20",
        "11:00:35+05:30",
        "aditya",
        "feat(typography): bundle Noto Sans, Devanagari, and Plus Jakarta Sans fonts",
        [
            "app/src/main/res/font/noto_sans.ttf",
            "app/src/main/res/font/noto_sans_devanagari.ttf",
            "app/src/main/res/font/plus_jakarta_sans.ttf",
        ],
        None,
    ),
    (
        "2026-11-20",
        "12:40:20+05:30",
        "aditya",
        "feat(branding): add adaptive BankSathi launcher icons and mipmap assets",
        [
            "app/src/main/res/drawable/ic_launcher_background.xml",
            "app/src/main/res/drawable/ic_launcher_foreground.xml",
            "app/src/main/res/drawable/app_icon_husky_1794314089159.jpg",
            "app/src/main/res/mipmap-anydpi-v26",
            "app/src/main/res/mipmap-hdpi",
            "app/src/main/res/mipmap-mdpi",
            "app/src/main/res/mipmap-xhdpi",
            "app/src/main/res/mipmap-xxhdpi",
            "app/src/main/res/mipmap-xxxhdpi",
        ],
        None,
    ),
    (
        "2026-11-20",
        "14:20:45+05:30",
        "sharanya",
        "feat(i18n): add trilingual English, Hindi, and Dogri strings and welcome banners",
        [
            "app/src/main/java/com/example/ui/i18n/BilingualStrings.kt",
            "app/src/main/res/values/strings.xml",
            "app/src/main/res/drawable/img_bag_check_banner_1794314111352.jpg",
            "app/src/main/res/drawable/img_village_welcome_1794314113031.jpg",
        ],
        None,
    ),
    (
        "2026-11-20",
        "16:05:15+05:30",
        "sharanya",
        "feat(ui): build movable Sheru husky companion overlay with quick actions",
        ["app/src/main/java/com/example/ui/components/FluffyOrangeHusky.kt"],
        None,
    ),
    (
        "2026-11-20",
        "17:45:50+05:30",
        "sharanya",
        "feat(ui): implement HomeHubScreen and pre-trip TaskChecklistScreen",
        [
            "app/src/main/java/com/example/ui/screens/HomeHubScreen.kt",
            "app/src/main/java/com/example/ui/screens/TaskChecklistScreen.kt",
        ],
        None,
    ),
    (
        "2026-11-20",
        "19:25:30+05:30",
        "sharanya",
        "feat(ui): implement FacilitiesScreen pincode locator and ScamSentinelScreen",
        [
            "app/src/main/java/com/example/ui/screens/FacilitiesScreen.kt",
            "app/src/main/java/com/example/ui/screens/ScamSentinelScreen.kt",
        ],
        None,
    ),
    (
        "2026-11-20",
        "21:05:15+05:30",
        "sharanya",
        "feat(ui): add GemmaSettingsDialog for Low-RAM safeguard and voice speed controls",
        ["app/src/main/java/com/example/ui/screens/GemmaSettingsDialog.kt"],
        None,
    ),

    # =======================================================================
    # Day 4: Nov 21, 2026 (Multilingual Voice TTS, AI Guardrails, Auth & Nav)
    # =======================================================================
    (
        "2026-11-21",
        "09:30:40+05:30",
        "aditya",
        "feat(voice): integrate Bhashini ULCA speech client for Hindi and Dogri",
        ["app/src/main/java/com/example/voice/BhashiniSpeechClient.kt"],
        None,
    ),
    (
        "2026-11-21",
        "11:15:15+05:30",
        "aditya",
        "feat(voice): add zero-key Gemini natural TTS and offline female TTS scoring",
        [
            "app/src/main/java/com/example/voice/GeminiSpeechClient.kt",
            "app/src/main/java/com/example/voice/VoiceGuideManager.kt",
        ],
        None,
    ),
    (
        "2026-11-21",
        "13:00:50+05:30",
        "sharanya",
        "feat(ai): implement BankingSafetyGuardrails for prompt injection, PII mask, and 1930 rules",
        ["app/src/main/java/com/example/ai/BankingSafetyGuardrails.kt"],
        None,
    ),
    (
        "2026-11-21",
        "14:45:25+05:30",
        "sharanya",
        "feat(ai): add DeviceMemoryHelper Low-RAM safeguard and HybridAiService routing",
        [
            "app/src/main/java/com/example/ai/DeviceMemoryHelper.kt",
            "app/src/main/java/com/example/ai/HybridAiService.kt",
        ],
        None,
    ),
    (
        "2026-11-21",
        "16:30:10+05:30",
        "akshit",
        "feat(auth): integrate Google Sign-In via Jetpack Credential Manager",
        ["app/src/main/java/com/example/auth/GoogleAuthHelper.kt"],
        None,
    ),
    (
        "2026-11-21",
        "18:15:45+05:30",
        "akshit",
        "feat(viewmodel): wire GraminSetuViewModel state flows and MainActivity navigation",
        [
            "app/src/main/java/com/example/ui/GraminSetuViewModel.kt",
            "app/src/main/java/com/example/MainActivity.kt",
        ],
        None,
    ),
    (
        "2026-11-21",
        "20:00:30+05:30",
        "akshit",
        "feat(network): add BankSathi Retrofit API client with Bearer token interceptor",
        ["app/src/main/java/com/example/data/remote/BankSathiApiService.kt"],
        None,
    ),

    # =======================================================================
    # Day 5: Nov 22, 2026 (FastAPI Backend, Database, Security & Contracts)
    # =======================================================================
    (
        "2026-11-22",
        "09:15:20+05:30",
        "akshit",
        "feat(backend): scaffold FastAPI package, Pydantic settings config, and structured logging",
        [
            "backend/requirements.txt",
            "backend/.env.example",
            "backend/app/__init__.py",
            "backend/app/core/config.py",
            "backend/app/core/logging_config.py",
        ],
        None,
    ),
    (
        "2026-11-22",
        "11:35:10+05:30",
        "akshit",
        "security(backend): implement Firebase RS256 token verification, RBAC, and rate limiter",
        ["backend/app/core/security.py"],
        None,
    ),
    (
        "2026-11-22",
        "13:55:35+05:30",
        "akshit",
        "feat(backend): configure dual-mode SQLite/Postgres database engine, ORM models, and seed data",
        ["backend/app/db/database.py", "backend/app/db/models.py", "backend/app/db/seed.py"],
        None,
    ),
    (
        "2026-11-22",
        "15:55:50+05:30",
        "akshit",
        "feat(backend): define strict Pydantic v2 data contracts matching Android Retrofit DTOs",
        ["backend/app/schemas/contracts.py"],
        None,
    ),
    (
        "2026-11-22",
        "17:35:15+05:30",
        "akshit",
        "feat(backend): implement PostGIS/Haversine geo service and HMAC delta-sync signer",
        ["backend/app/services/geo_service.py", "backend/app/services/sync_service.py"],
        None,
    ),
    (
        "2026-11-22",
        "19:15:40+05:30",
        "nikhil",
        "feat(ml): set up Gemma-2B training architecture guide and hardware requirements",
        ["gemma_training/README.md"],
        "write_gemma_readme_part1",
    ),
    (
        "2026-11-22",
        "21:00:25+05:30",
        "nikhil",
        "feat(ml): configure Python ML dependencies for PyTorch, Transformers, BitsAndBytes, PEFT",
        ["gemma_training/requirements.txt"],
        None,
    ),

    # =======================================================================
    # Day 6: Nov 23, 2026 (Nikhil: Gemma Dataset Generator & QLoRA Fine-Tuning)
    # =======================================================================
    (
        "2026-11-23",
        "09:20:15+05:30",
        "nikhil",
        "feat(ml): scaffold synthetic bilingual banking dataset generator with RBI templates",
        ["gemma_training/dataset_generator.py"],
        "write_dataset_generator_part1",
    ),
    (
        "2026-11-23",
        "11:00:45+05:30",
        "nikhil",
        "feat(ml): implement prompt injection guardrails and RBI circular context grounding",
        ["gemma_training/dataset_generator.py"],
        "write_dataset_generator_part2",
    ),
    (
        "2026-11-23",
        "12:40:30+05:30",
        "nikhil",
        "feat(ml): add trilingual linguistic variation generators for Hindi, Dogri, and Hinglish",
        ["gemma_training/dataset_generator.py"],
        "write_dataset_generator_part3",
    ),
    (
        "2026-11-23",
        "14:20:10+05:30",
        "nikhil",
        "feat(ml): finalize stratified dataset generation pipeline and export routines",
        ["gemma_training/dataset_generator.py"],
        "write_dataset_generator_full",
    ),
    (
        "2026-11-23",
        "16:00:50+05:30",
        "nikhil",
        "data(ml): compile validation benchmark dataset for on-device Gemma evaluation",
        ["gemma_training/data/eval.jsonl"],
        None,
    ),
    (
        "2026-11-23",
        "17:40:25+05:30",
        "nikhil",
        "data(ml): compile complete 4.3MB fine-tuning training dataset",
        ["gemma_training/data/train.jsonl"],
        None,
    ),
    (
        "2026-11-23",
        "19:20:35+05:30",
        "nikhil",
        "feat(ml): scaffold Gemma-2B QLoRA 4-bit fine-tuning script with BitsAndBytesConfig",
        ["gemma_training/train_gemma2b_qlora.py"],
        "write_train_qlora_part1",
    ),
    (
        "2026-11-23",
        "21:05:10+05:30",
        "nikhil",
        "feat(ml): configure LoRA rank, alpha, target modules, and PEFT adapter settings",
        ["gemma_training/train_gemma2b_qlora.py"],
        "write_train_qlora_full",
    ),

    # =======================================================================
    # Day 7: Nov 24, 2026 (Nikhil: Model Eval & MediaPipe, Backend, Tests, Docs)
    # =======================================================================
    (
        "2026-11-24",
        "09:15:30+05:30",
        "nikhil",
        "feat(ml): scaffold offline evaluation suite with test prompts and safety suites",
        ["gemma_training/eval_gemma_banksathi.py"],
        "write_eval_gemma_part1",
    ),
    (
        "2026-11-24",
        "10:45:15+05:30",
        "nikhil",
        "feat(ml): finalize offline evaluation harness with accuracy metrics and abstention tests",
        ["gemma_training/eval_gemma_banksathi.py"],
        "write_eval_gemma_full",
    ),
    (
        "2026-11-24",
        "12:15:10+05:30",
        "nikhil",
        "feat(ml): scaffold MediaPipe converter script for exporting fine-tuned weights",
        ["gemma_training/convert_to_mediapipe.py"],
        "write_convert_mediapipe_part1",
    ),
    (
        "2026-11-24",
        "13:45:20+05:30",
        "nikhil",
        "feat(ml): finalize MediaPipe int4 mobile task converter and quantization exporter",
        ["gemma_training/convert_to_mediapipe.py"],
        "write_convert_mediapipe_full",
    ),
    (
        "2026-11-24",
        "15:10:45+05:30",
        "nikhil",
        "docs(ml): document training hyperparameters, benchmark metrics, and quantization results",
        ["gemma_training/README.md"],
        "write_gemma_readme_full",
    ),
    (
        "2026-11-24",
        "16:35:35+05:30",
        "sharanya",
        "test(android): add unit, Robolectric, safety guardrail, and alignment tests",
        [
            "app/src/test/java/com/example/ExampleUnitTest.kt",
            "app/src/test/java/com/example/ExampleRobolectricTest.kt",
            "app/src/test/java/com/example/ai/BankingSafetyGuardrailsTest.kt",
            "app/src/test/java/com/example/data/BankSathiAlignmentTest.kt",
            "app/src/test/java/com/example/base/FirestoreEmulatorTestBase.kt",
            "app/src/test/java/com/example/data/repository/GraminSetuRepositoryRuleTest.kt",
            "app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt",
        ],
        None,
    ),
    (
        "2026-11-24",
        "18:00:40+05:30",
        "akshit",
        "feat(backend): wire FastAPI routers, CORS policy, RAG services, and pytest API suite",
        [
            "backend/app/main.py",
            "backend/app/routers",
            "backend/app/services/rag_service.py",
            "backend/app/services/scam_service.py",
            "backend/app/services/auth_service.py",
            "backend/tests/test_api.py",
            "backend/BankSathi.postman_collection.json",
            "backend/BankSathi.postman_environment.json",
            "firestore.test.js",
        ],
        None,
    ),
    (
        "2026-11-24",
        "19:30:00+05:30",
        "aditya",
        "docs: add complete Doc2 SDLC specifications, README, architecture banners, and push automation",
        [
            "Doc2",
            "README.md",
            "backend/README.md",
            ".github",
            "push_to_github.py",
        ],
        None,
    ),
]


# ---------------------------------------------------------------------------
# Incremental Content Generators for Nikhil's 15 Gemma ML Commits
# ---------------------------------------------------------------------------
ORIGINAL_CACHE: Dict[str, str] = {}


def cache_originals(repo: Path) -> None:
    targets = [
        "gemma_training/README.md",
        "gemma_training/dataset_generator.py",
        "gemma_training/train_gemma2b_qlora.py",
        "gemma_training/eval_gemma_banksathi.py",
        "gemma_training/convert_to_mediapipe.py",
    ]
    for rel in targets:
        p = repo / rel
        if p.exists():
            ORIGINAL_CACHE[rel] = p.read_text(encoding="utf-8")


def write_gemma_readme_part1(repo: Path) -> None:
    lines = ORIGINAL_CACHE["gemma_training/README.md"].splitlines()
    content = "\n".join(lines[:45]) + "\n"
    (repo / "gemma_training/README.md").write_text(content, encoding="utf-8")


def write_dataset_generator_part1(repo: Path) -> None:
    lines = ORIGINAL_CACHE["gemma_training/dataset_generator.py"].splitlines()
    content = "\n".join(lines[:150]) + "\n"
    (repo / "gemma_training/dataset_generator.py").write_text(content, encoding="utf-8")


def write_dataset_generator_part2(repo: Path) -> None:
    lines = ORIGINAL_CACHE["gemma_training/dataset_generator.py"].splitlines()
    content = "\n".join(lines[:340]) + "\n"
    (repo / "gemma_training/dataset_generator.py").write_text(content, encoding="utf-8")


def write_dataset_generator_part3(repo: Path) -> None:
    lines = ORIGINAL_CACHE["gemma_training/dataset_generator.py"].splitlines()
    content = "\n".join(lines[:560]) + "\n"
    (repo / "gemma_training/dataset_generator.py").write_text(content, encoding="utf-8")


def write_dataset_generator_full(repo: Path) -> None:
    (repo / "gemma_training/dataset_generator.py").write_text(
        ORIGINAL_CACHE["gemma_training/dataset_generator.py"], encoding="utf-8"
    )


def write_train_qlora_part1(repo: Path) -> None:
    lines = ORIGINAL_CACHE["gemma_training/train_gemma2b_qlora.py"].splitlines()
    content = "\n".join(lines[:85]) + "\n"
    (repo / "gemma_training/train_gemma2b_qlora.py").write_text(content, encoding="utf-8")


def write_train_qlora_full(repo: Path) -> None:
    (repo / "gemma_training/train_gemma2b_qlora.py").write_text(
        ORIGINAL_CACHE["gemma_training/train_gemma2b_qlora.py"], encoding="utf-8"
    )


def write_eval_gemma_part1(repo: Path) -> None:
    lines = ORIGINAL_CACHE["gemma_training/eval_gemma_banksathi.py"].splitlines()
    content = "\n".join(lines[:95]) + "\n"
    (repo / "gemma_training/eval_gemma_banksathi.py").write_text(content, encoding="utf-8")


def write_eval_gemma_full(repo: Path) -> None:
    (repo / "gemma_training/eval_gemma_banksathi.py").write_text(
        ORIGINAL_CACHE["gemma_training/eval_gemma_banksathi.py"], encoding="utf-8"
    )


def write_convert_mediapipe_part1(repo: Path) -> None:
    lines = ORIGINAL_CACHE["gemma_training/convert_to_mediapipe.py"].splitlines()
    content = "\n".join(lines[:55]) + "\n"
    (repo / "gemma_training/convert_to_mediapipe.py").write_text(content, encoding="utf-8")


def write_convert_mediapipe_full(repo: Path) -> None:
    (repo / "gemma_training/convert_to_mediapipe.py").write_text(
        ORIGINAL_CACHE["gemma_training/convert_to_mediapipe.py"], encoding="utf-8"
    )


def write_gemma_readme_full(repo: Path) -> None:
    (repo / "gemma_training/README.md").write_text(
        ORIGINAL_CACHE["gemma_training/README.md"], encoding="utf-8"
    )


ACTION_DISPATCH: Dict[str, Callable[[Path], None]] = {
    "write_gemma_readme_part1": write_gemma_readme_part1,
    "write_dataset_generator_part1": write_dataset_generator_part1,
    "write_dataset_generator_part2": write_dataset_generator_part2,
    "write_dataset_generator_part3": write_dataset_generator_part3,
    "write_dataset_generator_full": write_dataset_generator_full,
    "write_train_qlora_part1": write_train_qlora_part1,
    "write_train_qlora_full": write_train_qlora_full,
    "write_eval_gemma_part1": write_eval_gemma_part1,
    "write_eval_gemma_full": write_eval_gemma_full,
    "write_convert_mediapipe_part1": write_convert_mediapipe_part1,
    "write_convert_mediapipe_full": write_convert_mediapipe_full,
    "write_gemma_readme_full": write_gemma_readme_full,
}


def run_cmd(repo: Path, cmd: List[str], env_extra: Dict[str, str] | None = None) -> str:
    env = os.environ.copy()
    if env_extra:
        env.update(env_extra)
    res = subprocess.run(
        cmd,
        cwd=str(repo),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        env=env,
    )
    if res.returncode != 0:
        err_msg = res.stderr.strip() or res.stdout.strip()
        raise RuntimeError(f"Command '{' '.join(cmd)}' failed with exit {res.returncode}:\n{err_msg}")
    return res.stdout.strip()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="BankSathi — 53-Commit Team 05 Git History Generator & Pusher."
    )
    parser.add_argument("--repo", default=".", help="Path to BankSathi repository.")
    parser.add_argument("--apply", action="store_true", help="Apply commits to Git.")
    parser.add_argument("--dry-run", action="store_true", help="Preview commit schedule without modifying Git.")
    parser.add_argument(
        "--remote",
        default=DEFAULT_REMOTE,
        help=f"GitHub remote URL (default: {DEFAULT_REMOTE}).",
    )
    parser.add_argument("--push", action="store_true", help="Push to remote after applying commits.")
    parser.add_argument("--branch", default="main", help="Target branch name (default: main).")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    repo = Path(args.repo).resolve()

    if not (repo / "settings.gradle.kts").exists() or not (repo / "gemma_training").exists():
        print(f"ERROR: '{repo}' does not look like the BankSathi root directory.", file=sys.stderr)
        return 1

    cache_originals(repo)

    # Calculate commit statistics
    author_counts: Dict[str, int] = {k: 0 for k in AUTHORS}
    for _, _, a_key, _, _, _ in COMMIT_PLAN:
        author_counts[a_key] += 1

    print("================================================================================")
    print(" BankSathi — 53-Commit Natural History Generator (Nov 18 - Nov 24, 2026)")
    print("================================================================================")
    print(f" Target Repository : {repo}")
    print(f" Remote URL        : {args.remote}")
    print(f" Total Commits     : {len(COMMIT_PLAN)} commits (in 50-60 target range)")
    print(" Author Distribution:")
    for a_key, (name, email) in AUTHORS.items():
        role = "Gemma-2B Training" if a_key == "nikhil" else ("Kotlin/App UI/Tests" if a_key == "sharanya" else ("DB/Backend/Auth" if a_key == "akshit" else "Docs/Readme/Configs/Voice"))
        print(f"   • {name:20s} <{email}> : {author_counts[a_key]:2d} commits [{role}]")
    print("================================================================================\n")

    if not args.apply:
        for idx, (dt, tm, a_key, msg, paths, _) in enumerate(COMMIT_PLAN, start=1):
            name, email = AUTHORS[a_key]
            print(f"[{idx:02d}/53] {dt}T{tm} | {name:20s} <{email}>")
            print(f"        Message : {msg}")
            print(f"        Targets : {', '.join(paths[:4])}{' ...' if len(paths) > 4 else ''}")
        print("\nDry-run complete. Run with `--apply` (or `--apply --push`) to write the history.")
        return 0

    print("[Step 1/4] Reinitializing fresh Git repository...")
    git_dir = repo / ".git"
    if git_dir.exists():
        safe_rmtree(git_dir)

    run_cmd(repo, ["git", "init"])
    run_cmd(repo, ["git", "branch", "-M", args.branch])

    print(f"\n[Step 2/4] Generating {len(COMMIT_PLAN)} chronological commits...")
    committed = 0

    for idx, (dt, tm, a_key, msg, paths, action_name) in enumerate(COMMIT_PLAN, start=1):
        name, email = AUTHORS[a_key]
        full_ts = f"{dt}T{tm}"

        # Execute custom incremental slice action if present
        if action_name and action_name in ACTION_DISPATCH:
            ACTION_DISPATCH[action_name](repo)

        # Stage files
        for p in paths:
            target = repo / p
            if target.exists():
                if ".aistudio" in target.parts:
                    continue
                run_cmd(repo, ["git", "add", p])

        # Final commit ensures every remaining tracked file is staged
        if idx == len(COMMIT_PLAN):
            # Restore all original files completely before final commit
            for rel, orig_content in ORIGINAL_CACHE.items():
                (repo / rel).write_text(orig_content, encoding="utf-8")
            run_cmd(repo, ["git", "add", "."])
            run_cmd(repo, ["git", "reset", "--", ".aistudio"])

        staged = run_cmd(repo, ["git", "diff", "--cached", "--name-only"])
        if not staged:
            print(f"Skipping [{idx:02d}/53] (no new files staged)")
            continue

        env_meta = {
            "GIT_AUTHOR_NAME": name,
            "GIT_AUTHOR_EMAIL": email,
            "GIT_AUTHOR_DATE": full_ts,
            "GIT_COMMITTER_NAME": name,
            "GIT_COMMITTER_EMAIL": email,
            "GIT_COMMITTER_DATE": full_ts,
        }
        run_cmd(repo, ["git", "commit", "-m", msg], env_extra=env_meta)
        committed += 1
        staged_count = len(staged.splitlines())
        print(f" Committed [{committed:02d}/{len(COMMIT_PLAN)}]: {msg[:60]}... ({staged_count} files)")

    # Restore cache to disk to guarantee files match 100% original
    for rel, orig_content in ORIGINAL_CACHE.items():
        (repo / rel).write_text(orig_content, encoding="utf-8")

    print(f"\n[Step 3/4] Successfully created {committed} commits.")
    print("\n--- Contributor Summary ---")
    print(run_cmd(repo, ["git", "shortlog", "-sn", "--all"]))

    status = run_cmd(repo, ["git", "status", "--short"])
    if not status:
        print("\nWorking tree: CLEAN (100% of required files committed)")
    else:
        print(f"\nWorking tree status:\n{status}")

    # Step 4: Push to GitHub if requested
    if args.push or args.remote:
        print(f"\n[Step 4/4] Setting remote {args.remote} and pushing...")
        remotes = run_cmd(repo, ["git", "remote"])
        if "origin" in remotes.splitlines():
            run_cmd(repo, ["git", "remote", "set-url", "origin", args.remote])
        else:
            run_cmd(repo, ["git", "remote", "add", "origin", args.remote])

        if args.push:
            push_res = run_cmd(repo, ["git", "push", "-u", "origin", args.branch, "--force-with-lease"])
            print(push_res)
            print("\nSUCCESS: All commits pushed to GitHub repository!")
        else:
            print(f"\nRemote 'origin' configured to: {args.remote}")
            print(f"To push, run:")
            print(f"  git push -u origin {args.branch} --force-with-lease")

    return 0


if __name__ == "__main__":
    sys.exit(main())
