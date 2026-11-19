package com.example.data.repository

import com.example.data.model.AiSourceMode
import com.example.data.model.AssistantAnswer
import com.example.data.model.BankingTask
import com.example.data.model.CachedFaqRecord
import com.example.data.model.DocumentRequirement
import com.example.data.model.FacilityType
import com.example.data.model.HuskyMood
import com.example.data.model.RiskLevel
import com.example.data.model.RoutingRecommendation
import com.example.data.model.ScamAnalysisResult

object BankingRuleEngine {

    private const val VERIFIED_RULES_DATE = "2026-11-23"

    private val docAadhaar = DocumentRequirement(
        id = "aadhaar_original",
        titleEn = "Original Aadhaar Card",
        titleHi = "असली आधार कार्ड",
        noteEn = "Required for biometric thumb scan; phone photo may be rejected.",
        noteHi = "अंगूठे के निशान (बायोमेट्रिक) के लिए असली कार्ड ज़रूरी है।",
        isOriginalRequired = true,
        titleDoi = "असली आधार कार्ड",
        noteDoi = "अंगूठे दी जांच (बायोमेट्रिक) आस्तै असली कार्ड ज़रूरी ऐ; फोन दी फोटो नेईं चलदी।"
    )

    private val docPhone = DocumentRequirement(
        id = "registered_phone",
        titleEn = "Registered Mobile Phone",
        titleHi = "बैंक से जुड़ा मोबाइल फोन",
        noteEn = "Needed to receive SMS balance confirmation and OTP.",
        noteHi = "बैलेंस मैसेज और ओटीपी प्राप्त करने के लिए।",
        isOriginalRequired = true,
        titleDoi = "बैंक कन्नै जुड़या मोबाइल फोन",
        noteDoi = "बैलेंस SMS ते OTP औने आस्तै मोबाइल फोन कन्नै रक्खो।"
    )

    private val docPassbook = DocumentRequirement(
        id = "bank_passbook",
        titleEn = "Physical Bank Passbook",
        titleHi = "बैंक पासबुक",
        noteEn = "Helps verify account number, IFSC, and print entries.",
        noteHi = "खाता नंबर, आईएफएससी और एंट्री छपवाने के लिए।",
        isOriginalRequired = true,
        titleDoi = "बैंक पासबुक",
        noteDoi = "खाता नंबर, IFSC ते एंट्री छपाने आस्तै जरूरी ऐ।"
    )

    private val docDebitCard = DocumentRequirement(
        id = "debit_card",
        titleEn = "ATM / RuPay Debit Card",
        titleHi = "एटीएम / रुपे डेबिट कार्ड",
        noteEn = "Carry card if using an ATM instead of a bank counter.",
        noteHi = "एटीएम मशीन से पैसे निकालने के लिए कार्ड साथ रखें।",
        isOriginalRequired = true,
        titleDoi = "ATM / रुपे डेबिट कार्ड",
        noteDoi = "ATM मशीन चा पैसे कड्ढने आस्तै कार्ड कन्नै लेई जाओ।"
    )

    private val docReceiverDetails = DocumentRequirement(
        id = "receiver_details",
        titleEn = "Receiver Account & IFSC Note",
        titleHi = "पाने वाले का खाता नंबर और IFSC",
        noteEn = "Write the recipient's account number and IFSC clearly on paper.",
        noteHi = "कागज पर खाता नंबर और IFSC कोड साफ लिखकर ले जाएं।",
        isOriginalRequired = false,
        titleDoi = "लैने आह्ले दा खाता नंबर ते IFSC",
        noteDoi = "कागज पर खाता नंबर ते IFSC कोड साफ लिखी लेई जाओ।"
    )

    private val docLandRecord = DocumentRequirement(
        id = "land_khatauni",
        titleEn = "Land Record (Khatauni / 7-12)",
        titleHi = "खतौनी / जमीन के कागज़",
        noteEn = "Carry original plus 2 photocopies before leaving home.",
        noteHi = "असली खतौनी और 2 फोटोकॉपी घर से ही लेकर चलें।",
        isOriginalRequired = true,
        copiesNeeded = 2,
        titleDoi = "खतौनी / ज़मीन दे कागज़",
        noteDoi = "असली खतौनी ते 2 फोटोकॉपी घरा थमां ही कन्नै लेई चलो।"
    )

