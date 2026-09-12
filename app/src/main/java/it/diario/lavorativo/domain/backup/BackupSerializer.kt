package it.diario.lavorativo.domain.backup

import it.diario.lavorativo.core.json.JsonException
import it.diario.lavorativo.core.json.JsonParser
import it.diario.lavorativo.core.json.JsonValue
import it.diario.lavorativo.core.json.JsonWriter
import it.diario.lavorativo.core.json.jsonObject

/**
 * Scrive e rilegge il contenuto del backup.
 *
 * Kotlin puro, quindi provabile andata e ritorno. E' la parte piu'
 * delicata dell'app: un errore qui non da' fastidio, cancella il lavoro
 * di mesi.
 *
 * In lettura non si fida di niente. Un file puo' essere stato troncato da
 * un trasferimento andato male, scritto da una versione diversa dell'app o
 * scelto per sbaglio dalla cartella dei download: in tutti questi casi
 * deve dirlo, non ricostruire dati a caso.
 */
object BackupSerializer {

    // ---------------------------------------------------------- scrittura

    fun writeManifest(manifest: BackupManifest): String = JsonWriter.write(
        jsonObject {
            put("formatVersion", manifest.formatVersion)
            put("createdAt", manifest.createdAt)
            put("appVersion", manifest.appVersion)
            put("databaseVersion", manifest.databaseVersion)
            put("workDays", manifest.workDays)
            put("sites", manifest.sites)
            put("photos", manifest.photos)
            put("voiceNotes", manifest.voiceNotes)
            put("vehicles", manifest.vehicles)
            put("fuelStops", manifest.fuelStops)
            put("includesMedia", manifest.includesMedia)
            put("firstDate", manifest.firstDate)
            put("lastDate", manifest.lastDate)
        }
    )

