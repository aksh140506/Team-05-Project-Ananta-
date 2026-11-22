from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, sessionmaker

from app.core.config import get_settings


class Base(DeclarativeBase):
    pass


def normalize_database_url(database_url: str) -> str:
    url = (database_url or "").strip()
    if url.startswith("postgres://"):
        url = "postgresql+psycopg2://" + url[len("postgres://"):]
    elif url.startswith("postgresql://"):
        url = "postgresql+psycopg2://" + url[len("postgresql://"):]
    return url


def build_engine(database_url: str):
    normalized_url = normalize_database_url(database_url)
    lower = normalized_url.lower()
    is_sqlite = lower.startswith("sqlite")
    if is_sqlite:
        connect_args = {"check_same_thread": False}
    elif "supabase." in lower and "sslmode=" not in lower:
        connect_args = {"sslmode": "require"}
    else:
        connect_args = {}
    return create_engine(
        normalized_url,
        connect_args=connect_args,
        pool_pre_ping=not is_sqlite,
    )


_settings = get_settings()
engine = build_engine(_settings.database_url)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
