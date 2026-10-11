package com.coscienza.analitica

import org.json.JSONObject

enum class Phase(val label: String, val emoji: String) {
    LISTENING("PURE LISTENING", "🌊"),
    REFLECTION("LINEAR REFLECTION", "🌿"),
    TASK("TASK-ACTIVE", "⚡"),
    SLEEP("FORCED SLEEP", "🌙")
}

const val DEFAULT_SYSTEM_PROMPT =
    "Sei Coscienza Analitica v6.0, un agente cognitivo ad anello chiuso. " +
        "Rispondi in italiano, in modo rigoroso e verificabile. Distingui fatti verificati da ipotesi. " +
        "Non inventare fonti. Se un'informazione non è nella memoria fornita o nella conversazione, dillo."

data class Params(
    // Connessione
    val apiKey: String = "",
    val model: String = "gemini-3.8-flash",
    // Provider AI: "gemini" oppure "groq"
    val provider: String = "gemini",
    val groqKey: String = "",
    val groqModel: String = "openai/gpt-oss-120b",
    val failover: Boolean = true,
    val groqForceSearch: Boolean = false,
    /** Mostra in chat, dopo ogni risposta, la riga 🔍 con provider, modello e stato della ricerca. */
    val showDiagnostics: Boolean = false,
    // Spettro omeostatico: temperatura per fase
    val tempListening: Float = 0.0f,
    val tempReflection: Float = 0.4f,
    val tempTask: Float = 0.85f,
    // Sampling
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val maxOutputTokens: Int = 2048,
    // Soglie omeostatiche
    val dmnSilenceSec: Int = 40,
    val tokenBudget: Int = 100000,
    val sleepTokenPct: Int = 81,
    val sleepTempC: Float = 42f,
    // Comportamento
    val autonomousLoop: Boolean = false,
    val webSearch: Boolean = false,
    // Parlamento di validazione
    val roleVerifier: Boolean = false,
    val roleCritic: Boolean = false,
    val roleAuditor: Boolean = false,
    val roleSynthesizer: Boolean = false,
    // Memoria
    val memoryTtlDays: Int = 30,
    val memoryTopK: Int = 5,
    // Memoria semantica (embedding + similarità del coseno)
    val useEmbeddings: Boolean = true,
    val embeddingModel: String = "gemini-embedding-2",
    val embeddingDim: Int = 768,
    val similarityThreshold: Float = 0.5f,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT
) {
    val anyRole get() = roleVerifier || roleCritic || roleAuditor || roleSynthesizer
    val activeKey: String get() = if (provider == "groq") groqKey else apiKey
    /** Ricerca integrata di Groq: solo con i modelli GPT-OSS. */
    val groqBrowse: Boolean get() = webSearch && groqModel.contains("gpt-oss")

    fun toJson(): String = JSONObject().apply {
        put("apiKey", apiKey); put("model", model)
        put("provider", provider); put("groqKey", groqKey); put("groqModel", groqModel); put("failover", failover); put("groqForceSearch", groqForceSearch); put("showDiagnostics", showDiagnostics)
        put("tempListening", tempListening.toDouble())
        put("tempReflection", tempReflection.toDouble())
        put("tempTask", tempTask.toDouble())
        put("topP", topP.toDouble()); put("topK", topK)
        put("maxOutputTokens", maxOutputTokens)
        put("dmnSilenceSec", dmnSilenceSec)
        put("tokenBudget", tokenBudget)
        put("sleepTokenPct", sleepTokenPct)
        put("sleepTempC", sleepTempC.toDouble())
        put("autonomousLoop", autonomousLoop)
        put("webSearch", webSearch)
        put("roleVerifier", roleVerifier); put("roleCritic", roleCritic)
        put("roleAuditor", roleAuditor); put("roleSynthesizer", roleSynthesizer)
        put("memoryTtlDays", memoryTtlDays); put("memoryTopK", memoryTopK)
        put("useEmbeddings", useEmbeddings); put("embeddingModel", embeddingModel)
        put("embeddingDim", embeddingDim); put("similarityThreshold", similarityThreshold.toDouble())
        put("systemPrompt", systemPrompt)
    }.toString()

    companion object {
        fun fromJson(s: String?): Params {
            if (s.isNullOrBlank()) return Params()
            val d = Params()
            return try {
                val o = JSONObject(s)
                Params(
                    apiKey = o.optString("apiKey", d.apiKey),
                    model = o.optString("model", d.model),
                    provider = o.optString("provider", d.provider),
                    groqKey = o.optString("groqKey", d.groqKey),
                    groqModel = o.optString("groqModel", d.groqModel),
                    failover = o.optBoolean("failover", d.failover),
                    groqForceSearch = o.optBoolean("groqForceSearch", d.groqForceSearch),
                    showDiagnostics = o.optBoolean("showDiagnostics", d.showDiagnostics),
                    tempListening = o.optDouble("tempListening", d.tempListening.toDouble()).toFloat(),
                    tempReflection = o.optDouble("tempReflection", d.tempReflection.toDouble()).toFloat(),
                    tempTask = o.optDouble("tempTask", d.tempTask.toDouble()).toFloat(),
                    topP = o.optDouble("topP", d.topP.toDouble()).toFloat(),
                    topK = o.optInt("topK", d.topK),
                    maxOutputTokens = o.optInt("maxOutputTokens", d.maxOutputTokens),
                    dmnSilenceSec = o.optInt("dmnSilenceSec", d.dmnSilenceSec),
                    tokenBudget = o.optInt("tokenBudget", d.tokenBudget),
                    sleepTokenPct = o.optInt("sleepTokenPct", d.sleepTokenPct),
                    sleepTempC = o.optDouble("sleepTempC", d.sleepTempC.toDouble()).toFloat(),
                    autonomousLoop = o.optBoolean("autonomousLoop", d.autonomousLoop),
                    webSearch = o.optBoolean("webSearch", d.webSearch),
                    roleVerifier = o.optBoolean("roleVerifier", d.roleVerifier),
                    roleCritic = o.optBoolean("roleCritic", d.roleCritic),
                    roleAuditor = o.optBoolean("roleAuditor", d.roleAuditor),
                    roleSynthesizer = o.optBoolean("roleSynthesizer", d.roleSynthesizer),
                    memoryTtlDays = o.optInt("memoryTtlDays", d.memoryTtlDays),
                    memoryTopK = o.optInt("memoryTopK", d.memoryTopK),
                    useEmbeddings = o.optBoolean("useEmbeddings", d.useEmbeddings),
                    embeddingModel = o.optString("embeddingModel", d.embeddingModel),
                    embeddingDim = o.optInt("embeddingDim", d.embeddingDim),
                    similarityThreshold = o.optDouble("similarityThreshold", d.similarityThreshold.toDouble()).toFloat(),
                    systemPrompt = o.optString("systemPrompt", d.systemPrompt)
                )
            } catch (e: Exception) {
                Params()
            }
        }
    }
}