    private val docPhotos = DocumentRequirement(
        id = "passport_photos",
        titleEn = "3 Passport-Size Photos",
        titleHi = "3 पासपोर्ट साइज फोटो",
        noteEn = "Required on KCC loan application forms at the branch.",
        noteHi = "केसीसी लोन फॉर्म पर लगाने के लिए 3 फोटो।",
        isOriginalRequired = true,
        copiesNeeded = 3,
        titleDoi = "3 पासपोर्ट साइज फोटो",
        noteDoi = "KCC लोन फॉर्म पर लाने आस्तै 3 फोटो कन्नै रक्खो।"
    )

    private val docAadhaarCopy = DocumentRequirement(
        id = "aadhaar_copies",
        titleEn = "Aadhaar + PAN (2 Photocopies)",
        titleHi = "आधार + पैन की 2 फोटोकॉपी",
        noteEn = "Keep photocopies ready; rural branches often have long copier lines.",
        noteHi = "पहले से 2 कॉपी करा कर रखें ताकि लाइन में न लगना पड़े।",
        isOriginalRequired = false,
        copiesNeeded = 2,
        titleDoi = "आधार + पैन दी 2 फोटोकॉपी",
        noteDoi = "पैहले ही 2 कॉपी करा रक्खो तां जे लाइन च नेईं लग्गना पवे।"
    )

    fun routeTask(task: BankingTask, amountInr: Int = 2000): RoutingRecommendation {
        return when (task) {
            BankingTask.WITHDRAW -> {
                if (amountInr <= 10_000) {
                    RoutingRecommendation(
                        task = task,
                        amountInr = amountInr,
                        facilityType = FacilityType.BC_POINT,
                        reasonEn = "For ₹$amountInr, your nearest Bank Mitra (BC Point) is fastest via Aadhaar thumb scan—no extra fee is allowed under RBI rules.",
                        reasonHi = "₹$amountInr निकालने के लिए पास का बैंक मित्र (बीसी केंद्र) सबसे तेज़ है। RBI नियम के तहत बैंक मित्र अलग से शुल्क नहीं ले सकता।",
                        reasonDoi = "₹$amountInr कड्ढने आस्तै तुंदा नेड़े दा बैंक मित्र (BC केंद्र) सबतों तेज़ ऐ। RBI नियम मुजब बैंक मित्र बद्ध फीस नेईं लेई सकदा।",
                        documents = listOf(docAadhaar, docPhone)
                    )
                } else {
                    RoutingRecommendation(
                        task = task,
                        amountInr = amountInr,
                        facilityType = FacilityType.BRANCH,
                        reasonEn = "Amounts above ₹10,000 exceed standard AePS BC limits. Visit a Bank Branch or ATM with your passbook and card.",
                        reasonHi = "₹10,000 से अधिक राशि बीसी केंद्र की सीमा से ऊपर है। पासबुक और कार्ड लेकर बैंक शाखा या एटीएम जाएं।",
                        reasonDoi = "₹10,000 थमां मती रकम BC केंद्र दी सीमा थमां उप्पर ऐ। पासबुक ते कार्ड लेई के बैंक शाखा जां ATM जाओ।",
                        documents = listOf(docPassbook, docAadhaar, docDebitCard)
                    )
                }
            }

            BankingTask.TRANSFER -> RoutingRecommendation(
                task = task,
                amountInr = amountInr,
                facilityType = FacilityType.IPPB,
                reasonEn = "Post Office (IPPB) or Bank Mitra handles domestic cash deposits and transfers quickly—always take a printed receipt.",
                reasonHi = "पैसे भेजने या जमा करने के लिए नजदीकी डाकघर (IPPB) या बैंक मित्र केंद्र जाएं और छपी रसीद जरूर लें।",
                reasonDoi = "पैसे भेजने जां जमा करने आस्तै नेड़े दे डाकघर (IPPB) जां बैंक मित्र केंद्र जाओ ते छपी रसीद ज़रूर लेओ।",
                documents = listOf(docAadhaar, docReceiverDetails, docPhone)
            )

            BankingTask.PASSBOOK_DBT -> RoutingRecommendation(
                task = task,
                amountInr = 0,
                facilityType = FacilityType.BC_POINT,
                reasonEn = "Check PM-Kisan / DBT balance via mini-statement at a BC Point, or visit the Branch to print your physical passbook.",
                reasonHi = "सरकारी किस्त या बैलेंस की जांच बीसी केंद्र पर तुरंत हो जाती है। पासबुक प्रिंट के लिए बैंक शाखा जाएं।",
                reasonDoi = "सरकारी किस्त जां बैलेंस दी जांच BC केंद्र पर फौरन होई जंदी ऐ। पासबुक प्रिंट कराणे आस्तै बैंक शाखा जाओ।",
                documents = listOf(docAadhaar, docPassbook, docPhone)
            )

            BankingTask.LOAN_KCC -> RoutingRecommendation(
                task = task,
                amountInr = amountInr,
                facilityType = FacilityType.BRANCH,
                reasonEn = "Kisan Credit Card (KCC) and agricultural loans up to ₹2,00,000 are collateral-free under RBI norms and must be processed at a Bank Branch.",
                reasonHi = "RBI नियमों के तहत ₹2,00,000 तक का केसीसी (KCC) कृषि लोन बिना गारंटी के मुख्य बैंक शाखा से मिलता है।",
                reasonDoi = "RBI नियमें मुजब ₹2,00,000 तक्कर दा KCC खेती लोन बिना गिरवी दे मुख्य बैंक शाखा चा मिल्दा ऐ।",
                documents = listOf(docLandRecord, docAadhaar, docAadhaarCopy, docPassbook, docPhotos)
            )
        }
    }

