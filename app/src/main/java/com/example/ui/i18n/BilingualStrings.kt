package com.example.ui.i18n

import com.example.data.model.AiSourceMode
import com.example.data.model.AppLanguage
import com.example.data.model.BankingTask
import com.example.data.model.CashStatus
import com.example.data.model.FacilityType
import com.example.data.model.ReportStatus
import com.example.data.model.RiskLevel

object BilingualStrings {
    fun pick(language: AppLanguage, en: String, hi: String): String = when (language) {
        AppLanguage.ENGLISH -> en
        AppLanguage.HINDI -> hi
        AppLanguage.DOGRI -> hi
    }

    fun pick(language: AppLanguage, en: String, hi: String, doi: String): String = when (language) {
        AppLanguage.ENGLISH -> en
        AppLanguage.HINDI -> hi
        AppLanguage.DOGRI -> doi.ifBlank { hi }
    }

    fun taskTitle(task: BankingTask, lang: AppLanguage): String = when (task) {
        BankingTask.WITHDRAW -> pick(lang, "Cash Out", "पैसे निकालें", "पैसे कड्ढो")
        BankingTask.TRANSFER -> pick(lang, "Send Money", "पैसे भेजें", "पैसे भेजो")
        BankingTask.PASSBOOK_DBT -> pick(lang, "Passbook & DBT", "पासबुक और किस्त", "पासबुक ते किस्त")
        BankingTask.LOAN_KCC -> pick(lang, "Loan & KCC", "लोन और केसीसी", "लोन ते केसीसी")
    }

    fun taskSubtitle(task: BankingTask, lang: AppLanguage): String = when (task) {
        BankingTask.WITHDRAW -> pick(lang, "BC Agent, ATM or Branch", "बीसी केंद्र, एटीएम या बैंक", "बीसी केंद्र, एटीएम जां बैंक")
        BankingTask.TRANSFER -> pick(lang, "Post Office (IPPB) or BC", "डाकघर या बैंक मित्र", "डाकघर जां बैंक मित्र")
        BankingTask.PASSBOOK_DBT -> pick(lang, "Balance, PM-Kisan & print", "बैलेंस, सब्सिडी और प्रिंट", "बैलेंस, सब्सिडी ते प्रिंट")
        BankingTask.LOAN_KCC -> pick(lang, "Kisan Credit & papers", "किसान कार्ड और कागज़", "किसान कार्ड ते कागज़")
    }

    fun facilityTypeLabel(type: FacilityType, lang: AppLanguage): String = when (type) {
        FacilityType.BC_POINT -> pick(lang, "Bank Mitra (BC)", "बैंक मित्र (बीसी)", "बैंक मित्र (BC)")
        FacilityType.IPPB -> pick(lang, "Post Office (IPPB)", "डाकघर (IPPB)", "डाकघर (IPPB)")
        FacilityType.ATM -> pick(lang, "ATM Machine", "एटीएम मशीन", "ATM मशीन")
        FacilityType.BRANCH -> pick(lang, "Bank Branch", "बैंक शाखा", "बैंक शाखा")
    }

    fun cashStatusLabel(isOpen: Boolean, status: CashStatus, lang: AppLanguage): String {
        if (status == CashStatus.UNKNOWN) {
            return pick(lang, "Unknown • Verification Overdue", "अज्ञात • सत्यापन पुराना है", "अज्ञात • जांच पुराणी ऐ")
        }
        if (!isOpen) return pick(lang, "Closed / No Cash", "बंद / कैश नहीं", "बंद / कैश नेईं")
        return when (status) {
            CashStatus.CASH_READY -> pick(lang, "Open • Cash Ready", "खुला है • कैश उपलब्ध", "खुल्ला ऐ • कैश तैयार ऐ")
            CashStatus.LOW_CASH -> pick(lang, "Open • Long Line / Low", "खुला • भीड़ / कम कैश", "खुल्ला • भीड़ / कट्ट कैश")
            CashStatus.NO_CASH -> pick(lang, "No Cash Available", "कैश खत्म है", "कैश मुक्की गेआ")
            CashStatus.UNKNOWN -> pick(lang, "Unknown • Verification Overdue", "अज्ञात • सत्यापन पुराना है", "अज्ञात • जांच पुराणी ऐ")
        }
    }

    fun reportStatusLabel(status: ReportStatus, lang: AppLanguage): String = when (status) {
        ReportStatus.GOT_CASH -> pick(lang, "Got Cash", "कैश मिला", "कैश मिल्लेआ")
        ReportStatus.LONG_LINE -> pick(lang, "Long Line", "लंबी लाइन", "लम्मी लाइन")
        ReportStatus.NO_CASH_CLOSED -> pick(lang, "No Cash / Shut", "कैश नहीं / बंद", "कैश नेईं / बंद")
    }

    fun riskLabel(level: RiskLevel, lang: AppLanguage): String = when (level) {
        RiskLevel.SAFE -> pick(lang, "Looks Safe", "सुरक्षित लग रहा है", "सुरक्षित लग्गा दा ऐ")
        RiskLevel.CAUTION -> pick(lang, "Be Careful", "सावधान रहें", "ध्यान रक्खो")
        RiskLevel.HIGH_RISK -> pick(lang, "Scam Alert!", "धोखाधड़ी खतरा!", "ठगी दा खतरा!")
    }

    fun sourceLabel(mode: AiSourceMode, lang: AppLanguage): String = when (mode) {
        AiSourceMode.GEMMA_LOCAL -> pick(lang, "Gemma-2B Offline Chat", "जेमा-2B ऑफलाइन चैट", "जेमा-2B ऑफलाइन चैट")
        AiSourceMode.FASTAPI_RAG -> pick(lang, "FastAPI + ChromaDB RAG", "FastAPI + ChromaDB RAG", "FastAPI + ChromaDB RAG")
        AiSourceMode.GEMINI_AI -> pick(lang, "Gemini Live Chat", "जेमिनी लाइव चैट", "जेमिनी लाइव चैट")
        AiSourceMode.OFFLINE_HEURISTIC -> pick(lang, "Verified Offline Rules", "सत्यापित ऑफलाइन नियम", "पक्के ऑफलाइन नियम")
    }
}
