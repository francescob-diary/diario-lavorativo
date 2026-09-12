package it.diario.lavorativo.data.backup

import it.diario.lavorativo.data.local.entity.ActivityEntity
import it.diario.lavorativo.data.local.entity.BreakEntity
import it.diario.lavorativo.data.local.entity.CommunicationEntity
import it.diario.lavorativo.data.local.entity.EventEntity
import it.diario.lavorativo.data.local.entity.PhotoEntity
import it.diario.lavorativo.data.local.entity.SiteEntity
import it.diario.lavorativo.data.local.entity.VoiceNoteEntity
import it.diario.lavorativo.data.local.entity.VehicleEntity
import it.diario.lavorativo.data.local.entity.FuelStopEntity
import it.diario.lavorativo.data.local.entity.VehicleExpenseEntity
import it.diario.lavorativo.data.local.entity.MaintenanceEntity
import it.diario.lavorativo.data.local.entity.WorkDayEntity
import it.diario.lavorativo.domain.backup.BackupActivity
import it.diario.lavorativo.domain.backup.BackupBreak
import it.diario.lavorativo.domain.backup.BackupCommunication
import it.diario.lavorativo.domain.backup.BackupEvent
import it.diario.lavorativo.domain.backup.BackupPhoto
import it.diario.lavorativo.domain.backup.BackupSite
import it.diario.lavorativo.domain.backup.BackupVoiceNote
import it.diario.lavorativo.domain.backup.BackupVehicle
import it.diario.lavorativo.domain.backup.BackupFuelStop
import it.diario.lavorativo.domain.backup.BackupVehicleExpense
import it.diario.lavorativo.domain.backup.BackupMaintenance
import it.diario.lavorativo.domain.backup.BackupWorkDay

/**
 * Conversioni fra le tabelle del database e le righe del backup.
 *
 * Copie campo per campo, senza nessuna logica: e' voluto. Tutto quello che
 * ragiona sta nel serializzatore, che si puo' provare; qui c'e' solo la
 * ricopiatura, che si controlla a occhio.
 */

fun WorkDayEntity.toBackup() = BackupWorkDay(
    id = id, date = date, startTime = startTime, endTime = endTime,
    siteId = siteId, dayType = dayType, place = place, role = role,
    description = description, notes = notes,
    standardMinutesOverride = standardMinutesOverride,
    travelKm = travelKm,
    createdAt = createdAt, updatedAt = updatedAt
)

fun BackupWorkDay.toEntity() = WorkDayEntity(
    id = id, date = date, startTime = startTime, endTime = endTime,
    siteId = siteId, dayType = dayType, place = place, role = role,
    description = description, notes = notes,
    standardMinutesOverride = standardMinutesOverride,
    travelKm = travelKm,
    createdAt = createdAt, updatedAt = updatedAt
)

fun BreakEntity.toBackup() = BackupBreak(id, workDayId, startTime, endTime, type, note)

fun BackupBreak.toEntity() = BreakEntity(
    id = id, workDayId = workDayId, startTime = startTime,
    endTime = endTime, type = type, note = note
)

fun SiteEntity.toBackup() = BackupSite(
    id, name, address, city, client, company, contact, phone,
    startDate, expectedEndDate, notes, status, latitude, longitude,
    radiusMeters, createdAt, updatedAt
)

fun BackupSite.toEntity() = SiteEntity(
    id = id, name = name, address = address, city = city, client = client,
    company = company, contact = contact, phone = phone,
    startDate = startDate, expectedEndDate = expectedEndDate, notes = notes,
    status = status, latitude = latitude, longitude = longitude,
    radiusMeters = radiusMeters, createdAt = createdAt, updatedAt = updatedAt
)

fun ActivityEntity.toBackup() = BackupActivity(
    id, workDayId, category, description, startTime, endTime,
    quantity, notes, createdAt
)

fun BackupActivity.toEntity() = ActivityEntity(
    id = id, workDayId = workDayId, category = category,
    description = description, startTime = startTime, endTime = endTime,
    quantity = quantity, notes = notes, createdAt = createdAt
)