    fun writeContent(content: BackupContent): String = JsonWriter.write(
        jsonObject {
            put("formatVersion", BACKUP_FORMAT_VERSION)
            putArray("workDays", content.workDays.map { d ->
                jsonObject {
                    put("id", d.id)
                    put("date", d.date)
                    put("startTime", d.startTime)
                    put("endTime", d.endTime)
                    put("siteId", d.siteId)
                    put("dayType", d.dayType)
                    put("place", d.place)
                    put("role", d.role)
                    put("description", d.description)
                    put("notes", d.notes)
                    put("standardMinutesOverride", d.standardMinutesOverride)
                    put("travelKm", d.travelKm)
                    put("createdAt", d.createdAt)
                    put("updatedAt", d.updatedAt)
                }
            })
            putArray("breaks", content.breaks.map { b ->
                jsonObject {
                    put("id", b.id)
                    put("workDayId", b.workDayId)
                    put("startTime", b.startTime)
                    put("endTime", b.endTime)
                    put("type", b.type)
                    put("note", b.note)
                }
            })
            putArray("sites", content.sites.map { s ->
                jsonObject {
                    put("id", s.id)
                    put("name", s.name)
                    put("address", s.address)
                    put("city", s.city)
                    put("client", s.client)
                    put("company", s.company)
                    put("contact", s.contact)
                    put("phone", s.phone)
                    put("startDate", s.startDate)
                    put("expectedEndDate", s.expectedEndDate)
                    put("notes", s.notes)
                    put("status", s.status)
                    put("latitude", s.latitude)
                    put("longitude", s.longitude)
                    put("radiusMeters", s.radiusMeters)
                    put("createdAt", s.createdAt)
                    put("updatedAt", s.updatedAt)
                }
            })
            putArray("activities", content.activities.map { a ->
                jsonObject {
                    put("id", a.id)
                    put("workDayId", a.workDayId)
                    put("category", a.category)
                    put("description", a.description)
                    put("startTime", a.startTime)
                    put("endTime", a.endTime)
                    put("quantity", a.quantity)
                    put("notes", a.notes)
                    put("createdAt", a.createdAt)
                }
            })
            putArray("events", content.events.map { e ->
                jsonObject {
                    put("id", e.id)
                    put("workDayId", e.workDayId)
                    put("type", e.type)
                    put("time", e.time)
                    put("title", e.title)
                    put("description", e.description)
                    put("durationMinutes", e.durationMinutes)
                    put("severity", e.severity)
                    put("unresolved", e.unresolved)
                    put("createdAt", e.createdAt)
                }
            })
            putArray("communications", content.communications.map { c ->
                jsonObject {
                    put("id", c.id)
                    put("workDayId", c.workDayId)
                    put("channel", c.channel)
                    put("direction", c.direction)
                    put("time", c.time)
                    put("contactName", c.contactName)
                    put("contactRef", c.contactRef)
                    put("subject", c.subject)
                    put("content", c.content)
                    put("durationMinutes", c.durationMinutes)
                    put("requiresFollowUp", c.requiresFollowUp)
                    put("createdAt", c.createdAt)
                }
            })
            putArray("photos", content.photos.map { p ->
                jsonObject {
                    put("id", p.id)
                    put("workDayId", p.workDayId)
                    put("activityId", p.activityId)
                    put("eventId", p.eventId)
                    put("fileName", p.fileName)
                    put("galleryUri", p.galleryUri)
                    put("caption", p.caption)
                    put("takenAt", p.takenAt)
                    put("source", p.source)
                    put("sizeBytes", p.sizeBytes)
                    put("createdAt", p.createdAt)
                }
            })
            putArray("voiceNotes", content.voiceNotes.map { v ->
                jsonObject {
                    put("id", v.id)
                    put("workDayId", v.workDayId)
                    put("fileName", v.fileName)
                    put("durationSeconds", v.durationSeconds)
                    put("note", v.note)
                    put("recordedAt", v.recordedAt)
                    put("createdAt", v.createdAt)
                }
            })
            putArray("vehicles", content.vehicles.map { v ->
                jsonObject {
                    put("id", v.id)
                    put("name", v.name)
                    put("plate", v.plate)
                    put("active", v.active)
                    put("notes", v.notes)
                    put("createdAt", v.createdAt)
                    put("updatedAt", v.updatedAt)
                }
            })
            putArray("fuelStops", content.fuelStops.map { r ->
                jsonObject {
                    put("id", r.id)
                    put("vehicleId", r.vehicleId)
                    put("date", r.date)
                    put("liters", r.liters)
                    put("amountCents", r.amountCents)
                    put("odometerKm", r.odometerKm)
                    put("fullTank", r.fullTank)
                    put("station", r.station)
                    put("notes", r.notes)
                    put("createdAt", r.createdAt)
                }
            })
            putArray("vehicleExpenses", content.vehicleExpenses.map { e ->
                jsonObject {
                    put("id", e.id)
                    put("vehicleId", e.vehicleId)
                    put("workDayId", e.workDayId)
                    put("date", e.date)
                    put("type", e.type)
                    put("amountCents", e.amountCents)
                    put("place", e.place)
                    put("notes", e.notes)
                    put("createdAt", e.createdAt)
                }
            })
            putArray("maintenances", content.maintenances.map { m ->
                jsonObject {
                    put("id", m.id)
                    put("vehicleId", m.vehicleId)
                    put("date", m.date)
                    put("type", m.type)
                    put("description", m.description)
                    put("odometerKm", m.odometerKm)
                    put("amountCents", m.amountCents)
                    put("workshop", m.workshop)
                    put("nextDueDate", m.nextDueDate)
                    put("nextDueKm", m.nextDueKm)
                    put("notes", m.notes)
                    put("createdAt", m.createdAt)
                }
            })
            content.settings?.let { s ->
                put("settings", jsonObject {
                    put("userName", s.userName)
                    put("standardWorkMinutes", s.standardWorkMinutes)
                    put("overtimeEnabled", s.overtimeEnabled)
                    put("reminderEnabled", s.reminderEnabled)
                    put("reminderDayOfWeek", s.reminderDayOfWeek)
                    put("reminderHour", s.reminderHour)
                    put("reminderMinute", s.reminderMinute)
                })
            }
        }
    )

    // ---------------------------------------------------------- lettura

    fun readManifest(text: String): BackupManifest? = try {
        val o = JsonParser.parseObject(text)
        BackupManifest(
            formatVersion = o.int("formatVersion", 0),
            createdAt = o.long("createdAt"),
            appVersion = o.string("appVersion"),
            databaseVersion = o.int("databaseVersion"),
            workDays = o.int("workDays"),
            sites = o.int("sites"),
            photos = o.int("photos"),
            voiceNotes = o.int("voiceNotes"),
            // Un backup di formato 1 non ha queste chiavi: tornano a zero,
            // che e' esattamente quello che c'era dentro.
            vehicles = o.int("vehicles", 0),
            fuelStops = o.int("fuelStops", 0),
            includesMedia = o.bool("includesMedia"),
            firstDate = o.longOrNull("firstDate"),
            lastDate = o.longOrNull("lastDate")
        )
    } catch (e: JsonException) {
        null
    }

