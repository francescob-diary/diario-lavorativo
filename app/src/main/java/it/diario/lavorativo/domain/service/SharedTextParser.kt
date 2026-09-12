package it.diario.lavorativo.domain.service

import it.diario.lavorativo.domain.model.CommunicationChannel
import it.diario.lavorativo.domain.model.SharedText

/**
 * Interpreta il testo condiviso da un'altra app per precompilare la scheda
 * della comunicazione.
 *
 * Perche' serve: WhatsApp condivide i messaggi in un formato riconoscibile,
 * "[12/03/26, 14:32] Mario Rossi: testo". Riconoscerlo evita all'utente di
 * ricopiare a mano il nome e la data. Se il formato non viene riconosciuto
 * non succede niente di male: il testo finisce integro nel contenuto e
 * l'utente compila il resto.
 *
 * Classe di puro Kotlin, senza dipendenze da Android: cosi' e' verificabile
 * con test veri.
 */
class SharedTextParser {

    data class Parsed(
        val channel: CommunicationChannel,
        val contactName: String?,
        val subject: String?,
        val content: String
    )

    fun parse(shared: SharedText): Parsed {
        val channel = channelFor(shared.sourcePackage)
        val text = shared.text.trim()
        val sender = firstSender(text)
        return Parsed(
            channel = channel,
            contactName = sender,
            subject = shared.subject?.takeIf { it.isNotBlank() },
            content = text
        )
    }

    /**
     * Il canale si deduce dal pacchetto di origine. L'elenco copre le app
     * diffuse; per tutte le altre resta ALTRO e decide l'utente.
     */
    fun channelFor(sourcePackage: String?): CommunicationChannel = when {
        sourcePackage == null -> CommunicationChannel.ALTRO
        sourcePackage.startsWith("com.whatsapp") -> CommunicationChannel.WHATSAPP
        sourcePackage.contains("gm", ignoreCase = true) &&
            sourcePackage.contains("google") -> CommunicationChannel.EMAIL
        sourcePackage.contains("android.email") -> CommunicationChannel.EMAIL
        sourcePackage.contains("outlook") -> CommunicationChannel.EMAIL
        sourcePackage.contains("mail") -> CommunicationChannel.EMAIL
        sourcePackage.contains("messaging") -> CommunicationChannel.SMS
        sourcePackage.contains("mms") -> CommunicationChannel.SMS
        else -> CommunicationChannel.ALTRO
    }

    /**
     * Estrae il mittente della prima riga in formato WhatsApp.
     * Accetta sia le parentesi quadre sia la forma senza, usata da alcune
     * versioni: "12/03/26, 14:32 - Mario Rossi: testo".
     */
    fun firstSender(text: String): String? {
        val line = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return null

        val bracketed = BRACKETED.find(line)
        if (bracketed != null) return bracketed.groupValues[1].trim().ifBlank { null }

        val dashed = DASHED.find(line)
        if (dashed != null) return dashed.groupValues[1].trim().ifBlank { null }

        return null
    }

    private companion object {
        /** [12/03/26, 14:32] Mario Rossi: testo */
        val BRACKETED = Regex("""^\[[^\]]{4,30}\]\s*([^:]{1,60}):""")

        /** 12/03/26, 14:32 - Mario Rossi: testo */
        val DASHED = Regex("""^\d{1,2}/\d{1,2}/\d{2,4},?\s+\d{1,2}:\d{2}[^-]{0,8}-\s*([^:]{1,60}):""")
    }
}
