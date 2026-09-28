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

    /** Carica la libreria una volta sola. Null se tutto bene, altrimenti l'errore. */
    @Synchronized
    fun ensureLibrary(): Throwable? {
        if (!tried) {
            tried = true
            loaded = if (!CpuInfo.hasDotProduct()) {
                // Libreria compilata per processori con istruzioni veloci:
                // su quelli senza si chiuderebbe l'app. Meglio le regole.
                UnsupportedOperationException("Processore senza istruzioni per il modello")
            } else {
                runCatching { System.loadLibrary("diario_llm") }.exceptionOrNull()
            }
        }
        return loaded
    }

    external fun nativeLoad(path: String, nCtx: Int, nThreads: Int): Long
    external fun nativeSetThreads(handle: Long, genThreads: Int, batchThreads: Int)
    external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int, listener: ProgressListener?): ByteArray
    external fun nativeCancel(handle: Long)
    external fun nativeFree(handle: Long)
}
