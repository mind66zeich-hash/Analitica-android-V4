package com.coscienza.analitica

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =====================================================================
// CHAT
// =====================================================================

@Composable
fun ChatScreen(vm: CoscienzaViewModel, modifier: Modifier = Modifier) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(vm.chat.size) {
        if (vm.chat.isNotEmpty()) listState.animateScrollToItem(vm.chat.lastIndex)
    }

    Column(modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PhaseChip(vm.phase)
            val pct = tokenPct(vm)
            Text("Contesto ${"%.0f".format(pct * 100)}%", style = MaterialTheme.typography.labelLarge)
        }

        if (vm.params.activeKey.isBlank()) {
            var quickKey by remember { mutableStateOf("") }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Inserisci la API key di ${Llm.label(vm.params.provider)} per iniziare (si modifica anche in Parametri).",
                    color = MaterialTheme.colorScheme.error
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quickKey,
                        onValueChange = { quickKey = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("API key") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Button(
                        onClick = {
                            vm.updateParams {
                                if (it.provider == "groq") it.copy(groqKey = quickKey.trim())
                                else it.copy(apiKey = quickKey.trim())
                            }
                            quickKey = ""
                        },
                        enabled = quickKey.isNotBlank()
                    ) { Text("Salva") }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(vm.chat) { m -> Bubble(m) }
        }

        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Scrivi… (\"dormi\" per forzare lo sleep)") },
                maxLines = 4
            )
            Button(
                onClick = { vm.send(input); input = "" },
                enabled = input.isNotBlank() && !vm.busy
            ) { Text(if (vm.busy) "…" else "Invia") }
        }
    }
}

private fun tokenPct(vm: CoscienzaViewModel): Float =
    if (vm.params.tokenBudget <= 0) 0f
    else (vm.stats.contextTokens.toFloat() / vm.params.tokenBudget).coerceIn(0f, 1f)

