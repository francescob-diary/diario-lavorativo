package it.diario.lavorativo.ui.entries

import it.diario.lavorativo.domain.model.ActivityCategory
import it.diario.lavorativo.domain.model.CommunicationChannel
import it.diario.lavorativo.domain.model.CommunicationDirection
import it.diario.lavorativo.domain.model.EventSeverity
import it.diario.lavorativo.domain.model.EventType

/** Etichette in italiano corrente, quelle che si usano davvero in cantiere. */

fun ActivityCategory.label(): String = when (this) {
    ActivityCategory.MURATURA -> "Muratura"
    ActivityCategory.MARMO -> "Marmo e pietra"
    ActivityCategory.INTONACO -> "Intonaco"
    ActivityCategory.PAVIMENTI -> "Pavimenti"
    ActivityCategory.RIVESTIMENTI -> "Rivestimenti"
    ActivityCategory.DEMOLIZIONE -> "Demolizione"
    ActivityCategory.SCAVO -> "Scavo"
    ActivityCategory.IMPIANTI -> "Impianti"
    ActivityCategory.CARTONGESSO -> "Cartongesso"
    ActivityCategory.PULIZIA -> "Pulizia"
    ActivityCategory.CARICO_SCARICO -> "Carico e scarico"
    ActivityCategory.SOPRALLUOGO -> "Sopralluogo"
    ActivityCategory.PREPARAZIONE -> "Preparazione"
    ActivityCategory.ALTRO -> "Altro"
}

fun EventType.label(): String = when (this) {
    EventType.RITARDO -> "Ritardo"
    EventType.FERMO_LAVORI -> "Fermo lavori"
    EventType.CONSEGNA_MATERIALE -> "Consegna materiale"
    EventType.MATERIALE_MANCANTE -> "Materiale mancante"
    EventType.VISITA -> "Visita"
    EventType.SOPRALLUOGO_TECNICO -> "Sopralluogo tecnico"
    EventType.INFORTUNIO -> "Infortunio"
    EventType.GUASTO_ATTREZZATURA -> "Guasto attrezzatura"
    EventType.MALTEMPO -> "Maltempo"
    EventType.VARIANTE -> "Variante"
    EventType.PROBLEMA -> "Problema"
    EventType.ALTRO -> "Altro"
}

fun EventSeverity.label(): String = when (this) {
    EventSeverity.NORMALE -> "Normale"
    EventSeverity.IMPORTANTE -> "Importante"
    EventSeverity.GRAVE -> "Grave"
}

fun CommunicationChannel.label(): String = when (this) {
    CommunicationChannel.TELEFONATA -> "Telefonata"
    CommunicationChannel.WHATSAPP -> "WhatsApp"
    CommunicationChannel.EMAIL -> "Email"
    CommunicationChannel.SMS -> "SMS"
    CommunicationChannel.DI_PERSONA -> "Di persona"
    CommunicationChannel.ALTRO -> "Altro"
}

fun CommunicationDirection.label(): String = when (this) {
    CommunicationDirection.RICEVUTA -> "Ricevuta"
    CommunicationDirection.EFFETTUATA -> "Effettuata"
}

/** Verbo corretto per il canale, per non scrivere "telefonata ricevuta da" ovunque. */
fun CommunicationDirection.labelFor(channel: CommunicationChannel): String = when (channel) {
    CommunicationChannel.TELEFONATA ->
        if (this == CommunicationDirection.RICEVUTA) "Ricevuta da" else "Chiamato"
    CommunicationChannel.DI_PERSONA ->
        if (this == CommunicationDirection.RICEVUTA) "Detto da" else "Detto a"
    else ->
        if (this == CommunicationDirection.RICEVUTA) "Da" else "A"
}
