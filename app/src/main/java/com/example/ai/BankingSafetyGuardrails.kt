package com.example.ai

import com.example.data.model.AiSourceMode
import com.example.data.model.AppLanguage
import com.example.data.model.AssistantAnswer
import com.example.data.model.HuskyMood
import com.example.data.repository.BankingRuleEngine

sealed interface InputGuardrailDecision {
    data class Allow(val sanitizedQuery: String, val isScamSensitive: Boolean) : InputGuardrailDecision
    data class Block(val answer: AssistantAnswer) : InputGuardrailDecision
}

object BankingSafetyGuardrails {

    private val injectionPatterns = listOf(
        "ignore previous instruction",
        "ignore all instruction",
        "forget your instruction",
        "disregard above",
        "override system prompt",
        "reveal your prompt",
        "show system prompt",
        "act as a ",
        "pretend you are",
        "jailbreak",
        "dan mode",
        "bypass safety",
        "bypass guardrail",
        "developer mode",
        "<system>",
        "</system>"
    )

    private val asciiBankingAllowRegex = Regex(
        """\b(bank|banking|cash|atm|bc|mitra|aeps|aadhaar|adhar|passbook|kcc|kisan|loan|subsidy|dbt|pm-kisan|pm kisan|pmjdy|jan dhan|bsbda|balance|account|ifsc|ippb|post office|branch|deposit|transfer|withdraw|money|rupee|rupees|rs|upi|bhim|phonepe|gpay|paytm|pin|otp|cvv|kyc|scam|fraud|receipt|thumb|fingerprint|biometric|1930|14448|155299|rbi|nabard|cheque|khatauni|pan|photo|document|documents|paper|papers|fee|charge|commission|bag|facility|center|centre|nearest|location|pincode|pin code|zip|open|close|closed|timing|timings|helpline|ombudsman|anydesk|teamviewer|link|sms|call|interest|crop|limit|nominee|pension|ration|scheme|yojana|installment|npci|seeding|overdraft|debit|card|rupay|dormant|unfreeze|freeze|simpler|simply|carry|bring|namaste|hello)\b"""
    )

    private val indicOrSymbolAllowKeywords = listOf(
        "₹",
        // Hindi Devanagari & Roman
        "बैंक", "पैसे", "रुपये", "रुपए", "कैश", "नकद", "निकासी", "निकाल", "भेज", "जमा",
        "आधार", "पासबुक", "लोन", "कर्ज", "ऋण", "केसीसी", "किसान", "सब्सिडी", "किस्त",
        "बैलेंस", "खाता", "शाखा", "डाकघर", "एटीएम", "मित्र", "अंगूठा", "रसीद", "कागज़",
        "कागज", "दस्तावेज", "ओटीपी", "पिन", "ठगी", "फ्रॉड", "धोखा", "लिंक", "शुल्क",
        "खतौनी", "पैन", "फ़ोटो", "फोटो", "सुरक्षा", "मदद", "सहायता", "आसान", "नमस्ते",
        "पिनकोड", "नजदीकी", "केंद्र", "बायोमेट्रिक", "सीडिंग", "यूपीआई", "जन धन", "ओवरड्राफ्ट",
        "नॉमिनी", "चेक", "डेबिट", "कार्ड", "पेंशन",
        "paisa", "paise", "nikal", "bhej", "jama", "khata", "kist", "karz", "raseed", "thagi", "angootha",
        // Dogri
        "कड्ढ", "कड्ढो", "मिल्लेआ", "नेईं", "रक्खो", "दस्सो", "केंदर", "किश्त", "साढ़ी", "बियाज", "नेड़े"
    )