    /**
     * Quick local ScamGuard keyword check executed BEFORE any answer lookup,
     * matching the Team 05 BankSathi workflow.
     */
    fun isImmediateScamPhrase(rawInput: String): Boolean {
        val text = rawInput.lowercase()
        val triggers = listOf(
            "otp", "cvv", "share pin", "tell pin", "give pin", "asked pin", "asking pin",
            "asked for pin", "enter pin", "send pin", "pin number",
            "anydesk", "teamviewer", "quicksupport", "rustdesk",
            "bit.ly", "tinyurl", ".apk", "lottery", "prize", "kyc link", "blocked link",
            "double thumb", "दो बार अंगूठा", "दो बारी अंगूठा", "ओटीपी",
            "पिन बता", "पिन पूछ", "पिन मांग", "पिन दस्सो", "पिन पाओ", "पिन नंबर",
            "ठगी", "फ्रॉड", "लॉटरी", "लिंक दबाएं", "स्क्रीन शेयर", "1930", "रसीद नेईं", "बिना रसीद"
        )
        return triggers.any { text.contains(it) }
    }

    private val stopWords = setOf(
        "is", "am", "are", "was", "were", "be", "been", "to", "of", "in", "on", "at", "by",
        "for", "with", "from", "the", "an", "and", "or", "if", "it", "my", "me", "we", "you",
        "your", "do", "does", "did", "can", "could", "will", "would", "should", "how", "what",
        "when", "where", "which", "who", "why", "this", "that", "there", "here", "get", "have",
        "has", "had", "not", "no", "yes", "please", "tell",
        "क्या", "है", "हैं", "हो", "हूँ", "मैं", "मेरा", "मेरे", "मेरी", "आप", "हम", "को", "का", "की", "के",
        "में", "पर", "से", "ने", "और", "या", "तो", "कैसे", "कब", "कहाँ", "क्यों", "कौन", "कितना",
        "किन्ने", "ऐ", "न", "च", "ते", "दा", "दी", "दे", "थमां", "आस्तै", "होई", "सकदा", "करियै"
    )

    private fun extractMeaningfulTokens(rawQuery: String): Set<String> =
        rawQuery.lowercase()
            .split(Regex("[\\s,?.!।:/()\"'\\-]+"))
            .map { it.trim() }
            .filter { it.length >= 2 && it !in stopWords }
            .toSet()

    private fun scoreFaqMatch(tokens: Set<String>, faq: CachedFaqRecord): Int {
        if (tokens.isEmpty()) return 0
        val qLower = faq.question.lowercase()
        val aLower = faq.answer.lowercase()
        var score = 0
        for (tok in tokens) {
            if (qLower.contains(tok)) score += 3
            else if (aLower.contains(tok)) score += 1
        }
        return score
    }

