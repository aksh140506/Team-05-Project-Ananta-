# BankSathi FastAPI Backend

Dual-mode FastAPI service powering BankSathi's grounded RAG assistant, geospatial facility discovery (SQLite locally, PostgreSQL + PostGIS via `DATABASE_URL`), HMAC-SHA256 signed delta-sync bundles, ScamGuard rule checks, and Firebase Auth + Role-Based Access Control (`CITIZEN`, `VERIFIER`, `ADMIN`).

## Local Setup (Laptop Demo)

```bash
cd backend
python -m venv .venv
# Windows: .venv\Scripts\activate
# macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

- **Swagger UI:** `http://127.0.0.1:8000/docs`
- **OpenAPI JSON:** `http://127.0.0.1:8000/openapi.json`
- **Android Emulator URL:** `http://10.0.2.2:8000/` (set in root `.env` as `BANKSATHI_API_BASE_URL=http://10.0.2.2:8000/`)
- **Postman:** Import `BankSathi.postman_collection.json` and `BankSathi.postman_environment.json`

## Run Backend Tests

```bash
cd backend
pytest -v
```
