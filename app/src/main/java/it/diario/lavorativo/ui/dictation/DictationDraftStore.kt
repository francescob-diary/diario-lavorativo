package it.diario.lavorativo.ui.dictation

import android.content.Context
import it.diario.lavorativo.core.json.JsonException
import it.diario.lavorativo.core.json.JsonParser
import it.diario.lavorativo.core.json.JsonValue
import it.diario.lavorativo.core.json.JsonWriter
import it.diario.lavorativo.core.json.jsonObject
import it.diario.lavorativo.domain.model.DayType

/** Quello che si ritrova riaprendo la dettatura lasciata a meta'. */
data class SavedDictation(
    val stage: DictationStage,
    val transcript: String,
    val answers: List<Pair<String, String>>,
    val form: DictationForm,
    val usedModel: Boolean,
    val appending: Boolean
)

/**
 * La dettatura lasciata a meta' resta scritta sul telefono, una per ogni
 * giornata, finche' non la si salva nel diario o non la si butta. Cosi'
 * cambiare pagina, rispondere al telefono o chiudere l'app non fa perdere
 * niente.
 */
class DictationDraftStore(context: Context) {

    private val prefs = context.getSharedPreferences("bozze_dettatura", Context.MODE_PRIVATE)

    private fun key(epochDay: Long) = "giorno_$epochDay"

    fun save(epochDay: Long, s: DictationUiState) {
        // Niente da tenere: si toglie l'eventuale bozza vecchia.
        if (s.stage == DictationStage.SALVATO ||
            (s.transcript.isBlank() && s.stage == DictationStage.RACCONTO && !s.appending)
        ) {
            clear(epochDay)
            return
        }
        // A meta' elaborazione si torna al racconto: l'elaborazione si rifa'.
        val fase = when (s.stage) {
            DictationStage.ELABORAZIONE -> DictationStage.RACCONTO
            DictationStage.DOMANDE -> DictationStage.CONFERMA
            else -> s.stage
        }
        val f = s.form
        val json = jsonObject {
            put("stage", fase.name)
            put("transcript", s.transcript)
            put("usedModel", s.usedModel)
            put("appending", s.appending)
            putArray("answers", s.answers.map { (d, r) -> jsonObject { put("d", d); put("r", r) } })
            put("form", jsonObject {
                put("dayType", f.dayType.name)
                put("start", f.start)
                put("end", f.end)
                put("description", f.description)
                put("materials", f.materials)
                put("place", f.place)
                put("km", f.km)
                put("notes", f.notes)
                putArray("breaks", f.breaks.map { b -> jsonObject { put("a", b.start); put("b", b.end) } })
                putArray("sites", f.sites.map { x ->
                    jsonObject {
                        put("id", x.siteId)
                        put("name", x.name)
                        put("hours", x.hours)
                        put("work", x.work)
                    }
                })
                putArray("trips", f.trips.map { t ->
                    jsonObject {
                        put("from", t.from)
                        put("to", t.to)
                        put("depart", t.depart)
                        put("arrive", t.arrive)
                    }
                })
            })
        }
        prefs.edit().putString(key(epochDay), JsonWriter.write(json, indent = false)).apply()
    }

    fun load(epochDay: Long): SavedDictation? {
        val text = prefs.getString(key(epochDay), null) ?: return null
        return try {
            val o = JsonParser.parseObject(text)
            val f = o["form"] as? JsonValue.Obj ?: JsonValue.Obj()
            SavedDictation(
                stage = runCatching { DictationStage.valueOf(o.string("stage")) }
                    .getOrDefault(DictationStage.RACCONTO),
                transcript = o.stringOrNull("transcript").orEmpty(),
                answers = o.objects("answers").map { it.string("d") to it.string("r") },
                usedModel = o.bool("usedModel"),
                appending = o.bool("appending"),
                form = DictationForm(
                    dayType = runCatching { DayType.valueOf(f.string("dayType")) }.getOrDefault(DayType.LAVORO),
                    start = f.stringOrNull("start").orEmpty(),
                    end = f.stringOrNull("end").orEmpty(),
                    description = f.stringOrNull("description").orEmpty(),
                    materials = f.stringOrNull("materials").orEmpty(),
                    place = f.stringOrNull("place").orEmpty(),
                    km = f.stringOrNull("km").orEmpty(),
                    notes = f.stringOrNull("notes").orEmpty(),
                    breaks = f.objects("breaks").map { FormBreak(it.string("a"), it.string("b")) },
                    sites = f.objects("sites").map {
                        FormSite(it.longOrNull("id"), it.string("name"), it.string("hours"), it.string("work"))
                    },
                    trips = f.objects("trips").map {
                        FormTrip(it.string("from"), it.string("to"), it.string("depart"), it.string("arrive"))
                    }
                )
            )
        } catch (e: JsonException) {
            null
        } catch (e: RuntimeException) {
            null
        }
    }

    fun clear(epochDay: Long) {
        prefs.edit().remove(key(epochDay)).apply()
    }
}
