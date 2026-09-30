package it.diario.lavorativo.core.llm

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Come procede il lavoro del modello, per l'indicatore sullo schermo. */
data class LlmProgress(
    val phase: Phase,
    val done: Int = 0,
    val total: Int = 0
) {
    enum class Phase { CARICAMENTO, LETTURA, SCRITTURA }

    /** Da 0 a 1, per la barra. Il caricamento conta come inizio. */
    val fraction: Float
        get() = when (phase) {
            Phase.CARICAMENTO -> 0.02f
            Phase.LETTURA -> 0.05f + 0.45f * ratio()
            Phase.SCRITTURA -> 0.5f + 0.5f * ratio()
        }

    private fun ratio(): Float = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)

    val label: String
        get() = when (phase) {
            Phase.CARICAMENTO -> "Preparo il modello..."
            Phase.LETTURA -> "Leggo quello che hai detto..."
            Phase.SCRITTURA -> "Compilo i campi..."
        }
}

/** Quanto e' durata ogni fase, in secondi: serve a capire dove si perde tempo. */
data class LlmTimings(val load: Int = 0, val read: Int = 0, val write: Int = 0) {
    val total: Int get() = load + read + write
    val label: String get() = "caricamento ${load}s, lettura ${read}s, scrittura ${write}s"
}

sealed interface LlmResult {
    data class Ok(val text: String, val timings: LlmTimings = LlmTimings()) : LlmResult
    data class Failure(val reason: String) : LlmResult
}

/**
 * Il modello linguistico sul telefono.
 *
 * Regola per i telefoni con poca memoria: il modello si carica solo per il
 * tempo di una richiesta e si libera subito dopo. Ci mette qualche secondo
 * in piu' a partire, ma Android non chiude l'app a meta' lavoro.
 *
 * Il file del modello (GGUF) non sta nell'app: lo sceglie l'utente dal
 * telefono. Lo si apre con il descrittore del file, senza copiarlo: un
 * modello pesa un giga e mezzo, due copie non ci stanno.
 */
class LocalLlm(private val context: Context) {

    @Volatile
    private var handle: Long = 0L

    /** Vero se la libreria nativa c'e' ed e' caricabile su questo telefono. */
    private val libDir: String = context.applicationInfo.nativeLibraryDir

    fun isAvailable(): Boolean = LlamaBridge.ensureLibrary(libDir) == null

    /** Perche' il motore non parte, se non parte. Null = va tutto bene. */
    fun unavailableReason(): String? = LlamaBridge.ensureLibrary(libDir)?.let {
        "Il motore di Gemma non si avvia su questo telefono (" + (it.message ?: it.javaClass.simpleName) + ")"
    }

    /** Descrizione del processore vista dal motore. */
    fun systemInfo(): String = LlamaBridge.systemInfo

    suspend fun generate(
        modelUri: Uri,
        prompt: String,
        maxTokens: Int = 320,
        onProgress: (LlmProgress) -> Unit = {}
    ): LlmResult = withContext(Dispatchers.Default) {
        unavailableReason()?.let { return@withContext LlmResult.Failure(it) }
        onProgress(LlmProgress(LlmProgress.Phase.CARICAMENTO))

        val pfd: ParcelFileDescriptor = try {
            context.contentResolver.openFileDescriptor(modelUri, "r")
        } catch (e: Exception) {
            null
        } ?: return@withContext LlmResult.Failure(
            "Non riesco ad aprire il file del modello. Sceglilo di nuovo nelle Impostazioni."
        )

        try {
            val path = "/proc/self/fd/" + pfd.fd.toString()
            val veloci = CpuInfo.performanceCores()
            val t0 = System.currentTimeMillis()
            val h = LlamaBridge.nativeLoad(path, CONTEXT_SIZE, veloci)
            if (h == 0L) {
                return@withContext LlmResult.Failure(
                    "Il file scelto non sembra un modello valido (serve un file .gguf)."
                )
            }
            handle = h
            LlamaBridge.nativeSetThreads(h, veloci, veloci)
            val t1 = System.currentTimeMillis()
            var t2 = 0L
            try {
                val bytes = LlamaBridge.nativeGenerate(h, prompt, maxTokens) { phase, done, total ->
                    if (phase == 1 && t2 == 0L) t2 = System.currentTimeMillis()
                    onProgress(
                        LlmProgress(
                            phase = if (phase == 0) LlmProgress.Phase.LETTURA else LlmProgress.Phase.SCRITTURA,
                            done = done,
                            total = total
                        )
                    )
                }
                val text = String(bytes, Charsets.UTF_8)
                when {
                    text.startsWith("\u0001TROPPO_LUNGO") -> LlmResult.Failure(
                        "Il racconto e' troppo lungo per il modello. Prova a dividerlo."
                    )
                    text.startsWith("\u0001") -> LlmResult.Failure("Il modello si e' fermato per un errore.")
                    else -> {
                        val t3 = System.currentTimeMillis()
                        val lettura = if (t2 == 0L) t3 else t2
                        LlmResult.Ok(
                            text,
                            LlmTimings(
                                load = ((t1 - t0) / 1000).toInt(),
                                read = ((lettura - t1) / 1000).toInt(),
                                write = ((t3 - lettura) / 1000).toInt()
                            )
                        )
                    }
                }
            } finally {
                synchronized(lock) {
                    handle = 0L
                    LlamaBridge.nativeFree(h)
                }
            }
        } finally {
            runCatching { pfd.close() }
        }
    }

    /** Ferma la generazione in corso, se c'e'. */
    fun cancel() {
        synchronized(lock) {
            val h = handle
            if (h != 0L) LlamaBridge.nativeCancel(h)
        }
    }

    private val lock = Any()

    companion object {
        /** Spazio per il racconto piu' la risposta. Basso apposta: pesa sulla memoria. */
        const val CONTEXT_SIZE = 2048
    }
}