    /**
     * Rilegge il contenuto.
     *
     * Le righe che non stanno in piedi vengono saltate, non fanno cadere
     * tutto il ripristino: se in un backup da mille giornate una riga e'
     * rovinata, meglio recuperarne 999 che nessuna. Chi salta viene
     * contato e riferito.
     */
    fun readContent(text: String): BackupReadResult {
        val root = try {
            JsonParser.parseObject(text)
        } catch (e: JsonException) {
            return BackupReadResult.Damaged(e.message ?: "File non leggibile")
        }

        val versione = root.int("formatVersion", 0)
        if (versione > BACKUP_FORMAT_VERSION) {
            return BackupReadResult.TooNew(versione)
        }
        if (versione < 1) {
            return BackupReadResult.Damaged("Manca il numero di versione del formato")
        }

        val content = BackupContent(
            workDays = root.objects("workDays").mapNotNull { readWorkDay(it) },
            breaks = root.objects("breaks").mapNotNull { readBreak(it) },
            sites = root.objects("sites").mapNotNull { readSite(it) },
            activities = root.objects("activities").mapNotNull { readActivity(it) },
            events = root.objects("events").mapNotNull { readEvent(it) },
            communications = root.objects("communications").mapNotNull { readCommunication(it) },
            photos = root.objects("photos").mapNotNull { readPhoto(it) },
            voiceNotes = root.objects("voiceNotes").mapNotNull { readVoiceNote(it) },
            vehicles = root.objects("vehicles").mapNotNull { readVehicle(it) },
            fuelStops = root.objects("fuelStops").mapNotNull { readFuelStop(it) },
            vehicleExpenses = root.objects("vehicleExpenses").mapNotNull { readExpense(it) },
            maintenances = root.objects("maintenances").mapNotNull { readMaintenance(it) },
            settings = (root["settings"] as? JsonValue.Obj)?.let { s ->
                BackupSettings(
                    userName = s.string("userName"),
                    standardWorkMinutes = s.int("standardWorkMinutes", 480),
                    overtimeEnabled = s.bool("overtimeEnabled", true),
                    reminderEnabled = s.bool("reminderEnabled", true),
                    reminderDayOfWeek = s.int("reminderDayOfWeek", 1),
                    reminderHour = s.int("reminderHour", 8),
                    reminderMinute = s.int("reminderMinute", 20)
                )
            }
        )

        return BackupReadResult.Ok(
            manifest = BackupManifest(
                formatVersion = versione,
                createdAt = 0L,
                appVersion = "",
                databaseVersion = 0,
                workDays = content.workDays.size,
                sites = content.sites.size,
                photos = content.photos.size,
                voiceNotes = content.voiceNotes.size,
                vehicles = content.vehicles.size,
                fuelStops = content.fuelStops.size,
                includesMedia = false,
                firstDate = content.workDays.minOfOrNull { it.date },
                lastDate = content.workDays.maxOfOrNull { it.date }
            ),
            content = content
        )
    }

    /**
     * Conta le righe che il file conteneva ma che non si sono potute
     * rileggere. Serve per avvisare invece di far finta di niente.
     */
    fun countSkipped(text: String, content: BackupContent): Int = try {
        val root = JsonParser.parseObject(text)
        (root.objects("workDays").size - content.workDays.size) +
            (root.objects("breaks").size - content.breaks.size) +
            (root.objects("sites").size - content.sites.size) +
            (root.objects("activities").size - content.activities.size) +
            (root.objects("events").size - content.events.size) +
            (root.objects("communications").size - content.communications.size) +
            (root.objects("photos").size - content.photos.size) +
            (root.objects("voiceNotes").size - content.voiceNotes.size) +
            (root.objects("vehicles").size - content.vehicles.size) +
            (root.objects("fuelStops").size - content.fuelStops.size) +
            (root.objects("vehicleExpenses").size - content.vehicleExpenses.size) +
            (root.objects("maintenances").size - content.maintenances.size)
    } catch (e: JsonException) {
        0
    }

    // ------------------------------------------------------ righe singole

    // Una riga senza identificativo o senza la data non e' recuperabile:
    // finirebbe nel database come spazzatura collegata a niente.

