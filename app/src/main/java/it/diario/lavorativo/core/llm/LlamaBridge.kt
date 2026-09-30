package it.diario.lavorativo.core.llm

/**
 * Collegamento diretto con la libreria nativa (llama.cpp).
 * Usato solo da [LocalLlm]: il resto dell'app non lo tocca.
 */
internal object LlamaBridge {

    /** Riceve l'avanzamento: fase 0 = lettura del testo, fase 1 = scrittura della risposta. */
    fun interface ProgressListener {
        fun onProgress(phase: Int, done: Int, total: Int)
    }

    @Volatile
    private var loaded: Throwable? = null
    private var tried = false

    /** Cosa ha visto il motore del processore, per la diagnosi. */
    @Volatile
    var systemInfo: String = ""
        private set

    /**
     * Carica il motore una volta sola. Dalla cartella delle librerie
     * dell'app sceglie da solo la variante adatta a questo processore.
     * Null se tutto bene, altrimenti l'errore.
     */
    @Synchronized
    fun ensureLibrary(nativeLibDir: String): Throwable? {
        if (!tried) {
            tried = true
            loaded = runCatching {
                System.loadLibrary("diario_llm")
                systemInfo = nativeInit(nativeLibDir)
                if (systemInfo.startsWith("dispositivi: 0")) {
                    throw IllegalStateException("Nessuna variante del motore adatta a questo processore")
                }
            }.exceptionOrNull()
        }
        return loaded
    }

    external fun nativeInit(nativeLibDir: String): String

    external fun nativeLoad(path: String, nCtx: Int, nThreads: Int): Long
    external fun nativeSetThreads(handle: Long, genThreads: Int, batchThreads: Int)
    external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int, listener: ProgressListener?): ByteArray
    external fun nativeCancel(handle: Long)
    external fun nativeFree(handle: Long)
}
