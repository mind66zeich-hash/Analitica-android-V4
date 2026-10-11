package com.coscienza.analitica

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Parole che indicano che l'utente vuole una ricerca sul web: in quel caso la ricerca viene resa obbligatoria. */
private val SEARCH_INTENT = Regex(
    "\\b(cerca\\w*|ricerc\\w*|internet|web|online|google|notizi\\w*|ultim[eoi]|oggi|attual\\w*|recent\\w*|aggiornat\\w*|meteo|prezz\\w*)\\b",
    RegexOption.IGNORE_CASE
)

class CoscienzaViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("coscienza", Context.MODE_PRIVATE)
    val memory = MemoryStore(app)

    var params by mutableStateOf(Params.fromJson(prefs.getString("params", null)))
        private set
    var stats by mutableStateOf(Stats())
        private set
    var phase by mutableStateOf(Phase.LISTENING)
        private set
    var busy by mutableStateOf(false)
        private set

    /** Incrementato a ogni modifica della memoria per forzare la ricomposizione della UI. */
    var memoryVersion by mutableIntStateOf(0)
        private set

    val chat = mutableStateListOf<ChatMsg>()

    var backfilling by mutableStateOf(false)
        private set

    /** Diagnostica ricerca Groq: testo dell'ultimo esito e stato di esecuzione. */
    var probeResult by mutableStateOf("")
        private set
    var probing by mutableStateOf(false)
        private set

    fun probeGroqSearch() {
        if (probing) return
        probing = true
        probeResult = "Prova in corso…"
        val p = params
        viewModelScope.launch {
            probeResult = try {
                GroqClient.probe(p)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                "Errore imprevisto: ${e.javaClass.simpleName}: ${e.message}"
            } finally {
                probing = false
            }
        }
    }

    private val startMs = System.currentTimeMillis()
    private var lastActivityMs = startMs
    private var sleepUntilMs = 0L
    private var contextStart = 0 // indice in chat da cui parte la Working Memory corrente

    init {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                tick()
            }
        }
    }

    // ---------- Parametri ----------

    fun updateParams(change: (Params) -> Params) {
        params = change(params)
        prefs.edit().putString("params", params.toJson()).apply()
    }

    fun resetParams() {
        // Conserva API key e modello: sono credenziali/scelte di connessione, non parametri cognitivi.
        updateParams {
            Params(
                apiKey = it.apiKey, model = it.model,
                provider = it.provider, groqKey = it.groqKey, groqModel = it.groqModel
            )
        }
    }

    // ---------- Telemetria e ciclo omeostatico ----------

    private fun readBattery(): Pair<Float, Int> {
        val i: Intent? = getApplication<Application>()
            .registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else 0
        return temp to pct
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val (temp, pct) = readBattery()
        stats = stats.copy(uptimeSec = (now - startMs) / 1000, batteryTempC = temp, batteryPct = pct)

        phase = when {
            now < sleepUntilMs -> Phase.SLEEP
            busy -> Phase.TASK
            (now - lastActivityMs) / 1000 >= params.dmnSilenceSec -> Phase.REFLECTION
            else -> Phase.LISTENING
        }

        // Sleep termico, con 60 s di tolleranza dopo l'ultimo sleep per non ripeterlo in loop.
        if (temp > 0f && temp >= params.sleepTempC && now > sleepUntilMs + 60_000) {
            enterSleep("soglia termica (${"%.1f".format(temp)} °C)")
        }

        if (phase == Phase.REFLECTION && params.autonomousLoop && !busy && params.activeKey.isNotBlank()) {
            reflect()
        }
    }

    private fun currentTemperature(): Float = when (phase) {
        Phase.LISTENING -> params.tempListening
        Phase.REFLECTION -> params.tempReflection
        else -> params.tempTask
    }

    // ---------- Interazione ----------

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        lastActivityMs = System.currentTimeMillis()
        if (phase == Phase.SLEEP) {
            chat += ChatMsg("system", "🌙 Il sistema è in sleep, riprova tra qualche secondo.")
            return
        }
        chat += ChatMsg("user", t)
        if (t.lowercase() in setOf("dormi", "sleep", "/dormi", "/sleep")) {
            enterSleep("comando utente")
            return
        }
        viewModelScope.launch { runTurn(t, params.tempTask, "user") }
    }

    private fun reflect() {
        lastActivityMs = System.currentTimeMillis() // una sola meditazione per periodo di silenzio
        stats = stats.copy(reflectionCount = stats.reflectionCount + 1)
        val prompt = "Impulso endogeno (Default Mode Network): rifletti brevemente, in 3-4 righe, " +
            "sulla conversazione e sulla memoria disponibile. Segnala incongruenze o punti da approfondire."
        viewModelScope.launch { runTurn(prompt, params.tempReflection, "reflection") }
    }

    private suspend fun runTurn(userText: String, temperature: Float, source: String) {
        busy = true
        phase = Phase.TASK
        memory.log("turn_start", source)
        val p = params
        // Con Groq, se il messaggio chiede di cercare, la ricerca è obbligatoria per questo turno.
        val pTurn = if (source == "user" && SEARCH_INTENT.containsMatchIn(userText)) p.copy(groqForceSearch = true) else p
        val recalled = recallFor(p, userText)
        val system = buildString {
            append(p.systemPrompt)
            if (p.webSearch && (p.provider != "groq" || p.groqBrowse)) {
                append(
                    "\n\nHai a disposizione la ricerca web: usala quando servono informazioni aggiornate o verificabili. " +
                        "Se la conversazione o la memoria richiamata dicono che non puoi cercare su internet, ignorale: " +
                        "erano risposte passate, ora la ricerca è attiva."
                )
            }
            // Il modello non ha un orologio: gli passiamo noi data e ora correnti.
            append(
                "\n\nData e ora attuali: " +
                    java.time.ZonedDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm (VV)", java.util.Locale.ITALIAN)
                    ) +
                    ". Usa questa data, non quella di eventuali ricordi."
            )
            if (recalled.isNotEmpty()) {
                append("\n\nMemoria richiamata (usa solo se pertinente):\n")
                recalled.forEach { append("- [${it.layer.label}] ${it.content}\n") }
            }
        }
        val contents = if (source == "user") history() else listOf("user" to userText)

        Llm.generate(pTurn, temperature, system, contents)
            .onSuccess { o ->
                val r = o.result
                noteProvider(o)
                registerCall(r)
                val searchState = when {
                    o.provider == "groq" && r.searchCount > 0 ->
                        "🔎 ricerca eseguita (${r.searchCount} operazioni)" +
                            (if (r.searchQueries.isNotEmpty()) ": " + r.searchQueries.joinToString(" · ") else "")
                    o.provider == "groq" && pTurn.groqBrowse ->
                        "⚠️ ricerca inviata ma non eseguita da Groq" +
                            (if (pTurn.groqForceSearch) " (era obbligatoria)" else " (il modello ha scelto di non cercare)")
                    o.provider == "groq" -> "ricerca non inviata (interruttore spento o modello non GPT-OSS)"
                    p.webSearch -> "ricerca Google attiva"
                    else -> "ricerca spenta"
                }
                val meta = "${Llm.label(o.provider)} · ${o.model} · $searchState"
                val msg = ChatMsg(if (source == "reflection") "reflection" else "model", r.text)
                chat += msg
                // Riga di diagnostica come messaggio di sistema: non entra nella cronologia inviata al modello
                // e si vede anche se la schermata della chat non è aggiornata.
                if (p.showDiagnostics) chat += ChatMsg("system", "🔍 $meta")
                stats = stats.copy(contextTokens = r.promptTokens + r.outputTokens)
                val saved = memory.add(
                    Layer.EPISODIC, source,
                    "Input: ${userText.take(300)} | Output: ${r.text.take(500)}",
                    source = "agent", ttlDays = p.memoryTtlDays
                )
                viewModelScope.launch { embedEntry(saved) }
                if (p.anyRole && source == "user") parliament(p, userText, r.text, recalled)
            }
            .onFailure { e ->
                stats = stats.copy(requests = stats.requests + 1, errors = stats.errors + 1)
                chat += ChatMsg("error", e.message ?: "Errore sconosciuto")
                memory.log("error", e.message.orEmpty().take(200))
            }

        memoryVersion++
        busy = false
        lastActivityMs = System.currentTimeMillis()

        val limit = params.tokenBudget.toLong() * params.sleepTokenPct / 100
        if (stats.contextTokens >= limit && limit > 0) {
            enterSleep("budget token al ${params.sleepTokenPct}%")
        }
    }

    private suspend fun parliament(p: Params, question: String, answer: String, recalled: List<MemEntry>) {
        val roles = buildList {
            if (p.roleVerifier) add("Verifier (coerenza con i fatti e la memoria)")
            if (p.roleCritic) add("Critic (punti deboli del ragionamento)")
            if (p.roleAuditor) add("Auditor (conformità a regole e policy)")
            if (p.roleSynthesizer) add("Synthesizer (sintesi finale correttiva)")
        }
        val prompt = buildString {
            append("Agisci come parlamento di validazione con questi ruoli:\n")
            roles.forEach { append("- $it\n") }
            append("\nDomanda:\n$question\n\nRisposta da validare:\n$answer\n")
            if (recalled.isNotEmpty()) {
                append("\nFatti in memoria:\n")
                recalled.forEach { append("- ${it.content}\n") }
            }
            append("\nPer ogni ruolo scrivi al massimo 2 righe. Se tutto è coerente scrivi COERENTE.")
        }
        Llm.generate(p, 0.0f, p.systemPrompt, listOf("user" to prompt))
            .onSuccess { o ->
                val r = o.result
                noteProvider(o)
                registerCall(r)
                stats = stats.copy(parliamentCalls = stats.parliamentCalls + 1)
                chat += ChatMsg("parliament", r.text)
            }
            .onFailure {
                stats = stats.copy(requests = stats.requests + 1, errors = stats.errors + 1)
                chat += ChatMsg("error", "Parlamento: ${it.message}")
            }
    }

    /** Aggiorna le statistiche del provider e avvisa in chat se la risposta viene dall'altro provider. */
    private fun noteProvider(o: LlmOutcome) {
        stats = stats.copy(
            lastProvider = "${Llm.label(o.provider)} · ${o.model}",
            groqCalls = stats.groqCalls + if (o.provider == "groq") 1 else 0,
            failovers = stats.failovers + if (o.failedOverFrom != null) 1 else 0
        )
        if (o.failedOverFrom != null) {
            val noSearch = if (o.provider == "groq" && params.webSearch && !params.groqBrowse) ", senza ricerca web" else ""
            chat += ChatMsg(
                "system",
                "↪️ ${o.failedOverFrom}. Risposta da ${Llm.label(o.provider)} (${o.model})$noSearch."
            )
        }
    }

    private fun registerCall(r: GeminiResult) {
        stats = stats.copy(
            requests = stats.requests + 1,
            promptTokens = stats.promptTokens + r.promptTokens,
            outputTokens = stats.outputTokens + r.outputTokens,
            latenciesMs = (stats.latenciesMs + r.latencyMs).takeLast(60)
        )
    }

    /** Working Memory = messaggi dopo l'ultimo sleep, con ruoli alternati user/model. */
    private fun history(): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        chat.drop(contextStart).takeLast(24).forEach { m ->
            val role = when (m.role) {
                "user" -> "user"
                "model" -> "model"
                else -> return@forEach
            }
            if (out.isNotEmpty() && out.last().first == role) {
                out[out.lastIndex] = role to (out.last().second + "\n" + m.text)
            } else {
                out += role to m.text
            }
        }
        while (out.isNotEmpty() && out.first().first != "user") out.removeAt(0)
        return out
    }

    // ---------- Sleep ----------

    fun enterSleep(reason: String) {
        val now = System.currentTimeMillis()
        sleepUntilMs = now + 8000
        phase = Phase.SLEEP
        val purged = memory.purgeExpired()
        memory.log("sleep", reason)
        contextStart = chat.size
        stats = stats.copy(sleepCount = stats.sleepCount + 1, contextTokens = 0)
        chat += ChatMsg(
            "system",
            "🌙 Sleep ($reason). Working Memory svuotata, $purged voci scadute rimosse. " +
                "La memoria persistente resta intatta."
        )
        memoryVersion++
    }

    // ---------- Memoria (azioni dell'utente) ----------

    fun addSemanticFact(text: String) {
        if (text.isBlank()) return
        val saved = memory.add(
            Layer.SEMANTIC, "fact", text.trim(), source = "user", ttlDays = 0,
            confidence = 1.0, validation = "verified", criticality = "high"
        )
        memoryVersion++
        viewModelScope.launch { embedEntry(saved) }
    }

    fun makePermanent(id: String) {
        memory.makePermanent(id)
        memoryVersion++
    }

    fun deleteMemory(id: String) {
        memory.delete(id)
        memoryVersion++
    }

    // ---------- Memoria semantica (embedding + coseno) ----------

    /** Richiamo ibrido: coseno sui ricordi con vettore; parole in comune per quelli senza vettore. */
    private suspend fun recallFor(p: Params, query: String): List<MemEntry> {
        if (p.memoryTopK <= 0) return emptyList()
        var result = emptyList<MemEntry>()
        var embeddingOk = false
        if (p.useEmbeddings && p.apiKey.isNotBlank() && memory.vectors.isNotEmpty()) {
            GeminiClient.embed(p, query, true)
                .onSuccess { q ->
                    embeddingOk = true
                    val hits = memory.recallSemantic(q, p.embeddingModel.trim(), p.memoryTopK, p.similarityThreshold)
                    stats = stats.copy(
                        embedCalls = stats.embedCalls + 1,
                        lastSimilarity = hits.firstOrNull()?.score ?: 0f,
                        lastRecallHits = hits.size
                    )
                    result = hits.map { it.entry }
                }
                .onFailure { e ->
                    stats = stats.copy(embedErrors = stats.embedErrors + 1)
                    memory.log("embed_error", e.message.orEmpty().take(200))
                }
        }
        if (result.size < p.memoryTopK) {
            val extra = memory.recall(query, p.memoryTopK).filter { e ->
                result.none { it.id == e.id } && (!embeddingOk || memory.vectors[e.id] == null)
            }
            result = result + extra.take(p.memoryTopK - result.size)
        }
        return result
    }

    private suspend fun embedEntry(e: MemEntry) {
        val p = params
        if (!p.useEmbeddings || p.apiKey.isBlank()) return
        GeminiClient.embed(p, e.content, false)
            .onSuccess { v ->
                memory.setVector(e.id, p.embeddingModel.trim(), v)
                stats = stats.copy(embedCalls = stats.embedCalls + 1)
                memoryVersion++
            }
            .onFailure { err ->
                stats = stats.copy(embedErrors = stats.embedErrors + 1)
                memory.log("embed_error", err.message.orEmpty().take(200))
            }
    }

    /** Calcola i vettori mancanti o incompatibili (modello o dimensione cambiati). */
    fun backfillEmbeddings() {
        val p = params
        if (backfilling || !p.useEmbeddings || p.apiKey.isBlank()) return
        val model = p.embeddingModel.trim()
        val todo = memory.missingVector(model, p.embeddingDim)
        if (todo.isEmpty()) return
        viewModelScope.launch {
            backfilling = true
            todo.forEach {
                embedEntry(it)
                delay(300)
            }
            backfilling = false
            val done = todo.count { memory.vectors[it.id]?.model == model }
            chat += ChatMsg("system", "🧭 Embedding calcolati per $done di ${todo.size} ricordi.")
        }
    }

    fun clearChat() {
        chat.clear()
        contextStart = 0
    }
}