@Composable
private fun Bubble(m: ChatMsg) {
    val cs = MaterialTheme.colorScheme
    val (bg, label, align) = when (m.role) {
        "user" -> Triple(cs.primaryContainer, "Tu", Alignment.CenterEnd)
        "model" -> Triple(cs.surfaceVariant, "Coscienza", Alignment.CenterStart)
        "reflection" -> Triple(Color(0x333C9D5B), "🌿 Riflessione", Alignment.CenterStart)
        "parliament" -> Triple(Color(0x33E39B1B), "⚖️ Parlamento", Alignment.CenterStart)
        "error" -> Triple(cs.errorContainer, "Errore", Alignment.CenterStart)
        else -> Triple(Color(0x336D4FC2), "Sistema", Alignment.CenterStart)
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = align) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .background(bg, RoundedCornerShape(14.dp))
                .padding(10.dp)
        ) {
            val clipboard = LocalClipboardManager.current
            var copied by remember(m) { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                TextButton(
                    onClick = {
                        clipboard.setText(
                            AnnotatedString(if (m.meta.isNotEmpty()) m.text + "\n\n[" + m.meta + "]" else m.text)
                        )
                        copied = true
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(if (copied) "Copiato ✓" else "Copia", style = MaterialTheme.typography.labelSmall)
                }
            }
            SelectionContainer { Text(m.text) }
            if (m.meta.isNotEmpty()) {
                Text(
                    m.meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

// =====================================================================
// PARAMETRI
// =====================================================================

@Composable
fun ParamsScreen(vm: CoscienzaViewModel, modifier: Modifier = Modifier) {
    val p = vm.params
    var showKey by remember { mutableStateOf(false) }
    var showGroqKey by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {

        Section("🚀 Provider AI") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("gemini" to "Gemini", "groq" to "Groq").forEach { (id, name) ->
                    if (p.provider == id) {
                        Button(onClick = {}, modifier = Modifier.weight(1f)) { Text(name) }
                    } else {
                        OutlinedButton(
                            onClick = { vm.updateParams { it.copy(provider = id) } },
                            modifier = Modifier.weight(1f)
                        ) { Text(name) }
                    }
                }
            }
            Text(
                "Attivo: ${Llm.label(p.provider)} · ${Llm.modelOf(p, p.provider)}",
                fontWeight = FontWeight.Bold
            )
            SwitchRow(
                "Passa all'altro provider se non risponde", p.failover,
                "Dopo un nuovo tentativo, gli errori 429 e 503 fanno passare la richiesta all'altro provider, se ne hai la chiave."
            ) { v -> vm.updateParams { it.copy(failover = v) } }
            SwitchRow(
                "Groq: cerca sempre sul web", p.groqForceSearch,
                "Vale con ricerca web attiva e un modello GPT-OSS. Se spento, la ricerca parte quando il messaggio la chiede (parole come «cerca», «notizie», «oggi»), altrimenti decide il modello. Acceso, parte a ogni risposta: più lento e più token."
            ) { v -> vm.updateParams { it.copy(groqForceSearch = v) } }
            SwitchRow(
                "Mostra diagnostica ricerca", p.showDiagnostics,
                "Dopo ogni risposta aggiunge una riga 🔍 con provider, modello e stato della ricerca (eseguita, non eseguita, spenta)."
            ) { v -> vm.updateParams { it.copy(showDiagnostics = v) } }
            OutlinedTextField(
                value = p.groqKey,
                onValueChange = { v -> vm.updateParams { it.copy(groqKey = v.trim()) } },
                label = { Text("API key Groq") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showGroqKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            TextButton(onClick = { showGroqKey = !showGroqKey }) { Text(if (showGroqKey) "Nascondi chiave" else "Mostra chiave") }
            OutlinedTextField(
                value = p.groqModel,
                onValueChange = { v -> vm.updateParams { it.copy(groqModel = v.trim()) } },
                label = { Text("Modello Groq") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Modelli suggeriti (tocca per usarli)", style = MaterialTheme.typography.labelMedium)
            GROQ_PRESETS.forEach { (id, name) ->
                TextButton(onClick = { vm.updateParams { it.copy(groqModel = id) } }) {
                    Text(if (p.groqModel == id) "✓ $name · $id" else "$name · $id")
                }
            }
            Text(
                "Llama 4 Scout non è più disponibile su Groq. Gli ID dei modelli cambiano spesso: " +
                    "se uno dà errore 404, scegline un altro. Groq non offre embedding: la memoria a coseno usa sempre la chiave Gemini. " +
                    "La ricerca web con Groq funziona solo con i modelli GPT-OSS.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Section("🧪 Diagnostica ricerca Groq") {
            val clipboard = LocalClipboardManager.current
            var copiedProbe by remember { mutableStateOf(false) }
            Text(
                "Invia a Groq una richiesta minima con browser_search obbligatorio (modello e chiave Groq impostati qui sopra) e mostra la risposta grezza o l'errore.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { copiedProbe = false; vm.probeGroqSearch() },
                enabled = !vm.probing,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (vm.probing) "Prova in corso…" else "Prova ricerca Groq") }
            if (vm.probeResult.isNotEmpty()) {
                SelectionContainer {
                    Text(
                        vm.probeResult,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(vm.probeResult))
                        copiedProbe = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (copiedProbe) "Copiato ✓" else "Copia risultato negli appunti") }
            }
        }

        Section("🔌 Connessione Gemini") {
            OutlinedTextField(
                value = p.apiKey,
                onValueChange = { v -> vm.updateParams { it.copy(apiKey = v.trim()) } },
                label = { Text("API key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Nascondi chiave" else "Mostra chiave") }
            OutlinedTextField(
                value = p.model,
                onValueChange = { v -> vm.updateParams { it.copy(model = v.trim()) } },
                label = { Text("Modello") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.updateParams { it.copy(model = "gemini-3.8-flash") } }) {
                    Text("Usa gemini-3.8-flash")
                }
                if (p.apiKey.isNotBlank()) {
                    TextButton(onClick = { vm.updateParams { it.copy(apiKey = "") } }) { Text("Cancella chiave") }
                }
            }
            Text(
                "Il modello è un campo libero: se Google cambia nome o ritira un modello, " +
                    "scrivi qui il nuovo ID (elenco su ai.google.dev/gemini-api/docs/models). " +
                    "gemini-1.5-flash non esiste più e dà errore 404.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SwitchRow(
                "Ricerca web (Gemini e Groq)", p.webSearch,
                "Con Gemini usa Google Search. Con Groq usa la ricerca integrata, solo con i modelli GPT-OSS. Consuma più token e tempo."
            ) { v -> vm.updateParams { it.copy(webSearch = v) } }
        }

        Section("🌡️ Spettro omeostatico (temperatura per fase)") {
            SliderRow("🌊 PURE LISTENING", p.tempListening, 0f..2f, 39) { v -> vm.updateParams { it.copy(tempListening = round2(v)) } }
            SliderRow("🌿 LINEAR REFLECTION", p.tempReflection, 0f..2f, 39) { v -> vm.updateParams { it.copy(tempReflection = round2(v)) } }
            SliderRow("⚡ TASK-ACTIVE", p.tempTask, 0f..2f, 39) { v -> vm.updateParams { it.copy(tempTask = round2(v)) } }
        }

        Section("🎛️ Sampling") {
            SliderRow("Top-P", p.topP, 0f..1f, 19) { v -> vm.updateParams { it.copy(topP = round2(v)) } }
            IntSliderRow("Top-K", p.topK, 1, 100) { v -> vm.updateParams { it.copy(topK = v) } }
            IntSliderRow("Max token in uscita", p.maxOutputTokens, 256, 8192, 256) { v -> vm.updateParams { it.copy(maxOutputTokens = v) } }
        }

        Section("🌙 Soglie e Sleep") {
            IntSliderRow(
                "Silenzio prima del DMN", p.dmnSilenceSec, 10, 300, 5, " s",
                "Dopo questo silenzio si passa a LINEAR REFLECTION."
            ) { v -> vm.updateParams { it.copy(dmnSilenceSec = v) } }
            IntSliderRow(
                "Budget token di contesto", p.tokenBudget, 10_000, 1_000_000, 10_000
            ) { v -> vm.updateParams { it.copy(tokenBudget = v) } }
            IntSliderRow(
                "Sleep al raggiungimento del budget", p.sleepTokenPct, 50, 100, 1, "%"
            ) { v -> vm.updateParams { it.copy(sleepTokenPct = v) } }
            SliderRow(
                "Sleep alla temperatura batteria", p.sleepTempC, 30f..55f, 49,
                { "%.1f °C".format(it) },
                "Sostituisce il limite GPU a 82 °C: sul telefono la telemetria è la batteria."
            ) { v -> vm.updateParams { it.copy(sleepTempC = Math.round(v * 2) / 2f) } }
            SwitchRow(
                "Loop autonomo (meditazione spontanea)", p.autonomousLoop,
                "Chiama Gemini da sola a ogni silenzio: consuma quota API."
            ) { v -> vm.updateParams { it.copy(autonomousLoop = v) } }
        }

        Section("⚖️ Parlamento di validazione") {
            Text(
                "Ogni ruolo attivo aggiunge una chiamata extra per risposta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SwitchRow("Verifier", p.roleVerifier) { v -> vm.updateParams { it.copy(roleVerifier = v) } }
            SwitchRow("Critic", p.roleCritic) { v -> vm.updateParams { it.copy(roleCritic = v) } }
            SwitchRow("Auditor", p.roleAuditor) { v -> vm.updateParams { it.copy(roleAuditor = v) } }
            SwitchRow("Synthesizer", p.roleSynthesizer) { v -> vm.updateParams { it.copy(roleSynthesizer = v) } }
        }

        Section("🧠 Memoria") {
            IntSliderRow(
                "TTL memoria episodica", p.memoryTtlDays, 1, 365, 1, " gg"
            ) { v -> vm.updateParams { it.copy(memoryTtlDays = v) } }
            IntSliderRow(
                "Ricordi richiamati per richiesta", p.memoryTopK, 0, 20
            ) { v -> vm.updateParams { it.copy(memoryTopK = v) } }
        }

        Section("🧭 Memoria semantica (embedding)") {
            SwitchRow(
                "Richiamo per similarità del coseno", p.useEmbeddings,
                "Ogni ricordo e ogni domanda fanno una chiamata embedding in più."
            ) { v -> vm.updateParams { it.copy(useEmbeddings = v) } }
            OutlinedTextField(
                value = p.embeddingModel,
                onValueChange = { v -> vm.updateParams { it.copy(embeddingModel = v.trim()) } },
                label = { Text("Modello di embedding") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            IntSliderRow(
                "Dimensione del vettore", p.embeddingDim, 128, 3072, 128,
                hint = "Consigliati 768, 1536, 3072. Se la cambi, usa \"Calcola embedding mancanti\" in Memoria."
            ) { v -> vm.updateParams { it.copy(embeddingDim = v) } }
            SliderRow(
                "Soglia di similarità minima", p.similarityThreshold, 0f..1f, 99,
                hint = "Sotto questa soglia il ricordo non viene richiamato."
            ) { v -> vm.updateParams { it.copy(similarityThreshold = round2(v)) } }
        }

        Section("📜 System prompt") {
            OutlinedTextField(
                value = p.systemPrompt,
                onValueChange = { v -> vm.updateParams { it.copy(systemPrompt = v) } },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 12
            )
            OutlinedButton(onClick = { vm.updateParams { it.copy(systemPrompt = DEFAULT_SYSTEM_PROMPT) } }) {
                Text("Ripristina prompt")
            }
        }

        OutlinedButton(
            onClick = { vm.resetParams() },
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) { Text("Ripristina tutti i parametri (tiene chiave e modello)") }
    }
}

private fun round2(v: Float) = Math.round(v * 100) / 100f

// =====================================================================
// STATISTICHE
// =====================================================================

@Composable
fun StatsScreen(vm: CoscienzaViewModel, modifier: Modifier = Modifier) {
    val s = vm.stats
    val p = vm.params
    vm.memoryVersion // legge lo stato per ricomporre quando la memoria cambia

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {

        Section("Stato attuale") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PhaseChip(vm.phase)
                Text("Uptime ${formatDuration(s.uptimeSec)}")
            }
            val temp = when (vm.phase) {
                Phase.LISTENING -> p.tempListening
                Phase.REFLECTION -> p.tempReflection
                Phase.TASK -> p.tempTask
                Phase.SLEEP -> 0f
            }
            Text("Temperatura di sampling attiva: ${"%.2f".format(temp)}")

            val ctxFrac = tokenPct(vm)
            val limitFrac = p.sleepTokenPct / 100f
            Text("Contesto: ${s.contextTokens} / ${p.tokenBudget} token (sleep al ${p.sleepTokenPct}%)")
            LinearProgressIndicator(
                progress = { ctxFrac },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = if (ctxFrac >= limitFrac) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }

        Section("Telemetria dispositivo (interocezione)") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Temp. batteria", if (s.batteryTempC > 0) "%.1f °C".format(s.batteryTempC) else "n/d", Modifier.weight(1f))
                StatTile("Batteria", "${s.batteryPct}%", Modifier.weight(1f))
            }
            val frac = if (s.batteryTempC > 0) (s.batteryTempC / p.sleepTempC).coerceIn(0f, 1f) else 0f
            Text("Margine termico verso la soglia di sleep (${"%.1f".format(p.sleepTempC)} °C)", style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().height(8.dp))
        }

        Section("Richieste e token") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Richieste", s.requests.toString(), Modifier.weight(1f))
                StatTile("Errori", s.errors.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Token input", s.promptTokens.toString(), Modifier.weight(1f))
                StatTile("Token output", s.outputTokens.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Token totali", s.totalTokens.toString(), Modifier.weight(1f))
                val rate = if (s.requests == 0) "—" else "%.0f%%".format((s.requests - s.errors) * 100f / s.requests)
                StatTile("Successo", rate, Modifier.weight(1f))
            }
        }

        Section("Provider") {
            StatTile("Ultima risposta da", s.lastProvider, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Chiamate Groq", s.groqCalls.toString(), Modifier.weight(1f))
                StatTile("Passaggi automatici", s.failovers.toString(), Modifier.weight(1f))
            }
        }

        Section("Latenza") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Ultima", "${s.lastLatencyMs} ms", Modifier.weight(1f))
                StatTile("Media", "${s.avgLatencyMs} ms", Modifier.weight(1f))
            }
            Sparkline(s.latenciesMs)
            Text("Ultime ${s.latenciesMs.size} chiamate riuscite", style = MaterialTheme.typography.bodySmall)
        }

        Section("Ciclo cognitivo") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Sleep", s.sleepCount.toString(), Modifier.weight(1f))
                StatTile("Riflessioni", s.reflectionCount.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Chiamate parlamento", s.parliamentCalls.toString(), Modifier.weight(1f))
                StatTile("Eventi nel log", vm.memory.eventCount.toString(), Modifier.weight(1f))
            }
        }

        Section("Memoria semantica (coseno)") {
            val indexed = vm.memory.vectorCount(p.embeddingModel.trim(), p.embeddingDim)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Ricordi con vettore", "$indexed / ${vm.memory.entries.size}", Modifier.weight(1f))
                StatTile("Chiamate embedding", s.embedCalls.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Errori embedding", s.embedErrors.toString(), Modifier.weight(1f))
                StatTile("Ultimo coseno (max)", "%.3f".format(s.lastSimilarity), Modifier.weight(1f))
            }
            StatTile("Ricordi richiamati all'ultima richiesta", s.lastRecallHits.toString(), Modifier.fillMaxWidth())
        }

        Section("Memoria a lungo termine") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Episodica", vm.memory.count(Layer.EPISODIC).toString(), Modifier.weight(1f))
                StatTile("Semantica", vm.memory.count(Layer.SEMANTIC).toString(), Modifier.weight(1f))
                StatTile("Procedurale", vm.memory.count(Layer.PROCEDURAL).toString(), Modifier.weight(1f))
            }
            StatTile("Working Memory (messaggi)", vm.chat.size.toString(), Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Sparkline(values: List<Long>) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(80.dp)) {
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
        if (values.size < 2) return@Canvas
        val max = values.max().coerceAtLeast(1L).toFloat()
        val stepX = size.width / (values.size - 1)
        for (i in 0 until values.size - 1) {
            val y1 = size.height - (values[i] / max) * (size.height - 6f)
            val y2 = size.height - (values[i + 1] / max) * (size.height - 6f)
            drawLine(line, Offset(i * stepX, y1), Offset((i + 1) * stepX, y2), strokeWidth = 5f, cap = StrokeCap.Round)
        }
    }
}

// =====================================================================
// MEMORIA
// =====================================================================

@Composable
fun MemoryScreen(vm: CoscienzaViewModel, modifier: Modifier = Modifier) {
    vm.memoryVersion
    var fact by remember { mutableStateOf("") }
    val fmt = remember { SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()) }
    val sorted = vm.memory.entries.sortedByDescending { it.timestamp }

    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Aggiungi un fatto alla memoria semantica", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = fact, onValueChange = { fact = it },
                        modifier = Modifier.fillMaxWidth(), minLines = 2,
                        placeholder = { Text("Fatto verificato, regola o policy fissa") }
                    )
                    Button(onClick = { vm.addSemanticFact(fact); fact = "" }, enabled = fact.isNotBlank()) {
                        Text("Salva")
                    }
                }
            }
        }
        item {
            val missing = vm.memory.missingVector(vm.params.embeddingModel.trim(), vm.params.embeddingDim).size
            OutlinedButton(
                onClick = { vm.backfillEmbeddings() },
                enabled = missing > 0 && !vm.backfilling && vm.params.useEmbeddings && vm.params.apiKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (vm.backfilling) "Calcolo in corso…" else "Calcola embedding mancanti ($missing)")
            }
        }
        if (sorted.isEmpty()) {
            item { Text("Nessun ricordo ancora. Gli scambi vengono registrati come memoria episodica.") }
        }
        items(sorted, key = { it.id }) { e ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${e.layer.label} · ${e.type}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(fmt.format(Date(e.timestamp)), style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(e.content, maxLines = 6)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "id ${e.id} · ${e.validationStatus} · ttl ${if (e.ttlDays == 0) "∞" else "${e.ttlDays}gg"} · " +
                                (vm.memory.vectors[e.id]?.let { "🧭 ${it.values.size}d" } ?: "no vettore"),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Row {
                            if (e.ttlDays > 0) {
                                TextButton(onClick = { vm.makePermanent(e.id) }) { Text("Rendi permanente") }
                            }
                            TextButton(onClick = { vm.deleteMemory(e.id) }) { Text("Elimina") }
                        }
                    }
                }
            }
        }
    }
}