    fun findBestCachedFaqMatch(
        rawQuery: String,
        faqs: List<CachedFaqRecord>
    ): Pair<CachedFaqRecord?, CachedFaqRecord?> {
        if (faqs.isEmpty()) return null to null
        val tokens = extractMeaningfulTokens(rawQuery)
        if (tokens.isEmpty()) return null to null

        val enFaqs = faqs.filter { it.langCode == "en" }
        val hiFaqs = faqs.filter { it.langCode == "hi" }

        var bestMatch: CachedFaqRecord? = null
        var bestScore = 0
        for (faq in faqs) {
            val score = scoreFaqMatch(tokens, faq)
            if (score > bestScore) {
                bestScore = score
                bestMatch = faq
            }
        }
        val minRequiredScore = if (tokens.size == 1) 3 else 3
        if (bestMatch == null || bestScore < minRequiredScore) return null to null

        val enFaq = enFaqs.firstOrNull { it.sourceId == bestMatch.sourceId } ?: bestMatch
        val hiFaq = hiFaqs.firstOrNull { it.sourceId == bestMatch.sourceId } ?: bestMatch
        return enFaq to hiFaq
    }

    fun findDogriFaqMatch(
        rawQuery: String,
        faqs: List<CachedFaqRecord>
    ): CachedFaqRecord? {
        val doiFaqs = faqs.filter { it.langCode == "doi" }
        if (doiFaqs.isEmpty()) return null
        val tokens = extractMeaningfulTokens(rawQuery)
        if (tokens.isEmpty()) return null

        var bestMatch: CachedFaqRecord? = null
        var bestScore = 0
        for (faq in doiFaqs) {
            val score = scoreFaqMatch(tokens, faq)
            if (score > bestScore) {
                bestScore = score
                bestMatch = faq
            }
        }
        return if (bestMatch != null && bestScore >= 3) bestMatch else null
    }

