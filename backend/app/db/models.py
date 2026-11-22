from datetime import datetime, timezone
from sqlalchemy import Boolean, DateTime, Float, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.db.database import Base


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


class FacilityORM(Base):
    __tablename__ = "facilities"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    name_en: Mapped[str] = mapped_column(String(160), nullable=False)
    name_hi: Mapped[str] = mapped_column(String(160), nullable=False)
    bank_name: Mapped[str] = mapped_column(String(120), nullable=False, default="Gramin Vikas Bank")
    facility_type: Mapped[str] = mapped_column(String(32), nullable=False, index=True)
    village_or_area: Mapped[str] = mapped_column(String(180), nullable=False, default="")
    district_code: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    lat: Mapped[float] = mapped_column(Float, nullable=False)
    lon: Mapped[float] = mapped_column(Float, nullable=False)
    is_open: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    cash_status: Mapped[str] = mapped_column(String(32), nullable=False, default="CASH_READY")
    reports_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    verified_date: Mapped[str] = mapped_column(String(32), nullable=False, default="")
    verified_by_uid: Mapped[str] = mapped_column(String(128), nullable=False, default="seed_verifier")
    is_active: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True, index=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utc_now, onupdate=_utc_now)


class VerifiedDocORM(Base):
    __tablename__ = "verified_docs"

    source_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    title: Mapped[str] = mapped_column(String(180), nullable=False)
    lang_code: Mapped[str] = mapped_column(String(12), nullable=False, index=True)
    topic_tags: Mapped[str] = mapped_column(String(256), nullable=False)
    content: Mapped[str] = mapped_column(Text, nullable=False)
    recommended_task: Mapped[str] = mapped_column(String(32), nullable=True)
    suggested_amount_inr: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    verified_on: Mapped[str] = mapped_column(String(32), nullable=False)


class CachedFaqORM(Base):
    __tablename__ = "cached_faqs"

    faq_id: Mapped[int] = mapped_column(Integer, primary_key=True)
    question: Mapped[str] = mapped_column(Text, nullable=False)
    answer: Mapped[str] = mapped_column(Text, nullable=False)
    lang_code: Mapped[str] = mapped_column(String(12), nullable=False, index=True)
    source_id: Mapped[str] = mapped_column(String(64), nullable=False)
    verified_on: Mapped[str] = mapped_column(String(32), nullable=False)


class ScamRuleORM(Base):
    __tablename__ = "scam_rules"

    rule_id: Mapped[int] = mapped_column(Integer, primary_key=True)
    trigger_pattern: Mapped[str] = mapped_column(String(256), nullable=False)
    severity: Mapped[int] = mapped_column(Integer, nullable=False, default=3)
    lang_code: Mapped[str] = mapped_column(String(12), nullable=False, default="hi")
    explanation_en: Mapped[str] = mapped_column(Text, nullable=False)
    explanation_hi: Mapped[str] = mapped_column(Text, nullable=False)
    action_tip_en: Mapped[str] = mapped_column(Text, nullable=False)
    action_tip_hi: Mapped[str] = mapped_column(Text, nullable=False)
    updated_at: Mapped[str] = mapped_column(String(32), nullable=False)


class UpdatePackageORM(Base):
    __tablename__ = "update_packages"

    package_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    district_code: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    table_name: Mapped[str] = mapped_column(String(32), nullable=False, index=True)
    version: Mapped[int] = mapped_column(Integer, nullable=False)
    rows_payload_json: Mapped[str] = mapped_column(Text, nullable=False)
    created_by_uid: Mapped[str] = mapped_column(String(128), nullable=False, default="system_seed")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utc_now)


class UserORM(Base):
    __tablename__ = "users"

    uid: Mapped[str] = mapped_column(String(64), primary_key=True)
    email: Mapped[str] = mapped_column(String(180), unique=True, nullable=False, index=True)
    full_name: Mapped[str] = mapped_column(String(120), nullable=False)
    password_hash: Mapped[str] = mapped_column(String(256), nullable=False)
    role: Mapped[str] = mapped_column(String(24), nullable=False, default="CITIZEN")
    auth_provider: Mapped[str] = mapped_column(String(32), nullable=False, default="supabase_db")
    is_active: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utc_now, onupdate=_utc_now)


class UserRoleORM(Base):
    __tablename__ = "user_roles"

    uid: Mapped[str] = mapped_column(String(128), primary_key=True)
    email: Mapped[str] = mapped_column(String(180), nullable=True, index=True)
    role: Mapped[str] = mapped_column(String(24), nullable=False, default="CITIZEN")
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utc_now, onupdate=_utc_now)


class AuditLogORM(Base):
    __tablename__ = "audit_logs"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    actor_uid: Mapped[str] = mapped_column(String(128), nullable=False)
    actor_role: Mapped[str] = mapped_column(String(24), nullable=False)
    action: Mapped[str] = mapped_column(String(64), nullable=False)
    target_id: Mapped[str] = mapped_column(String(128), nullable=False)
    details: Mapped[str] = mapped_column(String(512), nullable=False, default="")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utc_now)
