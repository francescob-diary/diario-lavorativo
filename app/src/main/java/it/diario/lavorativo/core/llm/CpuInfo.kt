package it.diario.lavorativo.core.llm

import java.io.File

/** Cosa sa fare il processore del telefono, letto dal sistema. */
internal object CpuInfo {

    /** Istruzione "prodotto scalare" (asimddp): serve al motore del modello. */
    fun hasDotProduct(): Boolean = runCatching {
        File("/proc/cpuinfo").readLines()
            .filter { it.startsWith("Features", ignoreCase = true) }
            .any { line -> line.split(' ', '\t').any { it.trim() == "asimddp" } }
    }.getOrDefault(true)

    /**
     * Quanti nuclei veloci ci sono. I telefoni mescolano nuclei veloci e
     * lenti: dare lavoro a quelli lenti rallenta tutti, perche' gli altri
     * li aspettano. Si contano quelli con frequenza massima sopra la piu'
     * bassa; se sono tutti uguali, tutti.
     */
    fun performanceCores(): Int {
        val tutti = Runtime.getRuntime().availableProcessors()
        val freq = (0 until tutti).mapNotNull { i ->
            runCatching {
                File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toLong()
            }.getOrNull()
        }
        if (freq.size < tutti || freq.isEmpty()) return (tutti / 2).coerceIn(2, 4)
        val minima = freq.minOrNull() ?: return (tutti / 2).coerceIn(2, 4)
        val veloci = freq.count { it > minima }
        return (if (veloci == 0) tutti else veloci).coerceIn(2, 6)
    }
}