    private val clearOutOfScopeTopics = listOf(
        "recipe", "biryani", "pasta", "cricket", "ipl", "virat", "rohit", "movie", "song",
        "lyrics", "poem", "essay", "homework", "python", "java code", "javascript", "kotlin code",
        "html", "sql query", "algorithm", "math problem", "calculus", "politics", "election",
        "vote for", "celebrity", "astrology", "horoscope", "diagnose", "prescription", "antibiotic",
        "dosage", "tablet for", "stock market", "crypto", "bitcoin", "gambling", "betting",
        "weapon", "bomb", "hack wifi", "crack password",
        "क्रिकेट", "फिल्म", "गाना", "कविता", "निबंध", "रेसिपी", "बिरयानी", "ज्योतिष", "राशिफल",
        "राजनीति", "चुनाव", "दवाई", "गोली", "बुखार की दवा", "बिटकॉइन", "जुआ"
    )

    private val unsafeAdviceMarkers = listOf(
        "share your otp",
        "tell them your otp",
        "give your pin",
        "enter your pin to receive",
        "pay extra fee to bc",
        "ओटीपी बता दें",
        "पिन बता दें",
        "पैसे पाने के लिए पिन डालें",
        "ओटीपी दस्सी देओ"
    )

    private val aadhaarDigitsRegex = Regex("""\b\d{4}[\s-]\d{4}[\s-]\d{4}\b""")
    private val longAccountRegex = Regex("""\b\d{11,16}\b""")
    private val panRegex = Regex("""\b[A-Z]{5}\d{4}[A-Z]\b""")

    fun inspectInput(rawQuery: String): InputGuardrailDecision {
        val cleaned = rawQuery.trim()
        if (cleaned.isBlank()) {
            return InputGuardrailDecision.Block(BankingRuleEngine.abstainAnswer())
        }

        val lower = cleaned.lowercase()

        // 1. Prompt-Injection / Role-Override Guardrail
        if (injectionPatterns.any { lower.contains(it) }) {
            return InputGuardrailDecision.Block(
                AssistantAnswer(
                    replyEn = "I can only follow verified BankSathi rural banking and scam-safety rules. Please ask about cash withdrawal, KCC loans, PM-Kisan passbooks, or suspicious SMS checks.",
                    replyHi = "मैं केवल सत्यापित बैंकसाथी ग्रामीण बैंकिंग और सुरक्षा नियमों का पालन करता हूँ। कृपया पैसे निकासी, केसीसी लोन, पासबुक या स्कैम जांच से जुड़ा सवाल पूछें।",
                    replyDoi = "मैं सिर्फ पक्के बैंकसाथी ग्राएँ बैंकिंग ते सुरक्षा नियमें दा पालन करदां। कृपा करियै पैसे कड्ढने, KCC लोन, पासबुक जां ठगी जांच बारे पुच्छो।",
                    recommendedTask = null,
                    suggestedAmountInr = 0,
                    sourceMode = AiSourceMode.OFFLINE_HEURISTIC,
                    mood = HuskyMood.ALERT,
                    abstained = true,
                    sourceId = "GUARDRAIL_INJECTION_BLOCK",
                    verifiedOn = "2026-11-23"
                )
            )
        }

        val isScam = BankingRuleEngine.isImmediateScamPhrase(cleaned)
        val hasBankingSignal = asciiBankingAllowRegex.containsMatchIn(lower) ||
            indicOrSymbolAllowKeywords.any { lower.contains(it) } ||
            Regex("""\d[\d,]{1,8}""").containsMatchIn(lower)
        val isClearlyOutOfScope = clearOutOfScopeTopics.any { lower.contains(it) }

        // 2. Strict Banking-Only Scope Guardrail (when no banking signal & off-topic, or long multi-word non-banking query)
        if ((isClearlyOutOfScope && !hasBankingSignal) ||
            (!hasBankingSignal && cleaned.split(Regex("\\s+")).size >= 4 && !isScam)
        ) {
            return InputGuardrailDecision.Block(
                AssistantAnswer(
                    replyEn = "BankSathi is strictly for rural banking, government DBT/passbook, KCC loans, and fraud protection (1930). Please ask a banking or scam-safety question.",
                    replyHi = "बैंकसाथी केवल ग्रामीण बैंकिंग, पासबुक/सरकारी किस्त, केसीसी लोन और ठगी सुरक्षा (1930) के लिए है। कृपया बैंकिंग या सुरक्षा से जुड़ा सवाल पूछें।",
                    replyDoi = "बैंकसाथी सिर्फ ग्राएँ बैंकिंग, पासबुक/किस्त, KCC लोन ते ठगी सुरक्षा (1930) आस्तै ऐ। कृपा करियै बैंकिंग जां सुरक्षा बारे पुच्छो।",
                    recommendedTask = null,
                    suggestedAmountInr = 0,
                    sourceMode = AiSourceMode.OFFLINE_HEURISTIC,
                    mood = HuskyMood.GUIDING,
                    abstained = true,
                    sourceId = "GUARDRAIL_SCOPE_ONLY",
                    verifiedOn = "2026-11-23"
                )
            )
        }

        return InputGuardrailDecision.Allow(
            sanitizedQuery = cleaned.take(600),
            isScamSensitive = isScam
        )
    }