    private fun readWorkDay(o: JsonValue.Obj): BackupWorkDay? {
        val id = o.longOrNull("id") ?: return null
        val date = o.longOrNull("date") ?: return null
        return BackupWorkDay(
            id = id,
            date = date,
            startTime = o.longOrNull("startTime"),
            endTime = o.longOrNull("endTime"),
            siteId = o.longOrNull("siteId"),
            dayType = o.stringOrNull("dayType") ?: "LAVORO",
            place = o.stringOrNull("place"),
            role = o.stringOrNull("role"),
            description = o.stringOrNull("description"),
            notes = o.stringOrNull("notes"),
            standardMinutesOverride = o.intOrNull("standardMinutesOverride"),
            travelKm = o.intOrNull("travelKm"),
            createdAt = o.long("createdAt"),
            updatedAt = o.long("updatedAt")
        )
    }

    private fun readBreak(o: JsonValue.Obj): BackupBreak? {
        val id = o.longOrNull("id") ?: return null
        val workDayId = o.longOrNull("workDayId") ?: return null
        val startTime = o.longOrNull("startTime") ?: return null
        return BackupBreak(
            id = id,
            workDayId = workDayId,
            startTime = startTime,
            endTime = o.longOrNull("endTime"),
            type = o.stringOrNull("type") ?: "PAUSA",
            note = o.stringOrNull("note")
        )
    }

    private fun readSite(o: JsonValue.Obj): BackupSite? {
        val id = o.longOrNull("id") ?: return null
        val name = o.stringOrNull("name") ?: return null
        return BackupSite(
            id = id,
            name = name,
            address = o.stringOrNull("address"),
            city = o.stringOrNull("city"),
            client = o.stringOrNull("client"),
            company = o.stringOrNull("company"),
            contact = o.stringOrNull("contact"),
            phone = o.stringOrNull("phone"),
            startDate = o.longOrNull("startDate"),
            expectedEndDate = o.longOrNull("expectedEndDate"),
            notes = o.stringOrNull("notes"),
            status = o.stringOrNull("status") ?: "ATTIVO",
            latitude = o.doubleOrNull("latitude"),
            longitude = o.doubleOrNull("longitude"),
            radiusMeters = o.int("radiusMeters", 150),
            createdAt = o.long("createdAt"),
            updatedAt = o.long("updatedAt")
        )
    }

    private fun readActivity(o: JsonValue.Obj): BackupActivity? {
        val id = o.longOrNull("id") ?: return null
        val workDayId = o.longOrNull("workDayId") ?: return null
        return BackupActivity(
            id = id,
            workDayId = workDayId,
            category = o.stringOrNull("category") ?: "ALTRO",
            description = o.stringOrNull("description") ?: "",
            startTime = o.longOrNull("startTime"),
            endTime = o.longOrNull("endTime"),
            quantity = o.stringOrNull("quantity"),
            notes = o.stringOrNull("notes"),
            createdAt = o.long("createdAt")
        )
    }

    private fun readEvent(o: JsonValue.Obj): BackupEvent? {
        val id = o.longOrNull("id") ?: return null
        val workDayId = o.longOrNull("workDayId") ?: return null
        val time = o.longOrNull("time") ?: return null
        return BackupEvent(
            id = id,
            workDayId = workDayId,
            type = o.stringOrNull("type") ?: "ALTRO",
            time = time,
            title = o.stringOrNull("title") ?: "",
            description = o.stringOrNull("description"),
            durationMinutes = o.intOrNull("durationMinutes"),
            severity = o.stringOrNull("severity") ?: "NORMALE",
            unresolved = o.bool("unresolved"),
            createdAt = o.long("createdAt")
        )
    }

    private fun readCommunication(o: JsonValue.Obj): BackupCommunication? {
        val id = o.longOrNull("id") ?: return null
        val workDayId = o.longOrNull("workDayId") ?: return null
        val time = o.longOrNull("time") ?: return null
        return BackupCommunication(
            id = id,
            workDayId = workDayId,
            channel = o.stringOrNull("channel") ?: "ALTRO",
            direction = o.stringOrNull("direction") ?: "RICEVUTA",
            time = time,
            contactName = o.stringOrNull("contactName") ?: "",
            contactRef = o.stringOrNull("contactRef"),
            subject = o.stringOrNull("subject"),
            content = o.stringOrNull("content"),
            durationMinutes = o.intOrNull("durationMinutes"),
            requiresFollowUp = o.bool("requiresFollowUp"),
            createdAt = o.long("createdAt")
        )
    }