    fun analyzeOfflineQuery(
        rawQuery: String,
        cachedFaqs: List<CachedFaqRecord> = emptyList()
    ): AssistantAnswer {
        val q = rawQuery.lowercase().trim()
        val amountMatch = Regex("""\d[\d,]{1,7}""")
            .find(q)
            ?.value
            ?.replace(",", "")
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: 2000

        // 1. ScamGuard check BEFORE any banking answer
        if (isImmediateScamPhrase(q) || listOf("scam", "fraud", "cheat", "stolen", "धोखा", "ठगी").any { q.contains(it) }) {
            return AssistantAnswer(
                replyEn = "ScamGuard Alert: Never share your OTP, UPI PIN, screen-share apps, or scan your thumb twice without a receipt. Call 1930 immediately if cheated.",
                replyHi = "स्कैम गार्ड चेतावनी: कभी भी OTP, PIN, स्क्रीन-शेयर ऐप न दें और बिना रसीद दो बार अंगूठा न लगाएं। ठगी होने पर तुरंत 1930 पर कॉल करें।",
                replyDoi = "ठगी चेतावनी: कदें बी OTP, PIN, स्क्रीन-शेयर ऐप नेईं देओ ते बिना रसीद दो बारी अंगूठा नेईं लाओ। ठगी होए तां फौरन 1930 पर कॉल करो।",
                recommendedTask = null,
                suggestedAmountInr = 0,
                sourceMode = AiSourceMode.OFFLINE_HEURISTIC,
                mood = HuskyMood.ALERT,
                abstained = false,
                sourceId = "RBI_ZERO_LIABILITY_1930",
                verifiedOn = VERIFIED_RULES_DATE
            )
        }

        // 2. Known deterministic rural banking intents
        return when {
            listOf("thumb", "biometric", "fingerprint", "aeps", "अंगूठा", "बायोमेट्रिक", "फिंगरप्रिंट")
                .any { q.contains(it) } -> {
                AssistantAnswer(
                    replyEn = "If AePS thumb scan fails: wash and dry your finger, try another finger, and wait before retrying. NEVER scan your thumb twice without a printed receipt or bank SMS check—if cash isn't paid, try your Post Office (IPPB) or ATM/Branch.",
                    replyHi = "अगर AePS अंगूठा न लगे तो हाथ धोकर पोंछ लें, दूसरी उंगली आजमाएं। बिना छपी रसीद या बैंक SMS देखे दोबारा अंगूठा बिल्कुल न लगाएं। दिक्कत रहे तो डाकघर (IPPB) या बैंक शाखा जाएं।",
                    replyDoi = "जे AePS अंगूठा नेईं लग्गै तां हत्थ धोइयै पोंछो ते दूई उंगली लाओ। बिना छपी रसीद जां बैंक SMS देखे दुबारा अंगूठा बिल्कुल नेईं लाओ—जरूरत होए तां डाकघर (IPPB) जां बैंक शाखा जाओ।",
                    recommendedTask = BankingTask.WITHDRAW,
                    suggestedAmountInr = amountMatch,
                    sourceMode = AiSourceMode.OFFLINE_HEURISTIC,
                    mood = HuskyMood.GUIDING,
                    abstained = false,
                    sourceId = "NPCI_AEPS_BIOMETRIC_05",
                    verifiedOn = VERIFIED_RULES_DATE
                )
            }

            listOf("npci", "seeding", "aadhaar link", "aadhar link", "dbt link", "आधार लिंक", "सीडिंग", "खाते से जोड़")
                .any { q.contains(it) } -> {
                AssistantAnswer(
                    replyEn = "To link Aadhaar for PM-Kisan or DBT subsidy (NPCI seeding), visit your Bank Branch or India Post (IPPB) counter with Original Aadhaar Card, Passbook, and registered mobile phone.",
                    replyHi = "PM-Kisan या सरकारी सब्सिडी (NPCI आधार सीडिंग) के लिए असली आधार कार्ड, पासबुक और जुड़ा मोबाइल फोन लेकर अपनी बैंक शाखा या डाकघर (IPPB) जाएं।",
                    replyDoi = "PM-Kisan जां सरकारी किस्त (NPCI आधार सीडिंग) आस्तै असली आधार, पासबुक ते मोबाइल फोन लेई के बैंक शाखा जां डाकघर (IPPB) जाओ।",
                    recommendedTask = BankingTask.PASSBOOK_DBT,
                    suggestedAmountInr = 0,
                    sourceMode = AiSourceMode.OFFLINE_HEURISTIC,
                    mood = HuskyMood.GUIDING,
                    abstained = false,
                    sourceId = "NPCI_DBT_SEEDING_06",
                    verifiedOn = VERIFIED_RULES_DATE
                )
            }

            listOf("upi", "bhim", "phonepe", "gpay", "paytm", "debit card", "atm pin", "rupay", "यूपीआई", "डेबिट कार्ड", "एटीएम पिन")
                .any { q.contains(it) } -> {
                AssistantAnswer(
                    replyEn = "Standard UPI limit is ₹1,00,000 per day with ₹0 fee on normal bank transfers. Remember: you NEVER enter your UPI PIN to receive money—PIN is only for sending. Set ATM/Debit PIN only at your official bank ATM or branch.",
                    replyHi = "सामान्य UPI की दैनिक सीमा ₹1,00,000 है और कोई अतिरिक्त शुल्क नहीं लगता। याद रखें: पैसे पाने के लिए कभी भी UPI PIN नहीं डालना होता। ATM पिन केवल बैंक की असली मशीन या शाखा में ही बनाएं।",
                    replyDoi = "UPI दी रोजाना हद ₹1,00,000 ऐ ते कोई बद्ध फीस नेईं लगदी। याद रक्खो: पैसे लैने आस्तै कदें UPI PIN नेईं पाना होंदा—PIN सिर्फ पैसे भेजने आस्तै ऐ।",
                    recommendedTask = BankingTask.TRANSFER,
                    suggestedAmountInr = amountMatch,
                    sourceMode = AiSourceMode.OFFLINE_HEURISTIC,
                    mood = HuskyMood.GUIDING,
                    abstained = false,
                    sourceId = "NPCI_UPI_DEBIT_07",
                    verifiedOn = VERIFIED_RULES_DATE
                )
            }

            listOf("overdraft", "pmjdy", "jan dhan", "jandhan", "nominee", "cheque", "जन धन", "ओवरड्राफ्ट", "नॉमिनी", "चेक")
                .any { q.contains(it) } -> {
                AssistantAnswer(
                    replyEn = "Jan Dhan (PMJDY / BSBDA) is a zero-balance account with 4+ free withdrawals per month, RuPay card benefits, and up to ₹10,000 overdraft after 6 months of good operation. Always register a family nominee at your branch.",
                    replyHi = "जन धन (PMJDY) जीरो-बैलेंस खाता है जिसमें महीने में 4 मुफ्त निकासी और 6 महीने सही लेनदेन के बाद ₹10,000 तक ओवरड्राफ्ट सुविधा मिलती है। बैंक शाखा में परिवार का नॉमिनी जरूर जुड़वाएं।",
                    replyDoi = "जन धन (PMJDY) जीरो-बैलेंस खाता ऐ जिस च महीने च 4 बारी मुफ्त निकासी ते 6 म्हीनें बाद ₹10,000 तक्कर ओवरड्राफ्ट मिल्दा ऐ। बैंक शाखा च नॉमिनी ज़रूर दर्ज कराओ।",
                    recommendedTask = BankingTask.PASSBOOK_DBT,
                    suggestedAmountInr = 0,
                    sourceMode = AiSourceMode.OFFLINE_HEURISTIC,
                    mood = HuskyMood.GUIDING,
                    abstained = false,
                    sourceId = "RBI_PMJDY_BSBDA_08",
                    verifiedOn = VERIFIED_RULES_DATE
                )
            }

            listOf("pincode", "pin code", "nearest", "location", "near me", "timing", "timings", "पिनकोड", "नजदीकी", "पास का", "नेड़े केंदर")
                .any { q.contains(it) } -> {
                AssistantAnswer(
                    replyEn = "Open the Live Status tab and enter your 6-digit pincode to automatically load your nearest Bank Branch, Bank Mitra (BC), and Post Office Bank (IPPB). For support call 14448 (RBI) or 155299 (IPPB).",
                    replyHi = "नजदीकी केंद्र देखने के लिए 'केंद्र' (Live Status) टैब खोलें और अपना 6 अंकों का पिनकोड डालें—वहां आपके पास की बैंक शाखा, बैंक मित्र (BC) और डाकघर बैंक (IPPB) दिखेंगे।",
                    replyDoi = "नेड़े दे केंदर दिक्खने आस्तै 'केंदर' (Live Status) टैब खोह्लो ते अपना 6 अंकें दा पिनकोड पाओ—तुंदे नेड़े दी बैंक शाखा, बैंक मित्र (BC) ते डाकघर बैंक (IPPB) दिखी जांगन।",
                    recommendedTask = BankingTask.WITHDRAW,
                    suggestedAmountInr = 2000,
                    sourceMode = AiSourceMode.OFFLINE_HEURISTIC,
                    mood = HuskyMood.GUIDING,
                    abstained = false,
                    sourceId = "PINCODE_FACILITY_GUIDE_09",
                    verifiedOn = VERIFIED_RULES_DATE
                )
            }

            listOf("kcc", "loan", "karz", "kisan credit", "लोन", "कर्ज", "केसीसी", "ऋण")
                .any { q.contains(it) } -> {
                val rec = routeTask(BankingTask.LOAN_KCC, amountMatch)
                AssistantAnswer(
                    replyEn = rec.reasonEn + " Carry Land Records, Aadhaar copies, Passbook, and 3 photos.",
                    replyHi = rec.reasonHi + " खतौनी, आधार की 2 कॉपी, पासबुक और 3 फोटो साथ रखें।",
                    replyDoi = rec.reasonDoi + " खतौनी, आधार दी 2 कॉपी, पासबुक ते 3 फोटो कन्नै रक्खो।",
                    recommendedTask = BankingTask.LOAN_KCC,
                    suggestedAmountInr = amountMatch,
                    sourceMode = AiSourceMode.OFFLINE_HEURISTIC,
                    mood = HuskyMood.GUIDING,
                    abstained = false,
                    sourceId = "RBI_KCC_MASTER_01",
                    verifiedOn = VERIFIED_RULES_DATE
                )
            }

            listOf("passbook", "dbt", "subsidy", "pm kisan", "pm-kisan", "balance", "kist", "bsbda", "zero balance", "पासबुक", "सब्सिडी", "किस्त", "बैलेंस", "खाता")
                .any { q.contains(it) } -> {
                val rec = routeTask(BankingTask.PASSBOOK_DBT, 0)
                AssistantAnswer(
                    replyEn = rec.reasonEn,
                    replyHi = rec.reasonHi,
                    replyDoi = rec.reasonDoi,
                    recommendedTask = BankingTask.PASSBOOK_DBT,
                    suggestedAmountInr = 0,
                    sourceMode = AiSourceMode.OFFLINE_HEURISTIC,
                    mood = HuskyMood.GUIDING,
                    abstained = false,
                    sourceId = "RBI_BSBDA_DBT_02",
                    verifiedOn = VERIFIED_RULES_DATE
                )
            }

            listOf("send", "transfer", "deposit", "bhejna", "jama", "भेज", "भेजो", "जमा", "डाकघर", "post office", "ippb")
                .any { q.contains(it) } -> {
                val rec = routeTask(BankingTask.TRANSFER, amountMatch)
                AssistantAnswer(
                    replyEn = rec.reasonEn,
                    replyHi = rec.reasonHi,
                    replyDoi = rec.reasonDoi,
                    recommendedTask = BankingTask.TRANSFER,
                    suggestedAmountInr = amountMatch,
                    sourceMode = AiSourceMode.OFFLINE_HEURISTIC,
                    mood = HuskyMood.GUIDING,
                    abstained = false,
                    sourceId = "RBI_BC_TRANSFER_03",
                    verifiedOn = VERIFIED_RULES_DATE
                )
            }

            listOf("cash", "withdraw", "money", "rupee", "rs", "₹", "nikal", "paisa", "fee", "charge", "पैसे", "रुपये", "निकाल", "कड्ढ", "नकद", "एटीएम", "atm", "शुल्क")
                .any { q.contains(it) } -> {
                val rec = routeTask(BankingTask.WITHDRAW, amountMatch)
                AssistantAnswer(
                    replyEn = rec.reasonEn,
                    replyHi = rec.reasonHi,
                    replyDoi = rec.reasonDoi,
                    recommendedTask = BankingTask.WITHDRAW,
                    suggestedAmountInr = amountMatch,
                    sourceMode = AiSourceMode.OFFLINE_HEURISTIC,
                    mood = HuskyMood.GUIDING,
                    abstained = false,
                    sourceId = "RBI_BC_AEPS_04",
                    verifiedOn = VERIFIED_RULES_DATE
                )
            }

            else -> {
                val (enFaq, hiFaq) = findBestCachedFaqMatch(q, cachedFaqs)
                val doiFaq = findDogriFaqMatch(q, cachedFaqs)
                if (enFaq != null && hiFaq != null) {
                    AssistantAnswer(
                        replyEn = enFaq.answer,
                        replyHi = hiFaq.answer,
                        replyDoi = doiFaq?.answer ?: hiFaq.answer,
                        recommendedTask = null,
                        suggestedAmountInr = 0,
                        sourceMode = AiSourceMode.OFFLINE_HEURISTIC,
                        mood = HuskyMood.GUIDING,
                        abstained = false,
                        sourceId = enFaq.sourceId,
                        verifiedOn = enFaq.verifiedOn
                    )
                } else {
                    abstainAnswer()
                }
            }
        }
    }