fun EventEntity.toBackup() = BackupEvent(
    id, workDayId, type, time, title, description, durationMinutes,
    severity, unresolved, createdAt
)

fun BackupEvent.toEntity() = EventEntity(
    id = id, workDayId = workDayId, type = type, time = time, title = title,
    description = description, durationMinutes = durationMinutes,
    severity = severity, unresolved = unresolved, createdAt = createdAt
)

fun CommunicationEntity.toBackup() = BackupCommunication(
    id, workDayId, channel, direction, time, contactName, contactRef,
    subject, content, durationMinutes, requiresFollowUp, createdAt
)

fun BackupCommunication.toEntity() = CommunicationEntity(
    id = id, workDayId = workDayId, channel = channel, direction = direction,
    time = time, contactName = contactName, contactRef = contactRef,
    subject = subject, content = content, durationMinutes = durationMinutes,
    requiresFollowUp = requiresFollowUp, createdAt = createdAt
)

fun PhotoEntity.toBackup() = BackupPhoto(
    id, workDayId, activityId, eventId, fileName, galleryUri, caption,
    takenAt, source, sizeBytes, createdAt
)

fun BackupPhoto.toEntity() = PhotoEntity(
    id = id, workDayId = workDayId, activityId = activityId,
    eventId = eventId, fileName = fileName, galleryUri = galleryUri,
    caption = caption, takenAt = takenAt, source = source,
    sizeBytes = sizeBytes, createdAt = createdAt
)

fun VoiceNoteEntity.toBackup() = BackupVoiceNote(
    id, workDayId, fileName, durationSeconds, note, recordedAt, createdAt
)

fun BackupVoiceNote.toEntity() = VoiceNoteEntity(
    id = id, workDayId = workDayId, fileName = fileName,
    durationSeconds = durationSeconds, note = note,
    recordedAt = recordedAt, createdAt = createdAt
)


// ---------------------------------------------------------------------------
// Fase 13: mezzo aziendale
// ---------------------------------------------------------------------------

fun VehicleEntity.toBackup() = BackupVehicle(
    id = id, name = name, plate = plate, active = active,
    notes = notes, createdAt = createdAt, updatedAt = updatedAt
)

fun BackupVehicle.toEntity() = VehicleEntity(
    id = id, name = name, plate = plate, active = active,
    notes = notes, createdAt = createdAt, updatedAt = updatedAt
)

fun FuelStopEntity.toBackup() = BackupFuelStop(
    id = id, vehicleId = vehicleId, date = date, liters = liters,
    amountCents = amountCents, odometerKm = odometerKm, fullTank = fullTank,
    station = station, notes = notes, createdAt = createdAt
)

fun BackupFuelStop.toEntity() = FuelStopEntity(
    id = id, vehicleId = vehicleId, date = date, liters = liters,
    amountCents = amountCents, odometerKm = odometerKm, fullTank = fullTank,
    station = station, notes = notes, createdAt = createdAt
)

fun VehicleExpenseEntity.toBackup() = BackupVehicleExpense(
    id = id, vehicleId = vehicleId, workDayId = workDayId, date = date,
    type = type, amountCents = amountCents, place = place,
    notes = notes, createdAt = createdAt
)

fun BackupVehicleExpense.toEntity() = VehicleExpenseEntity(
    id = id, vehicleId = vehicleId, workDayId = workDayId, date = date,
    type = type, amountCents = amountCents, place = place,
    notes = notes, createdAt = createdAt
)

fun MaintenanceEntity.toBackup() = BackupMaintenance(
    id = id, vehicleId = vehicleId, date = date, type = type,
    description = description, odometerKm = odometerKm, amountCents = amountCents,
    workshop = workshop, nextDueDate = nextDueDate, nextDueKm = nextDueKm,
    notes = notes, createdAt = createdAt
)

fun BackupMaintenance.toEntity() = MaintenanceEntity(
    id = id, vehicleId = vehicleId, date = date, type = type,
    description = description, odometerKm = odometerKm, amountCents = amountCents,
    workshop = workshop, nextDueDate = nextDueDate, nextDueKm = nextDueKm,
    notes = notes, createdAt = createdAt
)