    private fun readPhoto(o: JsonValue.Obj): BackupPhoto? {
        val id = o.longOrNull("id") ?: return null
        val workDayId = o.longOrNull("workDayId") ?: return null
        val fileName = o.stringOrNull("fileName") ?: return null
        return BackupPhoto(
            id = id,
            workDayId = workDayId,
            activityId = o.longOrNull("activityId"),
            eventId = o.longOrNull("eventId"),
            fileName = fileName,
            galleryUri = o.stringOrNull("galleryUri"),
            caption = o.stringOrNull("caption"),
            takenAt = o.long("takenAt"),
            source = o.stringOrNull("source") ?: "FOTOCAMERA",
            sizeBytes = o.long("sizeBytes"),
            createdAt = o.long("createdAt")
        )
    }

    private fun readVoiceNote(o: JsonValue.Obj): BackupVoiceNote? {
        val id = o.longOrNull("id") ?: return null
        val workDayId = o.longOrNull("workDayId") ?: return null
        val fileName = o.stringOrNull("fileName") ?: return null
        return BackupVoiceNote(
            id = id,
            workDayId = workDayId,
            fileName = fileName,
            durationSeconds = o.int("durationSeconds"),
            note = o.stringOrNull("note"),
            recordedAt = o.long("recordedAt"),
            createdAt = o.long("createdAt")
        )
    }

    // ---- fase 13: mezzo aziendale ----

    private fun readVehicle(o: JsonValue.Obj): BackupVehicle? {
        val id = o.longOrNull("id") ?: return null
        val name = o.stringOrNull("name") ?: return null
        return BackupVehicle(
            id = id,
            name = name,
            plate = o.stringOrNull("plate"),
            active = o.bool("active", true),
            notes = o.stringOrNull("notes"),
            createdAt = o.long("createdAt"),
            updatedAt = o.long("updatedAt")
        )
    }

    private fun readFuelStop(o: JsonValue.Obj): BackupFuelStop? {
        val id = o.longOrNull("id") ?: return null
        val vehicleId = o.longOrNull("vehicleId") ?: return null
        val date = o.longOrNull("date") ?: return null
        return BackupFuelStop(
            id = id,
            vehicleId = vehicleId,
            date = date,
            liters = o.double("liters", 0.0),
            amountCents = o.long("amountCents"),
            odometerKm = o.intOrNull("odometerKm"),
            fullTank = o.bool("fullTank", true),
            station = o.stringOrNull("station"),
            notes = o.stringOrNull("notes"),
            createdAt = o.long("createdAt")
        )
    }

    private fun readExpense(o: JsonValue.Obj): BackupVehicleExpense? {
        val id = o.longOrNull("id") ?: return null
        val vehicleId = o.longOrNull("vehicleId") ?: return null
        val date = o.longOrNull("date") ?: return null
        return BackupVehicleExpense(
            id = id,
            vehicleId = vehicleId,
            workDayId = o.longOrNull("workDayId"),
            date = date,
            type = o.stringOrNull("type") ?: "ALTRO",
            amountCents = o.long("amountCents"),
            place = o.stringOrNull("place"),
            notes = o.stringOrNull("notes"),
            createdAt = o.long("createdAt")
        )
    }

    private fun readMaintenance(o: JsonValue.Obj): BackupMaintenance? {
        val id = o.longOrNull("id") ?: return null
        val vehicleId = o.longOrNull("vehicleId") ?: return null
        val date = o.longOrNull("date") ?: return null
        return BackupMaintenance(
            id = id,
            vehicleId = vehicleId,
            date = date,
            type = o.stringOrNull("type") ?: "ALTRO",
            description = o.stringOrNull("description"),
            odometerKm = o.intOrNull("odometerKm"),
            // L'importo puo' mancare davvero: un intervento in garanzia non
            // si paga. Zero e "non lo so" non sono la stessa cosa.
            amountCents = o.longOrNull("amountCents"),
            workshop = o.stringOrNull("workshop"),
            nextDueDate = o.longOrNull("nextDueDate"),
            nextDueKm = o.intOrNull("nextDueKm"),
            notes = o.stringOrNull("notes"),
            createdAt = o.long("createdAt")
        )
    }
}
