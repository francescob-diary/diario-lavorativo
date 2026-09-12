package it.diario.lavorativo.domain.service

import it.diario.lavorativo.domain.model.CommunicationChannel
import it.diario.lavorativo.domain.model.SharedText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedTextParserTest {

    private val parser = SharedTextParser()

    @Test
    fun `riconosce il mittente nel formato con parentesi quadre`() {
        val sender = parser.firstSender("[12/03/26, 14:32] Mario Rossi: porta il materiale domani")
        assertEquals("Mario Rossi", sender)
    }

    @Test
    fun `riconosce il mittente nel formato con trattino`() {
        val sender = parser.firstSender("12/03/26, 14:32 - Geom. Bianchi: confermo la variante")
        assertEquals("Geom. Bianchi", sender)
    }

    @Test
    fun `prende il mittente della prima riga utile ignorando le righe vuote`() {
        val text = "\n\n[01/02/26, 08:05] Impresa Verdi: iniziamo lunedi"
        assertEquals("Impresa Verdi", parser.firstSender(text))
    }

    @Test
    fun `testo normale non produce nessun mittente`() {
        assertNull(parser.firstSender("Ricordati di ordinare il marmo"))
    }

    @Test
    fun `i due punti dentro una frase non vengono scambiati per un mittente`() {
        assertNull(parser.firstSender("Nota importante: mancano tre bancali"))
    }

    @Test
    fun `testo vuoto non rompe il parser`() {
        assertNull(parser.firstSender(""))
    }

    @Test
    fun `whatsapp viene riconosciuto dal pacchetto`() {
        assertEquals(CommunicationChannel.WHATSAPP, parser.channelFor("com.whatsapp"))
        assertEquals(CommunicationChannel.WHATSAPP, parser.channelFor("com.whatsapp.w4b"))
    }

    @Test
    fun `la posta viene riconosciuta`() {
        assertEquals(CommunicationChannel.EMAIL, parser.channelFor("com.google.android.gm"))
        assertEquals(CommunicationChannel.EMAIL, parser.channelFor("com.microsoft.office.outlook"))
    }

    @Test
    fun `pacchetto sconosciuto resta da decidere all utente`() {
        assertEquals(CommunicationChannel.ALTRO, parser.channelFor("it.qualcosa.strana"))
        assertEquals(CommunicationChannel.ALTRO, parser.channelFor(null))
    }

    @Test
    fun `il contenuto condiviso viene conservato per intero`() {
        val messaggio = "[12/03/26, 14:32] Mario Rossi: prima riga\nseconda riga\nterza riga"
        val parsed = parser.parse(
            SharedText(text = messaggio, sourcePackage = "com.whatsapp")
        )
        assertEquals(CommunicationChannel.WHATSAPP, parsed.channel)
        assertEquals("Mario Rossi", parsed.contactName)
        assertEquals(messaggio, parsed.content)
    }

    @Test
    fun `l oggetto della mail viene riportato`() {
        val parsed = parser.parse(
            SharedText(
                text = "In allegato il computo aggiornato",
                subject = "Variante bagno secondo piano",
                sourcePackage = "com.google.android.gm"
            )
        )
        assertEquals(CommunicationChannel.EMAIL, parsed.channel)
        assertEquals("Variante bagno secondo piano", parsed.subject)
    }

    @Test
    fun `un oggetto vuoto non viene salvato come stringa vuota`() {
        val parsed = parser.parse(SharedText(text = "ciao", subject = "   "))
        assertNull(parsed.subject)
    }
}
