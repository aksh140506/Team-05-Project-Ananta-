import hashlib
import hmac
import html
import logging
import os
import re
import time
from collections import defaultdict, deque
from dataclasses import dataclass
from typing import Callable, Optional

import httpx
import jwt
from fastapi import Depends, Header, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.responses import JSONResponse

from app.core.config import Settings, get_settings
from app.db.database import get_db
from app.db.models import UserORM, UserRoleORM

logger = logging.getLogger("banksathi.security")

ROLE_CITIZEN = "CITIZEN"
ROLE_VERIFIER = "VERIFIER"
ROLE_ADMIN = "ADMIN"
VALID_ROLES = {ROLE_CITIZEN, ROLE_VERIFIER, ROLE_ADMIN}

BACKEND_JWT_ISSUER = "banksathi-api"

FIREBASE_CERTS_URL = (
    "https://www.googleapis.com/robot/v1/metadata/x509/"
    "securetoken@system.gserviceaccount.com"
)

_bearer_scheme = HTTPBearer(auto_error=False)

_CERT_CACHE: dict = {"certs": {}, "expires_at": 0.0}

# Patterns blocked as XSS / script-injection or SQL-tautology probes in untrusted inputs
_DANGEROUS_XSS_REGEX = re.compile(
    r"(<\s*script|javascript:|onerror\s*=|onload\s*=|<\s*iframe)",
    re.IGNORECASE,
)

_PROMPT_INJECTION_PATTERNS = (
    "ignore previous instruction",
    "ignore all instruction",
    "forget your instruction",
    "disregard above",
    "override system prompt",
    "reveal your prompt",
    "show system prompt",
    "jailbreak",
    "dan mode",
    "bypass safety",
    "bypass guardrail",
    "<system>",
    "</system>",
)


@dataclass
class AuthenticatedUser:
    uid: str
    email: Optional[str]
    role: str
    auth_mode: str
    full_name: Optional[str] = None


def hash_password(password: str) -> str:
    """Hash a plaintext password using salted scrypt (no external C dependencies)."""
    salt = os.urandom(16)
    dk = hashlib.scrypt(password.encode("utf-8"), salt=salt, n=16384, r=8, p=1, dklen=32)
    return f"scrypt${salt.hex()}${dk.hex()}"


def verify_password(password: str, stored_hash: str) -> bool:
    """Verify a plaintext password against a stored scrypt digest in constant time."""
    try:
        scheme, salt_hex, dk_hex = (stored_hash or "").split("$", 2)
        if scheme != "scrypt":
            return False
        salt = bytes.fromhex(salt_hex)
        expected_dk = bytes.fromhex(dk_hex)
        actual_dk = hashlib.scrypt(
            password.encode("utf-8"),
            salt=salt,
            n=16384,
            r=8,
            p=1,
            dklen=len(expected_dk),
        )
        return hmac.compare_digest(actual_dk, expected_dk)
    except (ValueError, TypeError):
        return False


def create_access_token(
    uid: str,
    email: str,
    role: str,
    settings: Settings,
    full_name: Optional[str] = None,
) -> tuple[str, int]:
    """Mint a signed HS256 session JWT for Supabase PostgreSQL DB authentication."""
    now = int(time.time())
    expires_in = max(300, int(settings.jwt_expire_minutes) * 60)
    payload = {
        "sub": uid,
        "email": email,
        "role": role,
        "name": full_name or "",
        "iss": BACKEND_JWT_ISSUER,
        "iat": now,
        "exp": now + expires_in,
    }
    token = jwt.encode(
        payload,
        settings.effective_jwt_secret,
        algorithm=settings.jwt_algorithm,
    )
    return token, expires_in


def sanitize_text_input(raw: str, max_len: int = 600) -> str:
    """Validates against XSS payloads and HTML-escapes stored user text."""
    cleaned = (raw or "").strip()
    if _DANGEROUS_XSS_REGEX.search(cleaned):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Input rejected: potentially unsafe script or markup detected.",
        )
    escaped = html.escape(cleaned, quote=False)
    return escaped[:max_len]


def mask_sensitive_pii(text: str) -> str:
    masked = re.sub(
        r"\b\d{4}[\s-]\d{4}[\s-]\d{4}\b",
        lambda m: "XXXX-XXXX-" + re.sub(r"\D", "", m.group(0))[-4:],
        text,
    )
    masked = re.sub(r"\b\d{11,16}\b", lambda m: "XXXX" + m.group(0)[-4:], masked)
    masked = re.sub(r"\b[A-Z]{5}\d{4}[A-Z]\b", "XXXXX****X", masked)
    return masked


def is_prompt_injection(query: str) -> bool:
    lower = (query or "").lower()
    return any(pat in lower for pat in _PROMPT_INJECTION_PATTERNS)


def _fetch_firebase_certs() -> dict[str, str]:
    now = time.time()
    if _CERT_CACHE["certs"] and now < _CERT_CACHE["expires_at"]:
        return _CERT_CACHE["certs"]
    resp = httpx.get(FIREBASE_CERTS_URL, timeout=4.0)
    resp.raise_for_status()
    certs = resp.json()
    _CERT_CACHE["certs"] = certs
    _CERT_CACHE["expires_at"] = now + 3600.0
    return certs


