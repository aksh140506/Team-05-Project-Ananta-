import json
from sqlalchemy.orm import Session

from app.db.models import (
    CachedFaqORM,
    FacilityORM,
    ScamRuleORM,
    UpdatePackageORM,
    UserRoleORM,
    VerifiedDocORM,
)


def seed_database_if_empty(db: Session) -> None:
    if db.query(FacilityORM).count() == 0:
        facilities = [
            FacilityORM(
                id="fac_bc_ramesh_chowk",
                name_en="Ramesh Kirana Bank Mitra (BC)",
                name_hi="रमेश किराना बैंक मित्र (बीसी)",
                bank_name="SBI CSP",
                facility_type="BC_POINT",
                village_or_area="Rampur Main Chowk",
                district_code="lucknow_rampur",
                lat=26.8492,
                lon=80.9482,
                is_open=True,
                cash_status="CASH_READY",
                reports_count=12,
                verified_date="2026-11-23",
                verified_by_uid="verifier_seed",
            ),
            FacilityORM(
                id="fac_ippb_rampur_post",
                name_en="Rampur Post Office (IPPB)",
                name_hi="रामपुर डाकघर (IPPB)",
                bank_name="India Post Payments Bank",
                facility_type="IPPB",
                village_or_area="Panchayat Bhavan Road",
                district_code="lucknow_rampur",
                lat=26.8518,
                lon=80.9510,
                is_open=True,
                cash_status="CASH_READY",
                reports_count=8,
                verified_date="2026-11-23",
                verified_by_uid="verifier_seed",
            ),
            FacilityORM(
                id="fac_atm_mandi_gate",
                name_en="Kisan Mandi ATM",
                name_hi="किसान मंडी एटीएम",
                bank_name="Bank of Baroda",
                facility_type="ATM",
                village_or_area="Anaj Mandi Gate",
                district_code="lucknow_rampur",
                lat=26.8575,
                lon=80.9540,
                is_open=True,
                cash_status="LOW_CASH",
                reports_count=5,
                verified_date="2026-11-21",
                verified_by_uid="verifier_seed",
            ),
            FacilityORM(
                id="fac_bc_sharma_csc",
                name_en="Sharma CSC & AePS Kendra",
                name_hi="शर्मा सीएससी और आधार केंद्र",
                bank_name="PNB BC Outlet",
                facility_type="BC_POINT",
                village_or_area="Bus Stand Lane",
                district_code="lucknow_rampur",
                lat=26.8610,
                lon=80.9350,
                is_open=True,
                cash_status="CASH_READY",
                reports_count=2,
                verified_date="2026-11-08",  # > 7 days old for staleness testing
                verified_by_uid="verifier_seed",
            ),
            FacilityORM(
                id="fac_branch_grameen_block",
                name_en="Gramin Vikas Bank Branch",
                name_hi="ग्रामीण विकास बैंक मुख्य शाखा",
                bank_name="Aryavart Gramin Bank",
                facility_type="BRANCH",
                village_or_area="Block Tehsil Headquarters",
                district_code="lucknow_rampur",
                lat=26.8850,
                lon=80.9740,
                is_open=True,
                cash_status="CASH_READY",
                reports_count=19,
                verified_date="2026-11-23",
                verified_by_uid="verifier_seed",
            ),
            FacilityORM(
                id="fac_bbk_dewa_bc",
                name_en="Verma Jan Seva & SBI BC Point",
                name_hi="वर्मा जन सेवा व SBI बैंक मित्र",
                bank_name="SBI CSP",
                facility_type="BC_POINT",
                village_or_area="Dewa Sharif Bazar",
                district_code="barabanki_block",
                lat=26.9295,
                lon=81.1995,
                is_open=True,
                cash_status="CASH_READY",
                reports_count=14,
                verified_date="2026-11-23",
                verified_by_uid="verifier_seed",
            ),
            FacilityORM(
                id="fac_bbk_ippb_post",
                name_en="Dewa Sub Post Office (IPPB)",
                name_hi="देवा उप डाकघर (IPPB)",
                bank_name="India Post Payments Bank",
                facility_type="IPPB",
                village_or_area="Tehsil Road",
                district_code="barabanki_block",
                lat=26.9348,
                lon=81.2030,
                is_open=True,
                cash_status="CASH_READY",
                reports_count=7,
                verified_date="2026-11-23",
                verified_by_uid="verifier_seed",
            ),
            FacilityORM(
                id="fac_stp_biswan_branch",
                name_en="Biswan Regional Gramin Branch",
                name_hi="बिसवां क्षेत्रीय ग्रामीण शाखा",
                bank_name="Aryavart Gramin Bank",
                facility_type="BRANCH",
                village_or_area="Station Road Biswan",
                district_code="sitapur_tehsil",
                lat=27.4975,
                lon=81.0038,
                is_open=True,
                cash_status="CASH_READY",
                reports_count=16,
                verified_date="2026-11-23",
                verified_by_uid="verifier_seed",
            ),
        ]
        db.add_all(facilities)

    if db.query(VerifiedDocORM).count() == 0:
        docs = [
            VerifiedDocORM(
                source_id="RBI_BC_BSBDA_01",
                title="RBI Business Correspondent & Cash Withdrawal Rules",
                lang_code="en",
                topic_tags="withdraw cash bc point bank mitra aeps extra fee commission receipt biometric",
                content=(
                    "For cash withdrawals up to ₹10,000, visit your nearest Bank Mitra (BC Point) with your "
                    "Original Aadhaar Card and registered mobile phone. Under RBI rules, BC agents cannot charge "
                    "any extra fee or commission. Always collect a printed receipt or verify bank SMS."
                ),
                recommended_task="WITHDRAW",
                suggested_amount_inr=3000,
                verified_on="2026-11-23",
            ),
            VerifiedDocORM(
                source_id="RBI_KCC_MASTER_01",
                title="RBI Kisan Credit Card (KCC) Collateral-Free Loan Circular",
                lang_code="en",
                topic_tags="kcc loan agricultural crop khatauni collateral 2 lakh branch",
                content=(
                    "Under RBI guidelines, Kisan Credit Card (KCC) crop loans up to ₹2,00,000 require zero "
                    "collateral security. Visit the Bank Branch with Original Khatauni (plus 2 photocopies), "
                    "Aadhaar + PAN (2 copies each), Passbook, and 3 passport photos."
                ),
                recommended_task="LOAN_KCC",
                suggested_amount_inr=100000,
                verified_on="2026-11-23",
            ),
            VerifiedDocORM(
                source_id="RBI_BSBDA_DBT_02",
                title="PMJDY / Jan Dhan & NPCI DBT Seeding Guide",
                lang_code="en",
                topic_tags="jan dhan pmjdy bsbda zero balance dbt pm-kisan subsidy passbook npci seeding",
                content=(
                    "Jan Dhan (BSBDA) accounts have zero minimum balance requirement and offer at least 4 free "
                    "withdrawals per month. For PM-Kisan or DBT subsidy credit issues, visit your Branch or India "
                    "Post Payments Bank (IPPB) with Aadhaar and Passbook to confirm NPCI Aadhaar mapping."
                ),
                recommended_task="PASSBOOK_DBT",
                suggested_amount_inr=2000,
                verified_on="2026-11-23",
            ),
            VerifiedDocORM(
                source_id="NPCI_IPPB_TRANSFER_03",
                title="IPPB & BC Domestic Money Transfer Rules",
                lang_code="en",
                topic_tags="transfer send money family ippb post office bc point receipt",
                content=(
                    "To send money safely without travelling far, use an India Post Payments Bank (IPPB) post "
                    "office counter or verified BC Point with your Aadhaar card and recipient account/IFSC details. "
                    "Always keep the printed transaction slip."
                ),
                recommended_task="TRANSFER",
                suggested_amount_inr=2000,
                verified_on="2026-11-23",
            ),
            VerifiedDocORM(
                source_id="RBI_ZERO_LIABILITY_1930",
                title="RBI Zero Liability & 1930 Cyber Fraud Helpline",
                lang_code="en",
                topic_tags="scam fraud otp pin kyc link anydesk 1930 cybercrime zero liability",
                content=(
                    "Never share OTP, UPI PIN, ATM PIN, or screen-sharing apps (AnyDesk/TeamViewer). If money "
                    "is deducted or you suspect fraud, call 1930 immediately, report at cybercrime.gov.in, and "
                    "notify your bank within 3 working days for RBI Zero Liability protection."
                ),
                recommended_task=None,
                suggested_amount_inr=0,
                verified_on="2026-11-23",
            ),
        ]
        db.add_all(docs)

    if db.query(CachedFaqORM).count() == 0:
        faqs = [
            CachedFaqORM(
                faq_id=1,
                question="Can a Bank Mitra or BC charge extra fee for withdrawing cash?",
                answer="No. Under RBI Business Correspondent rules, a Bank Mitra cannot demand extra fees or commission. Always take a printed receipt.",
                lang_code="en",
                source_id="RBI_BC_BSBDA_01",
                verified_on="2026-11-23",
            ),
            CachedFaqORM(
                faq_id=2,
                question="क्या बैंक मित्र पैसे निकालने के अलग से पैसे मांग सकता है?",
                answer="नहीं। RBI के नियम के अनुसार बैंक मित्र (BC) ग्राहक से अलग से शुल्क या कमीशन नहीं ले सकता। हमेशा छपी रसीद लें।",
                lang_code="hi",
                source_id="RBI_BC_BSBDA_01",
                verified_on="2026-11-23",
            ),
            CachedFaqORM(
                faq_id=3,
                question="क्या बैंक मित्र पैसे कड्ढने आस्तै बद्ध फीस मंगी सकदा ऐ?",
                answer="नेईं। RBI नियमें मुजब बैंक मित्र (BC) ग्राहक थमां बद्ध फीस जां कमीशन नेईं लेई सकदा। हमेशा छपी रसीद लेओ।",
                lang_code="doi",
                source_id="RBI_BC_BSBDA_01",
                verified_on="2026-11-23",
            ),
            CachedFaqORM(
                faq_id=4,
                question="How much KCC agricultural loan is available without collateral?",
                answer="Under RBI guidelines, Kisan Credit Card (KCC) loans up to ₹2,00,000 do not require collateral security. Visit your Bank Branch with Khatauni and KYC.",
                lang_code="en",
                source_id="RBI_KCC_MASTER_01",
                verified_on="2026-11-23",
            ),
        ]
        db.add_all(faqs)

    if db.query(ScamRuleORM).count() == 0:
        rules = [
            ScamRuleORM(
                rule_id=1,
                trigger_pattern=r"otp|pin|cvv|ओटीपी|पिन",
                severity=3,
                lang_code="hi",
                explanation_en="HIGH RISK: Legitimate banks, RBI, or PM-Kisan officials never ask for your OTP, UPI PIN, or CVV over call or SMS.",
                explanation_hi="खतरा (HIGH RISK): कोई भी असली बैंक, RBI या सरकारी अधिकारी फोन या SMS पर OTP, पिन या CVV कभी नहीं मांगता।",
                action_tip_en="Cut the call immediately. Do NOT share OTP/PIN. Dial 1930 if any money was deducted.",
                action_tip_hi="तुरंत फोन काट दें। OTP/पिन बिल्कुल न बताएं। पैसे कटे हों तो तुरंत 1930 पर कॉल करें।",
                updated_at="2026-11-23",
            ),
            ScamRuleORM(
                rule_id=2,
                trigger_pattern=r"anydesk|teamviewer|quicksupport|\.apk|screen\s*share",
                severity=3,
                lang_code="en",
                explanation_en="HIGH RISK: Remote screen-sharing apps (AnyDesk/TeamViewer) or unknown .apk files let fraudsters empty your bank account.",
                explanation_hi="खतरा (HIGH RISK): AnyDesk/TeamViewer जैसे स्क्रीन शेयरिंग ऐप या अजनबी .apk से ठग आपका खाता खाली कर सकते हैं।",
                action_tip_en="Uninstall the app immediately, turn off mobile data, and call 1930.",
                action_tip_hi="ऐसे ऐप तुरंत हटाएं, इंटरनेट बंद करें और 1930 पर शिकायत दर्ज करें।",
                updated_at="2026-11-23",
            ),
            ScamRuleORM(
                rule_id=3,
                trigger_pattern=r"kyc\s*link|bit\.ly|account\s*block|केवाईसी\s*लिंक|बंद\s*हो|digital\s*arrest",
                severity=3,
                lang_code="hi",
                explanation_en="HIGH RISK: Fake KYC expiry links or 'digital arrest' threats are cyber scams designed to steal banking credentials.",
                explanation_hi="खतरा (HIGH RISK): नकली KYC लिंक या 'डिजिटल अरेस्ट' की धमकी ऑनलाइन ठगी है। असली बैंक लिंक से KYC नहीं कराता।",
                action_tip_en="Do NOT tap the SMS link. Visit your nearest bank branch or dial 1930.",
                action_tip_hi="मैसेज के किसी लिंक पर क्लिक न करें। अपनी बैंक शाखा जाएं या 1930 पर कॉल करें।",
                updated_at="2026-11-23",
            ),
            ScamRuleORM(
                rule_id=4,
                trigger_pattern=r"दो बार अंगूठा|no receipt|बिना रसीद|failed scan|extra fee|commission|अलग से शुल्क",
                severity=2,
                lang_code="hi",
                explanation_en="CAUTION: AePS biometric re-scan without a printed receipt or extra BC commission violates RBI rules.",
                explanation_hi="सावधान (CAUTION): बिना छपी रसीद के दोबारा अंगूठा लगवाना या अतिरिक्त शुल्क मांगना RBI नियमों के खिलाफ है।",
                action_tip_en="Demand a printed receipt, check your bank SMS balance, and report to 14448 / 1930 if debited twice.",
                action_tip_hi="हमेशा छपी रसीद मांगें, बैंक SMS बैलेंस जांचें और गड़बड़ी पर 14448 / 1930 पर बताएं।",
                updated_at="2026-11-23",
            ),
        ]
        db.add_all(rules)

    if db.query(UpdatePackageORM).count() == 0:
        sample_rows = [
            {
                "id": "fac_bc_ramesh_chowk",
                "nameEn": "Ramesh Kirana Bank Mitra (BC)",
                "nameHi": "रमेश किराना बैंक मित्र (बीसी)",
                "bankName": "SBI CSP",
                "facilityType": "BC_POINT",
                "villageOrArea": "Rampur Main Chowk",
                "districtCode": "lucknow_rampur",
                "lat": 26.8492,
                "lon": 80.9482,
                "isOpen": True,
                "cashStatus": "CASH_READY",
                "reportsCount": 12,
                "verifiedDate": "2026-11-23",
            },
            {
                "id": "fac_ippb_rampur_post",
                "nameEn": "Rampur Post Office (IPPB)",
                "nameHi": "रामपुर डाकघर (IPPB)",
                "bankName": "India Post Payments Bank",
                "facilityType": "IPPB",
                "villageOrArea": "Panchayat Bhavan Road",
                "districtCode": "lucknow_rampur",
                "lat": 26.8518,
                "lon": 80.9510,
                "isOpen": True,
                "cashStatus": "CASH_READY",
                "reportsCount": 8,
                "verifiedDate": "2026-11-23",
            },
        ]
        db.add(
            UpdatePackageORM(
                package_id="pkg_lucknow_rampur_fac_v2",
                district_code="lucknow_rampur",
                table_name="FACILITY",
                version=2,
                rows_payload_json=json.dumps(sample_rows, ensure_ascii=False),
                created_by_uid="seed_admin",
            )
        )

    if db.query(UserRoleORM).count() == 0:
        db.add_all(
            [
                UserRoleORM(uid="demo_verifier_uid", email="verifier@banksathi.in", role="VERIFIER"),
                UserRoleORM(uid="demo_admin_uid", email="nikhilsoni8557@gmail.com", role="ADMIN"),
            ]
        )

    db.commit()
