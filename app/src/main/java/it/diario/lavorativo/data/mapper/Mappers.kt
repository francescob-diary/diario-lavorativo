package it.diario.lavorativo.data.mapper

import it.diario.lavorativo.data.local.entity.ActivityEntity
import it.diario.lavorativo.data.local.entity.BreakEntity
import it.diario.lavorativo.data.local.entity.CommunicationEntity
import it.diario.lavorativo.data.local.entity.EventEntity
import it.diario.lavorativo.data.local.entity.PhotoEntity
import it.diario.lavorativo.data.local.entity.SiteEntity
import it.diario.lavorativo.data.local.entity.WorkDayEntity
import it.diario.lavorativo.data.local.relation.WorkDayWithDetails
import it.diario.lavorativo.domain.model.ActivityCategory
import it.diario.lavorativo.domain.model.BreakType
import it.diario.lavorativo.domain.model.Communication
import it.diario.lavorativo.domain.model.CommunicationChannel
import it.diario.lavorativo.domain.model.CommunicationDirection
import it.diario.lavorativo.domain.model.EventSeverity
import it.diario.lavorativo.domain.model.EventType
import it.diario.lavorativo.domain.model.Photo
import it.diario.lavorativo.domain.model.PhotoSource
import it.diario.lavorativo.domain.model.WorkActivity
import it.diario.lavorativo.domain.model.WorkEvent
import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.Site
import it.diario.lavorativo.domain.model.SiteStatus
import it.diario.lavorativo.domain.model.WorkBreak
import it.diario.lavorativo.domain.model.WorkDay
import java.time.Instant
import java.time.LocalDate

/**
 * Conversioni fra entita' del database e modelli di dominio.
 * Tenerle qui evita che Room "sporchi" il dominio e viceversa.
 */

fun Long.toInstant(): Instant = Instant.ofEpochMilli(this)
fun Instant.toEpochMillisLong(): Long = toEpochMilli()
fun Long.toLocalDate(): LocalDate = LocalDate.ofEpochDay(this)
fun LocalDate.toEpochDayLong(): Long = toEpochDay()

fun SiteEntity.toDomain(): Site = Site(
    id = id,
    name = name,
    address = address,
    city = city,
    client = client,
    company = company,
    contact = contact,
    phone = phone,
    startDate = startDate?.toLocalDate(),
    expectedEndDate = expectedEndDate?.toLocalDate(),
    notes = notes,
    status = SiteStatus.fromStorage(status),
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters
)