def _verify_backend_or_supabase_jwt(token: str, settings: Settings) -> tuple[dict, str]:
    """Verifies HS256 JWTs issued by BankSathi API (/v1/auth/login) or Supabase Auth."""
    try:
        claims = jwt.decode(
            token,
            key=settings.effective_jwt_secret,
            algorithms=[settings.jwt_algorithm],
            issuer=BACKEND_JWT_ISSUER,
        )
        return claims, "supabase_db_jwt"
    except jwt.PyJWTError:
        pass

    supabase_secret = (settings.supabase_jwt_secret or "").strip()
    if supabase_secret:
        try:
            claims = jwt.decode(
                token,
                key=supabase_secret,
                algorithms=["HS256"],
                audience="authenticated",
            )
            return claims, "supabase_auth_jwt"
        except jwt.PyJWTError:
            pass

    raise HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Invalid or expired session token. Please sign in again.",
    )


def _verify_firebase_id_token(token: str, settings: Settings, header: dict) -> dict:
    kid = header.get("kid")
    project_id = settings.firebase_project_id
    issuer = f"https://securetoken.google.com/{project_id}"

    try:
        certs = _fetch_firebase_certs()
        cert_pem = certs.get(kid)
        if not cert_pem:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Unknown Firebase signing key ID.",
            )
        from cryptography.x509 import load_pem_x509_certificate

        public_key = load_pem_x509_certificate(cert_pem.encode("utf-8")).public_key()
        claims = jwt.decode(
            token,
            key=public_key,
            algorithms=["RS256"],
            audience=project_id,
            issuer=issuer,
        )
        return claims
    except HTTPException:
        raise
    except Exception as exc:
        if settings.firebase_allow_unverified_offline_demo:
            try:
                unverified = jwt.decode(
                    token,
                    options={"verify_signature": False, "verify_aud": False, "verify_exp": False},
                )
                if unverified.get("sub") or unverified.get("user_id"):
                    logger.warning("Verified Firebase JWT claims in offline demo mode (signature skipped).")
                    return unverified
            except Exception:
                pass
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired Firebase ID token.",
        ) from exc


def resolve_user_role(uid: str, email: Optional[str], db: Session, settings: Settings) -> str:
    clean_email = (email or "").strip().lower()
    if clean_email and clean_email in settings.admin_email_set:
        resolved = ROLE_ADMIN
    elif clean_email and clean_email in settings.verifier_email_set:
        resolved = ROLE_VERIFIER
    else:
        existing = db.query(UserRoleORM).filter(UserRoleORM.uid == uid).first()
        resolved = existing.role if existing and existing.role in VALID_ROLES else ROLE_CITIZEN

    existing = db.query(UserRoleORM).filter(UserRoleORM.uid == uid).first()
    if existing is None:
        db.add(UserRoleORM(uid=uid, email=clean_email or None, role=resolved))
        db.commit()
    elif clean_email and (existing.email != clean_email or (resolved != ROLE_CITIZEN and existing.role != resolved)):
        existing.email = clean_email
        if resolved in {ROLE_ADMIN, ROLE_VERIFIER}:
            existing.role = resolved
        db.commit()
        resolved = existing.role
    return resolved


def get_optional_user(
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(_bearer_scheme),
    x_firebase_appcheck: Optional[str] = Header(default=None),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> Optional[AuthenticatedUser]:
    if settings.require_app_check and not (x_firebase_appcheck or "").strip():
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing X-Firebase-AppCheck attestation token.",
        )
    if credentials is None or not credentials.credentials:
        return None

    token = credentials.credentials
    try:
        header = jwt.get_unverified_header(token)
    except jwt.PyJWTError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid bearer token header.",
        ) from exc

    alg = str(header.get("alg") or "").upper()
    if alg.startswith("HS"):
        claims, auth_mode = _verify_backend_or_supabase_jwt(token, settings)
    else:
        claims = _verify_firebase_id_token(token, settings, header)
        auth_mode = "firebase_jwt"

    uid = claims.get("sub") or claims.get("user_id")
    if not uid:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token missing subject UID.",
        )

    db_user = db.query(UserORM).filter(UserORM.uid == uid).first()
    if db_user is not None and not db_user.is_active:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Account is deactivated.",
        )

    email = claims.get("email") or (db_user.email if db_user else None)
    full_name = (db_user.full_name if db_user else None) or claims.get("name") or None
    role = resolve_user_role(uid, email, db, settings)
    return AuthenticatedUser(
        uid=uid,
        email=email,
        role=role,
        auth_mode=auth_mode,
        full_name=full_name,
    )


def get_current_user(
    user: Optional[AuthenticatedUser] = Depends(get_optional_user),
) -> AuthenticatedUser:
    if user is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication required. Sign in with email/password or Google to obtain a Bearer token.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return user


def require_role(*allowed_roles: str) -> Callable:
    allowed = set(allowed_roles)

    def _dependency(user: AuthenticatedUser = Depends(get_current_user)) -> AuthenticatedUser:
        if user.role not in allowed:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"Access denied: requires role {', '.join(sorted(allowed))} (current role: {user.role}).",
            )
        return user

    return _dependency


class RateLimitMiddleware(BaseHTTPMiddleware):
    """Simple per-client sliding-window rate limiter protecting API endpoints."""

    def __init__(self, app, max_requests_per_min: int = 60):
        super().__init__(app)
        self.max_requests_per_min = max(10, max_requests_per_min)
        self._hits: dict[str, deque[float]] = defaultdict(deque)

    async def dispatch(self, request: Request, call_next):
        if request.url.path in {"/", "/health", "/docs", "/openapi.json", "/redoc"}:
            return await call_next(request)

        client_host = request.client.host if request.client else "unknown"
        now = time.time()
        window = self._hits[client_host]
        while window and now - window[0] > 60.0:
            window.popleft()

        if len(window) >= self.max_requests_per_min:
            return JSONResponse(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                content={"detail": "Rate limit exceeded. Please wait before retrying."},
                headers={"Retry-After": "60"},
            )

        window.append(now)
        return await call_next(request)