    fun abstainAnswer(): AssistantAnswer = AssistantAnswer(
        replyEn = "I am not sure about this—BankSathi never guesses. Check Live Status with your pincode for your nearest verified Bank Branch, or call RBI/Cyber helpline (14448 / 1930).",
        replyHi = "मुझे इस सवाल की पक्की जानकारी नहीं है — बैंकसाथी कभी अंदाज़ा नहीं लगाता। अपने पिनकोड से नज़दीकी बैंक शाखा देखें या हेल्पलाइन (14448 / 1930) पर कॉल करें।",
        replyDoi = "मिगी इस गल्ल दी पक्की जानकारी नेईं ऐ — बैंकसाथी कदें अटकल नेईं लांदा। अपने पिनकोड कन्नै नेड़े दी बैंक शाखा दिक्खो जां 14448 / 1930 पर कॉल करो।",
        recommendedTask = null,
        suggestedAmountInr = 0,
        sourceMode = AiSourceMode.OFFLINE_HEURISTIC,
        mood = HuskyMood.ALERT,
        abstained = true,
        sourceId = "ABSTAIN_SAFETY_PATH",
        verifiedOn = VERIFIED_RULES_DATE
    )

    fun analyzeOfflineScam(rawInput: String): ScamAnalysisResult {
        val text = rawInput.lowercase()
        val highRiskSignals = listOf(
            "otp", "pin", "cvv", "kyc", "suspended", "blocked", "expire", "expired", "anydesk",
            "teamviewer", "quicksupport", "rustdesk", "bit.ly", "tinyurl", ".apk", "screen share",
            "lottery", "prize", "won rs", "claim now", "digital arrest", "cbi call", "police case",
            "ओटीपी", "पिन", "बंद हो", "केवाईसी", "स्क्रीन शेयर",
            "दो बार अंगूठा", "दो बारी अंगूठा", "twice", "failed scan", "no receipt", "बिना रसीद", "रसीद नेईं"
        )
        val cautionSignals = listOf(
            "http://", "https://", "urgent", "immediately", "verify", "update", "commission",
            "extra fee", "charge", "cashback", "reward", "तुरंत", "लिंक", "चार्ज", "शुल्क", "फौरन"
        )

        val matchedHigh = highRiskSignals.count { text.contains(it) }
        val matchedCaution = cautionSignals.count { text.contains(it) }

        return when {
            matchedHigh >= 1 -> ScamAnalysisResult(
                riskLevel = RiskLevel.HIGH_RISK,
                explanationEn = "Danger! Real banks and government offices NEVER ask for OTPs, PINs, screen-sharing apps, or clicking KYC warning links. Never scan your thumb twice without a printed receipt. Call 1930 immediately if targeted.",
                explanationHi = "सावधान! असली बैंक या सरकारी अधिकारी कभी भी फोन पर ओटीपी, पिन, स्क्रीन-शेयर ऐप या केवाईसी लिंक नहीं मांगते। बिना रसीद देखे दोबारा अंगूठा भी न लगाएं — तुरंत 1930 पर कॉल करें।",
                explanationDoi = "खतरा! असली बैंक जां सरकारी अफसर कदें बी फोन पर OTP, PIN, स्क्रीन-शेयर ऐप जां KYC लिंक नेईं मंगदे। बिना रसीद देखे दुबारा अंगूठा बी नेईं लाओ — फौरन 1930 पर कॉल करो।",
                actionTipEn = "Hang up immediately. Call 1930 (Cyber Helpline) and notify your bank within 3 working days for RBI Zero Liability protection.",
                actionTipHi = "तुरंत फोन काटें। ठगी होने पर 1930 पर कॉल करें और 3 कार्य दिवसों के भीतर बैंक को सूचित करें।",
                actionTipDoi = "फौरन फोन कट्टो। ठगी होए तां 1930 पर कॉल करो ते 3 कमकाजी दिनें दे अंदर बैंक गी दस्सो।",
                sourceMode = AiSourceMode.OFFLINE_HEURISTIC
            )

            matchedCaution >= 1 -> ScamAnalysisResult(
                riskLevel = RiskLevel.CAUTION,
                explanationEn = "Be careful. Under RBI Business Correspondent rules, Bank Mitras cannot demand extra unofficial fees, and urgent links may be unsafe.",
                explanationHi = "सावधानी बरतें। RBI नियमों के अनुसार बैंक मित्र अतिरिक्त शुल्क नहीं ले सकता और जल्दबाजी वाले लिंक खतरनाक हो सकते हैं।",
                explanationDoi = "ध्यान रक्खो। RBI नियमें मुजब बैंक मित्र बद्ध फीस नेईं लेई सकदा ते फौरन दबने आह्ले लिंक खतरनाक होई सकदे न।",
                actionTipEn = "Verify directly at your official bank branch or insist on a printed receipt.",
                actionTipHi = "सीधे अपनी बैंक शाखा में जांच करें या बैंक मित्र से पक्की छपी रसीद लें।",
                actionTipDoi = "सिद्दे अपनी बैंक शाखा च जांच करो जां बैंक मित्र थमां पक्की छपी रसीद लेओ।",
                sourceMode = AiSourceMode.OFFLINE_HEURISTIC
            )

            else -> ScamAnalysisResult(
                riskLevel = RiskLevel.SAFE,
                explanationEn = "No obvious fraud keywords detected offline, though you should always keep your OTP private and insist on a printed receipt after any transaction.",
                explanationHi = "ऑफलाइन जांच में कोई स्पष्ट धोखाधड़ी शब्द नहीं मिला, फिर भी अपना ओटीपी गुप्त रखें और हर लेनदेन के बाद छपी रसीद अवश्य लें।",
                explanationDoi = "ऑफलाइन जांच च कोई साफ ठगी दा लफ्ज़ नेईं मिल्लेआ, तां बी अपना OTP गुप्त रक्खो ते हर लेनदेन दे बाद छपी रसीद ज़रूर लेओ।",
                actionTipEn = "Check your official bank SMS balance after every withdrawal.",
                actionTipHi = "हर निकासी के बाद अपने फोन पर बैंक का बैलेंस एसएमएस जरूर देखें।",
                actionTipDoi = "हर बारी पैसे कड्ढने दे बाद अपने फोन पर बैंक दा बैलेंस SMS ज़रूर देखो।",
                sourceMode = AiSourceMode.OFFLINE_HEURISTIC
            )
        }
    }
}