fun Site.toEntity(createdAt: Long, updatedAt: Long): SiteEntity = SiteEntity(
    id = id,
    name = name,
    address = address,
    city = city,
    client = client,
    company = company,
    contact = contact,
    phone = phone,
    startDate = startDate?.toEpochDayLong(),
    expectedEndDate = expectedEndDate?.toEpochDayLong(),
    notes = notes,
    status = status.name,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun BreakEntity.toDomain(): WorkBreak = WorkBreak(
    id = id,
    workDayId = workDayId,
    startTime = startTime.toInstant(),
    endTime = endTime?.toInstant(),
    type = BreakType.fromStorage(type),
    note = note
)

fun WorkDayWithDetails.toDomain(): WorkDay = WorkDay(
    id = workDay.id,
    date = workDay.date.toLocalDate(),
    startTime = workDay.startTime?.toInstant(),
    endTime = workDay.endTime?.toInstant(),
    site = site?.toDomain(),
    breaks = breaks.map { it.toDomain() }.sortedBy { it.startTime },
    dayType = DayType.fromStorage(workDay.dayType),
    place = workDay.place,
    role = workDay.role,
    description = workDay.description,
    notes = workDay.notes,
    standardMinutesOverride = workDay.standardMinutesOverride,
    travelKm = workDay.travelKm,
    createdAt = workDay.createdAt.toInstant(),
    updatedAt = workDay.updatedAt.toInstant()
)

fun WorkDayEntity.toDomainWithoutRelations(): WorkDay = WorkDay(
    id = id,
    date = date.toLocalDate(),
    startTime = startTime?.toInstant(),
    endTime = endTime?.toInstant(),
    dayType = DayType.fromStorage(dayType),
    place = place,
    role = role,
    description = description,
    notes = notes,
    standardMinutesOverride = standardMinutesOverride,
    travelKm = travelKm,
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant()
)


// ---------------------------------------------------------------------------
// Fase 8: attivita', eventi, comunicazioni
// ---------------------------------------------------------------------------

fun ActivityEntity.toDomain(): WorkActivity = WorkActivity(
    id = id,
    workDayId = workDayId,
    category = ActivityCategory.fromStorage(category),
    description = description,
    startTime = startTime?.toInstant(),
    endTime = endTime?.toInstant(),
    quantity = quantity,
    notes = notes,
    createdAt = createdAt.toInstant()
)

fun WorkActivity.toEntity(): ActivityEntity = ActivityEntity(
    id = id,
    workDayId = workDayId,
    category = category.name,
    description = description,
    startTime = startTime?.toEpochMillisLong(),
    endTime = endTime?.toEpochMillisLong(),
    quantity = quantity,
    notes = notes,
    createdAt = createdAt.toEpochMillisLong()
)

fun EventEntity.toDomain(): WorkEvent = WorkEvent(
    id = id,
    workDayId = workDayId,
    type = EventType.fromStorage(type),
    time = time.toInstant(),
    title = title,
    description = description,
    durationMinutes = durationMinutes,
    severity = EventSeverity.fromStorage(severity),
    unresolved = unresolved,
    createdAt = createdAt.toInstant()
)

fun WorkEvent.toEntity(): EventEntity = EventEntity(
    id = id,
    workDayId = workDayId,
    type = type.name,
    time = time.toEpochMillisLong(),
    title = title,
    description = description,
    durationMinutes = durationMinutes,
    severity = severity.name,
    unresolved = unresolved,
    createdAt = createdAt.toEpochMillisLong()
)

fun CommunicationEntity.toDomain(): Communication = Communication(
    id = id,
    workDayId = workDayId,
    channel = CommunicationChannel.fromStorage(channel),
    direction = CommunicationDirection.fromStorage(direction),
    time = time.toInstant(),
    contactName = contactName,
    contactRef = contactRef,
    subject = subject,
    content = content,
    durationMinutes = durationMinutes,
    requiresFollowUp = requiresFollowUp,
    createdAt = createdAt.toInstant()
)

fun Communication.toEntity(): CommunicationEntity = CommunicationEntity(
    id = id,
    workDayId = workDayId,
    channel = channel.name,
    direction = direction.name,
    time = time.toEpochMillisLong(),
    contactName = contactName,
    contactRef = contactRef,
    subject = subject,
    content = content,
    durationMinutes = durationMinutes,
    requiresFollowUp = requiresFollowUp,
    createdAt = createdAt.toEpochMillisLong()
)


fun PhotoEntity.toDomain(): Photo = Photo(
    id = id,
    workDayId = workDayId,
    activityId = activityId,
    eventId = eventId,
    fileName = fileName,
    galleryUri = galleryUri,
    caption = caption,
    takenAt = takenAt.toInstant(),
    source = PhotoSource.fromStorage(source),
    sizeBytes = sizeBytes,
    createdAt = createdAt.toInstant()
)

fun Photo.toEntity(): PhotoEntity = PhotoEntity(
    id = id,
    workDayId = workDayId,
    activityId = activityId,
    eventId = eventId,
    fileName = fileName,
    galleryUri = galleryUri,
    caption = caption,
    takenAt = takenAt.toEpochMillisLong(),
    source = source.name,
    sizeBytes = sizeBytes,
    createdAt = createdAt.toEpochMillisLong()
)