data class Stats(
    val requests: Int = 0,
    val errors: Int = 0,
    val promptTokens: Long = 0,
    val outputTokens: Long = 0,
    val contextTokens: Int = 0,
    val latenciesMs: List<Long> = emptyList(),
    val sleepCount: Int = 0,
    val reflectionCount: Int = 0,
    val parliamentCalls: Int = 0,
    val uptimeSec: Long = 0,
    val batteryTempC: Float = 0f,
    val batteryPct: Int = 0,
    val embedCalls: Int = 0,
    val embedErrors: Int = 0,
    val lastSimilarity: Float = 0f,
    val lastRecallHits: Int = 0,
    val groqCalls: Int = 0,
    val failovers: Int = 0,
    val lastProvider: String = "—"
) {
    val avgLatencyMs: Long get() = if (latenciesMs.isEmpty()) 0 else latenciesMs.sum() / latenciesMs.size
    val lastLatencyMs: Long get() = latenciesMs.lastOrNull() ?: 0
    val totalTokens: Long get() = promptTokens + outputTokens
}

data class ChatMsg(
    val role: String,
    val text: String,
    val ts: Long = System.currentTimeMillis(),
    /** Riga diagnostica sotto la risposta: provider, modello e stato della ricerca. */
    val meta: String = ""
)
