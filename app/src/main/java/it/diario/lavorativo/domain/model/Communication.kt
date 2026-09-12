package it.diario.lavorativo.domain.model

import java.time.Instant

/**
 * Una comunicazione legata al lavoro: telefonata, WhatsApp, mail, messaggio
 * o parola detta di persona.
 *
 * Perche' esiste: quando il cliente sostiene di non aver mai autorizzato un
 * lavoro, il contenuto datato e agganciato al cantiere vale piu' di qualsiasi
 * discussione a memoria.
 *
 * Il contenuto arriva SEMPRE da un gesto dell'utente: digitato, dettato, o
 * ricevuto tramite la condivisione di sistema da WhatsApp o dalla posta.
 * L'app non legge notifiche, non accede al registro chiamate, non intercetta
 * nulla: quelle sono tecniche da spyware e non hanno posto in un diario
 * di lavoro.
 */
data class Communication(
    val id: Long = 0L,
    val workDayId: Long,
    val channel: CommunicationChannel,
    val direction: CommunicationDirection,
    val time: Instant,
    /** Nome della persona o dell'azienda. */
    val contactName: String,
    /** Numero, indirizzo di posta o altro riferimento. */
    val contactRef: String? = null,
    /** Oggetto della mail o titolo breve dato dall'utente. */
    val subject: String? = null,
    /** Testo integrale, incollato, condiviso o dettato. */
    val content: String? = null,
    /** Durata della telefonata in minuti, inserita a mano. */
    val durationMinutes: Int? = null,
    /** Segnala una decisione presa a voce che andra' messa per iscritto. */
    val requiresFollowUp: Boolean = false,
    val createdAt: Instant = Instant.EPOCH
) {
    val hasContent: Boolean get() = !content.isNullOrBlank()

    /** Anteprima per l'elenco, senza far esplodere la riga. */
    fun preview(maxChars: Int = 120): String {
        val text = content?.replace("\n", " ")?.trim().orEmpty()
        if (text.isEmpty()) return ""
        return if (text.length <= maxChars) text else text.take(maxChars).trimEnd() + "..."
    }
}

enum class CommunicationChannel {
    TELEFONATA,
    WHATSAPP,
    EMAIL,
    SMS,
    DI_PERSONA,
    ALTRO;

    companion object {
        fun fromStorage(value: String?): CommunicationChannel =
            entries.firstOrNull { it.name == value } ?: ALTRO
    }
}

/** Chi ha iniziato lo scambio: serve a ricostruire chi ha chiesto cosa. */
enum class CommunicationDirection {
    RICEVUTA,
    EFFETTUATA;

    companion object {
        fun fromStorage(value: String?): CommunicationDirection =
            entries.firstOrNull { it.name == value } ?: RICEVUTA
    }
}