    fun sanitizeAndVerifyOutput(
        rawReply: String,
        language: AppLanguage,
        isScamContext: Boolean
    ): Pair<String, Boolean> {
        var text = rawReply
            .replace("<end_of_turn>", "")
            .replace("<start_of_turn>model", "")
            .replace("<start_of_turn>user", "")
            .replace("**", "")
            .replace("###", "")
            .replace("##", "")
            .trim()

        if (text.isBlank()) {
            val abstained = BankingRuleEngine.abstainAnswer()
            return when (language) {
                AppLanguage.ENGLISH -> abstained.replyEn
                AppLanguage.HINDI -> abstained.replyHi
                AppLanguage.DOGRI -> abstained.replyDoi.ifBlank { abstained.replyHi }
            } to true
        }

        // Mask echoed Aadhaar / Account numbers / PAN for privacy
        text = aadhaarDigitsRegex.replace(text) { m ->
            val digits = m.value.filter { it.isDigit() }
            "XXXX-XXXX-${digits.takeLast(4)}"
        }
        text = longAccountRegex.replace(text) { m ->
            "XXXX${m.value.takeLast(4)}"
        }
        text = panRegex.replace(text, "XXXXX****X")

        val lower = text.lowercase()
        val hasUnsafeInstruction = unsafeAdviceMarkers.any { lower.contains(it) }
        if (hasUnsafeInstruction) {
            val safe = when (language) {
                AppLanguage.ENGLISH -> "Safety Correction: Never share your OTP, UPI PIN, or pay extra commission at a BC counter. Call 1930 immediately if pressured."
                AppLanguage.HINDI -> "सुरक्षा सुधार: कभी भी OTP या UPI PIN न बताएं और न ही अतिरिक्त शुल्क दें। संदेह होने पर तुरंत 1930 पर कॉल करें।"
                AppLanguage.DOGRI -> "सुरक्षा सुधार: कदें बी OTP जां UPI PIN नेईं दस्सो ते ना ही बद्ध फीस देओ। शक होए तां फौरन 1930 पर कॉल करो।"
            }
            return safe to true
        }

        if (isScamContext && !text.contains("1930")) {
            val suffix = when (language) {
                AppLanguage.ENGLISH -> "\n\nSafety Rule: Never share OTP/PIN. Call 1930 within 3 working days for RBI Zero Liability."
                AppLanguage.HINDI -> "\n\nसुरक्षा नियम: कभी OTP/PIN न बताएं। ठगी होने पर तुरंत 1930 पर कॉल करें।"
                AppLanguage.DOGRI -> "\n\nसुरक्षा नियम: कदें OTP/PIN नेईं दस्सो। ठगी होए तां फौरन 1930 पर कॉल करो।"
            }
            text += suffix
        }

        return text to false
    }
}
