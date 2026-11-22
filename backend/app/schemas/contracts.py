from typing import Dict, List, Literal, Optional
from pydantic import BaseModel, Field, field_validator


class RagHistoryTurnDto(BaseModel):
    role: Literal["user", "assistant", "model"]
    text: str = Field(..., min_length=1, max_length=600)


class RagAskRequest(BaseModel):
    query: str = Field(..., min_length=1, max_length=600)
    lang: Literal["en", "hi", "doi"] = "en"
    district_code: str = Field(default="lucknow_rampur", min_length=2, max_length=64, pattern=r"^[a-zA-Z0-9_-]+$")
    history: List[RagHistoryTurnDto] = Field(default_factory=list, max_length=8)


class RagAskResponse(BaseModel):
    answer_en: str
    answer_hi: str
    source_id: Optional[str] = None
    verified_on: Optional[str] = None
    abstained: bool = False
    recommended_task: Optional[str] = None
    suggested_amount_inr: int = 0


class RemoteFacilityDto(BaseModel):
    id: str
    name_en: str
    name_hi: Optional[str] = None
    bank_name: str = "Gramin Vikas Bank"
    facility_type: str = "BC_POINT"
    village_or_area: str = ""
    district_code: str = "lucknow_rampur"
    distance_meters: int = 0
    lat: float
    lon: float
    is_open: bool = True
    cash_status: str = "CASH_READY"
    reports_count: int = 0
    verified_date: str = ""


class NearbyFacilitiesResponse(BaseModel):
    district_code: str
    facilities: List[RemoteFacilityDto] = Field(default_factory=list)


class DeltaSyncRequest(BaseModel):
    district_code: str = Field(default="lucknow_rampur", min_length=2, max_length=64, pattern=r"^[a-zA-Z0-9_-]+$")
    since_versions: Dict[str, int] = Field(default_factory=dict)

    @field_validator("since_versions")
    @classmethod
    def check_versions_non_negative(cls, v: Dict[str, int]) -> Dict[str, int]:
        for tbl, ver in v.items():
            if ver < 0 or ver > 1_000_000:
                raise ValueError(f"Invalid version number for table '{tbl}'.")
        return v


class SignedUpdatePackageDto(BaseModel):
    package_id: str
    district_code: str
    table_name: str
    version: int
    rows_payload_json: str
    signature: str


class DeltaSyncResponse(BaseModel):
    packages: List[SignedUpdatePackageDto] = Field(default_factory=list)


class RemoteScamCheckRequest(BaseModel):
    snippet: str = Field(..., min_length=1, max_length=800)
    lang: Literal["en", "hi", "doi"] = "en"


class RemoteScamCheckResponse(BaseModel):
    risk_level: Literal["SAFE", "CAUTION", "HIGH_RISK"]
    explanation_en: str
    explanation_hi: str
    action_tip_en: str
    action_tip_hi: str


class AuthMeResponse(BaseModel):
    uid: str
    email: Optional[str] = None
    full_name: Optional[str] = None
    role: Literal["CITIZEN", "VERIFIER", "ADMIN"]
    auth_provider: str = "firebase_jwt"
    permissions: List[str]


class AuthRegisterRequest(BaseModel):
    email: str = Field(..., min_length=5, max_length=180, pattern=r"^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$")
    password: str = Field(..., min_length=8, max_length=128)
    full_name: str = Field(..., min_length=2, max_length=120)

    @field_validator("email")
    @classmethod
    def normalize_email(cls, v: str) -> str:
        return v.strip().lower()


class AuthLoginRequest(BaseModel):
    email: str = Field(..., min_length=5, max_length=180, pattern=r"^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$")
    password: str = Field(..., min_length=1, max_length=128)

    @field_validator("email")
    @classmethod
    def normalize_email(cls, v: str) -> str:
        return v.strip().lower()


class AuthTokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    expires_in: int
    user: AuthMeResponse


class VerifyFacilityRequest(BaseModel):
    facility_id: str = Field(..., min_length=2, max_length=64, pattern=r"^[a-zA-Z0-9_-]+$")
    cash_status: Literal["CASH_READY", "LOW_CASH", "NO_CASH"] = "CASH_READY"
    is_open: bool = True
    notes: str = Field(default="", max_length=250)


class VerifyFacilityResponse(BaseModel):
    facility: RemoteFacilityDto
    verified_by: str
    verified_date: str
    message: str


class PublishSyncPackageRequest(BaseModel):
    package_id: str = Field(..., min_length=3, max_length=64, pattern=r"^[a-zA-Z0-9_-]+$")
    district_code: str = Field(..., min_length=2, max_length=64, pattern=r"^[a-zA-Z0-9_-]+$")
    table_name: Literal["FACILITY", "CACHED_FAQ", "SCAM_RULE", "CHECKLIST"]
    version: int = Field(..., ge=1, le=1_000_000)
    rows_payload_json: str = Field(..., min_length=2, max_length=20000)


class AuditLogEntryDto(BaseModel):
    id: int
    actor_uid: str
    actor_role: str
    action: str
    target_id: str
    details: str
    created_at: str
