<div align="center">

<img src=".github/assets/banksathi_hero_banner.jpg" alt="BankSathi Rural Banking & ScamGuard Hero Banner" width="100%" />

<br /><br />

# BankSathi (`GraminSetu`)

### Offline-First, Trilingual Rural Banking & Scam-Guard Companion

[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.115+-009688?style=for-the-badge&logo=fastapi&logoColor=white)](https://fastapi.tiangolo.com/)
[![PostgreSQL PostGIS](https://img.shields.io/badge/PostGIS-SQLite%20Dual--Mode-336791?style=for-the-badge&logo=postgresql&logoColor=white)](https://postgis.net/)
[![Firebase](https://img.shields.io/badge/Firebase-Auth%20%7C%20Firestore%20%7C%20AppCheck-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)](https://firebase.google.com/)
[![Gemma 2B](https://img.shields.io/badge/On--Device%20AI-Gemma--2B%20int4-EA4335?style=for-the-badge&logo=google&logoColor=white)](https://ai.google.dev/gemma)
[![Pytest](https://img.shields.io/badge/Tests-JUnit%20%2B%20Robolectric%20%2B%20Pytest-10B981?style=for-the-badge&logo=pytest&logoColor=white)](backend/tests/test_api.py)

<p>
  <a href="#-why-banksathi">Overview</a> •
  <a href="#-system-architecture">Architecture</a> •
  <a href="#-core-capabilities">Features</a> •
  <a href="#-tech-stack">Tech Stack</a> •
  <a href="#-quick-start">Quick Start</a> •
  <a href="#-team-05-ownership">Team</a> •
  <a href="#-engineering-documentation-doc2">Docs</a>
</p>

</div>

---

## 🎯 Why BankSathi?

Rural and semi-urban banking citizens face fragmented connectivity, confusing paperwork requirements, outdated Bank Mitra (BC) availability, and rising OTP/KYC/screen-sharing scams. **BankSathi** works **100% offline-first** on budget Android phones (`<= 3.5 GB RAM` safe) in **English**, **Hindi (हिन्दी)**, and **Dogri (डोगरी)**—and seamlessly upgrades with live geospatial PostGIS queries, grounded RAG circulars, signed delta-sync packs, and role-based verifier stamps whenever connectivity is available.

| Challenge in Rural Banking | How BankSathi Solves It |
|---|---|
| **Zero / Spotty Village Connectivity** | Local **Room SQLite** cache + **HMAC-SHA256 signed delta-sync packs** with automatic rollback on tamper detection |
| **Low-End Smartphones (`<= 3.5 GB RAM`)** | Automatic **Low-RAM FAQ-Only Safeguard** prevents out-of-memory SLM crashes while keeping verified RBI rules instant |
| **Language & Literacy Barriers** | Trilingual UI & voice guidance (**English · Hindi · Dogri**) via Bhashini, Gemini TTS, and Google India offline TTS + movable **Sheru** assistant |
| **Wasted Branch Trips & Cyber Scams** | Pincode-first nearest **Bank / BC / IPPB** cash status with 7-day staleness badge, **RBAC Verifier** stamps, and **ScamGuard (`1930`)** analyzer |

---

## ✨ Core Capabilities

- **🗣️ Trilingual Voice-First Hub & Sheru Companion**: Ask banking questions by voice or text in `en`, `hi`, or `doi`, backed by a movable Husky mascot (`MovableHuskyOverlay`) providing screen-aware guidance.
- **🧠 4-Tier Grounded Hybrid AI Pipeline**: Routes queries through **On-Device Gemma-2B (`int4`)** -> **FastAPI Grounded RAG (`/v1/rag/ask`)** -> **Firebase AI Logic** -> **Local RBI/NPCI Rule Engine**, with prompt-injection blocking, banking-scope guardrails, and `[ABSTAIN]` fallback.
- **📍 Pincode-First Facility Locator & RBAC Verification**: Auto-discovers nearby Bank Branches, Bank Mitras (BC Points), ATMs, and Post Office Banks (`IPPB`), flags stale (`>7 days`) records as `UNKNOWN`, and unlocks official `/v1/facilities/verify` stamping for authenticated `VERIFIER` / `ADMIN` accounts.
- **🛡️ ScamGuard Sentinel (`1930` Zero Liability)**: Intercepts shared SMS/WhatsApp snippets (`ACTION_SEND`) and flags OTP requests, fake KYC links, AnyDesk/TeamViewer `.apk` traps, and digital arrest threats with `1930` action steps.
- **🎒 Pre-Trip Bag Check & Receipt Ledger**: Deterministic rule routing (`BankingRuleEngine`) checks withdrawal limits, KCC collateral-free eligibility (`<= ₹2,00,000`), and Aadhaar-DBT seeding checklists before leaving home.

---

## 🏗️ System Architecture

<div align="center">
  <img src=".github/assets/banksathi_arch_banner.jpg" alt="BankSathi Edge-to-Cloud Architecture Banner" width="100%" />
</div>

<br />

### Edge-to-Cloud Hybrid Flow

```mermaid
flowchart LR
    U["Citizen / Verifier<br/>(EN · HI · DOI)"] --> UI["Jetpack Compose M3 UI<br/>+ Sheru Voice Overlay"]
    UI --> VM["GraminSetuViewModel<br/>(StateFlow + DataStore)"]

    subgraph Edge["Offline-First Android Edge"]
        VM --> ROOM["Room SQLite<br/>(Facilities, FAQs, ScamRules, SyncMeta)"]
        VM --> GEMMA["Gemma-2B int4 /<br/>Low-RAM FAQ Safeguard"]
        VM --> DSV["DeltaSyncVerifier<br/>(HMAC-SHA256 / Rollback)"]
    end

    subgraph Cloud["Firebase + FastAPI Backend"]
        VM -->|Bearer ID Token + App Check| API["FastAPI Router<br/>(/v1/rag, /facilities, /sync, /scam)"]
        API --> AUTH["Firebase JWT + RBAC<br/>(CITIZEN · VERIFIER · ADMIN)"]
        API --> DB[("Dual-Mode DB<br/>SQLite Local / PostgreSQL + PostGIS")]
        VM <-->|Google Sign-In Sync| FS["Cloud Firestore + Rules"]
    end
```

### Signed Delta-Sync & Protected Verifier Flow

```mermaid
sequenceDiagram
    autonumber
    actor Reviewer as Verifier / Citizen
    participant App as BankSathi Android App
    participant Cred as Credential Manager (Firebase)
    participant API as FastAPI Backend
    participant DB as SQLite / PostGIS + AuditLog

    Reviewer->>App: Tap "Verify" on Facility Card
    App->>Cred: Fetch Firebase ID Token + App Check
    App->>API: POST /v1/facilities/verify (Bearer JWT)
    API->>API: Verify RS256 Token & Check Role (VERIFIER/ADMIN)
    alt Role == CITIZEN or Unauthenticated
        API-->>App: 401 Unauthorized / 403 Forbidden
        App-->>Reviewer: Show Role-Gated Guidance Snackbar
    else Role in [VERIFIER, ADMIN]
        API->>DB: Stamp verified_date + Write AuditLogORM
        API-->>App: 200 OK (Verified Facility DTO)
    end
    App->>API: POST /v1/sync/delta (since_versions)
    API-->>App: Signed UPDATE_PACKAGE (hmac:<sha256>)
    App->>App: Verify Signature Before Atomic Room Swap
```

---

## 🧰 Tech Stack

| Layer | Technologies |
|---|---|
| **Android App** | Kotlin, Jetpack Compose, Material Design 3, Navigation Compose, Lifecycle `StateFlow`, Room SQLite, DataStore, Retrofit + Moshi + OkHttp |
| **Security & Auth** | Firebase Authentication (Google Sign-In via Jetpack Credential Manager), Firebase App Check (`reCAPTCHA` / `Debug`), `network_security_config.xml`, HMAC-SHA256 Delta Verification |
| **On-Device & Cloud AI** | MediaPipe `tasks-genai` (Gemma-2B `int4`), Firebase AI Logic, Grounded FastAPI RAG (`/v1/rag/ask`), Bhashini + Gemini TTS + Google India Offline TTS |
| **Backend API** | Python 3.11+, FastAPI, Pydantic v2, SQLAlchemy 2.0, Dual-Mode SQLite / PostgreSQL + PostGIS (`ST_DWithin`), `PyJWT[crypto]`, Sliding-Window Rate Limiter |
| **ML Fine-Tuning** | `gemma_training/` — QLoRA fine-tuning, trilingual dataset generator (`train.jsonl` / `eval.jsonl`), MediaPipe `.task` converter & safety evaluator |
| **Testing & API Docs** | JUnit 4, Robolectric, Firebase Firestore Rules Tests, `pytest` (`TestClient`), OpenAPI/Swagger (`/docs`), Postman Collection + Environment |

---

## 🚀 Quick Start

<details>
<summary><b>1. Start the FastAPI Backend (Local SQLite or PostgreSQL + PostGIS)</b></summary>

```bash
cd backend
python -m venv .venv

# Windows PowerShell:
.venv\Scripts\Activate.ps1
# macOS / Linux:
# source .venv/bin/activate

pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

- **Interactive Swagger Docs**: [`http://127.0.0.1:8000/docs`](http://127.0.0.1:8000/docs)
- **Health Endpoint**: `GET http://127.0.0.1:8000/health`
- **Switch to PostgreSQL + PostGIS**: Set `DATABASE_URL=postgresql+psycopg2://user:pass@localhost:5432/banksathi` in `backend/.env`.

</details>

<details>
<summary><b>2. Configure Android Emulator & Secrets</b></summary>

1. Create a root `.env` from `.env.example` (or add variables in the **AI Studio Secrets** panel):
   ```env
   BANKSATHI_API_BASE_URL=http://10.0.2.2:8000/
   SYNC_VERIFY_KEY=your_matching_hmac_secret
   ```
2. `app/src/main/res/xml/network_security_config.xml` restricts cleartext HTTP strictly to loopback (`10.0.2.2`, `127.0.0.1`, `localhost`) for emulator demos while enforcing HTTPS globally.
3. Build and run on Android Emulator (`minSdk 24`, `targetSdk 36`).

</details>

<details>
<summary><b>3. Postman Collection & API Endpoints Reference</b></summary>

Import `backend/BankSathi.postman_collection.json` and `backend/BankSathi.postman_environment.json` into Postman.

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| `GET` | `/health` | Public | Server liveness, DB mode (`sqlite_local` / `postgresql_postgis`), & table counts |
| `POST` | `/v1/rag/ask` | Public (App Check optional) | Grounded banking & scam RAG with prompt-injection shield & `[ABSTAIN]` |
| `GET` | `/v1/facilities/nearby` | Public | Spatial facility lookup (`PostGIS ST_DWithin` or Haversine fallback) |
| `POST` | `/v1/sync/delta` | Public | Returns HMAC-SHA256 / SHA-256 signed versioned `UPDATE_PACKAGE` bundles |
| `POST` | `/v1/scam/check` | Public | Regex threat classification (`HIGH_RISK`, `CAUTION`, `SAFE`) + `1930` action tip |
| `GET` | `/v1/auth/me` | `CITIZEN+` (`401` if anon) | Resolves authenticated user UID, email, role, and permissions |
| `POST` | `/v1/facilities/verify` | `VERIFIER` / `ADMIN` (`403` otherwise) | Stamps official `verified_date` & cash status + writes `AuditLogORM` |
| `POST` | `/v1/admin/sync/publish` | `ADMIN` only | Publishes & signs a new versioned district delta-sync package |
| `GET` | `/v1/admin/audit-logs` | `ADMIN` only | Inspects recent verification and package-publish audit trail |

</details>

<details>
<summary><b>4. Gemma-2B QLoRA Fine-Tuning & Automated Tests</b></summary>

- **Backend API & RBAC Tests**:
  ```bash
  cd backend
  pytest -v
  ```
- **Android Unit & Robolectric Tests**:
  ```bash
  gradle :app:testDebugUnitTest \
    --tests "com.example.ExampleUnitTest" \
    --tests "com.example.ExampleRobolectricTest" \
    --tests "com.example.data.BankSathiAlignmentTest" \
    --tests "com.example.data.BankingSafetyGuardrailsTest"
  ```
- **Gemma-2B Training Pipeline (`gemma_training/`)**: See [`gemma_training/README.md`](gemma_training/README.md) for dataset generation (`dataset_generator.py`), QLoRA training (`train_gemma2b_qlora.py`), and MediaPipe `.task` export (`convert_to_mediapipe.py`).

</details>

---

## 👥 Team 05 Ownership

| Member | Domain | Primary Contributions |
|---|---|---|
| **Akshit Raj** | Android Architecture, Navigation & Auth | `MainActivity`, `GraminSetuViewModel`, Jetpack Credential Manager Google Sign-In, M3 Screen Navigation |
| **Nikhil Kumar** | FastAPI Backend, PostGIS/SQLite, RAG & Security | `backend/app/*`, Firebase RS256 Auth & RBAC (`CITIZEN`/`VERIFIER`/`ADMIN`), Grounded `/v1/rag/ask`, HMAC Delta-Sync, Audit Trail |
| **Sharanya Srivastava** | Offline Persistence, Geo-Sync & Scam Guard | `GraminSetuDatabase`, `DeltaSyncVerifier`, `PincodeFacilityResolver`, `ScamDetector`, `BankSathiApiService` |
| **Aditya Kumar** | Multilingual Voice, UI/UX & Accessibility | `VoiceGuideManager` (Bhashini/Gemini/Google TTS), `BilingualStrings` (`en`/`hi`/`doi`), M3 Components, `MovableHuskyOverlay` |

---

## 📚 Engineering Documentation (`Doc2/`)

<details>
<summary><b>View all 16 Finalized SDLC, Setup & Engineering PDFs in Doc2/</b></summary>

| Phase | Documents |
|---|---|
| **Project Initiation** | [`01_Project_Charter.pdf`](Doc2/01_Project_Charter.pdf) · [`02_Technical_Design_Document.pdf`](Doc2/02_Technical_Design_Document.pdf) |
| **Planning** | [`03_Work_Breakdown_Structure_WBS.pdf`](Doc2/03_Work_Breakdown_Structure_WBS.pdf) · [`04_Risk_Management_Plan_PIE.pdf`](Doc2/04_Risk_Management_Plan_PIE.pdf) · [`05_UI_UX_Design_Documentation.pdf`](Doc2/05_UI_UX_Design_Documentation.pdf) |
| **Execution** | [`06_Code_Documentation_and_README.pdf`](Doc2/06_Code_Documentation_and_README.pdf) · [`07_Sprint_Backlog.pdf`](Doc2/07_Sprint_Backlog.pdf) · [`08_Database_Schema_Documentation.pdf`](Doc2/08_Database_Schema_Documentation.pdf) · [`09_API_Documentation.pdf`](Doc2/09_API_Documentation.pdf) |
| **Testing & Deployment** | [`10_Software_Requirements_Specification_SRS.pdf`](Doc2/10_Software_Requirements_Specification_SRS.pdf) · [`11_Test_Case_Documentation.pdf`](Doc2/11_Test_Case_Documentation.pdf) · [`12_Test_Summary_Report.pdf`](Doc2/12_Test_Summary_Report.pdf) |
| **Closure, Setup & Compiled** | [`13_Change_Request_Log.pdf`](Doc2/13_Change_Request_Log.pdf) · [`14_Final_Close_Out_Report.pdf`](Doc2/14_Final_Close_Out_Report.pdf) · [`BankSathi_Complete_Compiled_Documentation.pdf`](Doc2/BankSathi_Complete_Compiled_Documentation.pdf) · [`BankSathi_Complete_Setup_and_GitHub_Guide.pdf`](Doc2/BankSathi_Complete_Setup_and_GitHub_Guide.pdf) |

</details>

---

## 📦 Pushing to GitHub

```bash
git init
git branch -M main
git status            # Verify .env, keystores, build/, and .aistudio/ are ignored
git add .
git commit -m "feat: BankSathi offline-first Android app, FastAPI backend, Gemma pipeline, and Doc2 specs"
git remote add origin <YOUR_GITHUB_REPO_URL>
git push -u origin main
```
