# Coscienza Analitica (Android)

App Android in Kotlin + Jetpack Compose che porta su telefono l'architettura di Coscienza Sintetica v6.0 (Analitica Pro), con le API di Gemini.

## Compilare su GitHub

1. Crea un repository vuoto su GitHub e carica tutto il contenuto di questa cartella (compresa `.github/`).
2. Vai su **Actions > Build APK > Run workflow** (parte anche a ogni push su `main`).
3. A build finita apri l'esecuzione e scarica l'artefatto **coscienza-analitica-debug**: dentro c'è `app-debug.apk`.
4. Installalo sul telefono (serve consentire l'installazione da fonti sconosciute).

Il workflow installa Gradle 8.7 con l'action, quindi non serve il Gradle wrapper.

## Primo avvio

1. Scheda **Parametri**: incolla la API key di Gemini (da Google AI Studio).
2. Controlla il campo **Modello**. `gemini-1.5-flash` è stato ritirato da Google; il default è `gemini-3.8-flash`.
3. Scheda **Chat** per parlare con l'agente. Scrivendo `dormi` o `sleep` forzi lo sleep.

## Schede

- **Chat**: conversazione, fase omeostatica corrente, % di contesto usato.
- **Parametri**: connessione, temperature per fase, top-P/top-K, soglie di sleep, loop autonomo, ruoli del parlamento, memoria, system prompt.
- **Statistiche**: fase e temperatura attive, contesto vs budget, telemetria batteria, richieste, errori, token, latenza, sleep, riflessioni, memoria per strato.
- **Memoria**: voci a 13 campi (episodica, semantica, procedurale), aggiunta di fatti e cancellazione manuale.

## Provider: Gemini e Groq

In **Parametri → Provider AI** scegli il provider attivo e inserisci le due chiavi (Groq: console.groq.com).

- Il modello Groq è un campo libero con modelli suggeriti: Qwen 3.8 27B (anteprima), Llama 3.3 70B, Llama 3.1 8B, GPT-OSS 120B e 20B.
- Llama 4 Scout non è più disponibile su Groq (spento il 17 luglio 2026).
- Se il provider attivo risponde con errore 429 o 503, l'app ritenta una volta e poi, se l'opzione è attiva e hai la chiave dell'altro provider, passa a quello. In chat compare l'avviso del passaggio.
- Ricerca web: con Gemini usa Google Search; con Groq usa la ricerca integrata (`browser_search`), disponibile solo con i modelli GPT-OSS. L'interruttore "Groq: cerca sempre sul web" obbliga la ricerca a ogni risposta; anche da spento, i messaggi che chiedono di cercare ("cerca", "notizie", "oggi"...) la rendono obbligatoria per quel turno.
- Gli embedding (memoria a coseno) restano su Gemini: Groq non li offre.

## Stato rispetto alla specifica

| Elemento | Stato |
|---|---|
| Fasi PURE LISTENING / LINEAR REFLECTION / TASK-ACTIVE / FORCED SLEEP con temperature | Implementato |
| Sleep per budget token, temperatura e comando utente | Implementato (temperatura = batteria, non GPU) |
| Memoria a strati con schema a 13 campi e TTL | Implementato |
| Event log append-only | Implementato (`.jsonl`, una riga per evento) |
| Parlamento Verifier/Critic/Auditor/Synthesizer | Una chiamata di validazione con i ruoli attivi |
| Richiamo semantico con embedding a 768 dim | Implementato: embedding Gemini + similarità del coseno, vettori in `ltm_architecture_v6_index.bin`; i ricordi senza vettore ricadono sul richiamo per parole |
| Consolidamento lossy/lossless allo sleep | Parziale: svuota la Working Memory e rimuove le voci scadute, non riassume |
| Loop a 7 fasi (Percezione ... Ripetizione) | Non ancora |
| Ricerca web | Opzione che attiva il grounding Google Search di Gemini |

La telemetria GPU AMD non esiste su Android: temperatura e livello della batteria ne fanno le veci.
