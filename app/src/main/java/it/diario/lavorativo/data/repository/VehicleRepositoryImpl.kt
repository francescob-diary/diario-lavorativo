package it.diario.lavorativo.data.repository

import it.diario.lavorativo.core.time.AppClock
import it.diario.lavorativo.data.local.dao.FuelStopDao
import it.diario.lavorativo.data.local.dao.MaintenanceDao
import it.diario.lavorativo.data.local.dao.VehicleDao
import it.diario.lavorativo.data.local.dao.VehicleExpenseDao
import it.diario.lavorativo.data.local.entity.FuelStopEntity
import it.diario.lavorativo.data.local.entity.MaintenanceEntity
import it.diario.lavorativo.data.local.entity.VehicleEntity
import it.diario.lavorativo.data.local.entity.VehicleExpenseEntity
import it.diario.lavorativo.domain.model.ExpenseType
import it.diario.lavorativo.domain.model.FuelStop
import it.diario.lavorativo.domain.model.Maintenance
import it.diario.lavorativo.domain.model.MaintenanceType
import it.diario.lavorativo.domain.model.Vehicle
import it.diario.lavorativo.domain.model.VehicleExpense
import it.diario.lavorativo.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

class VehicleRepositoryImpl(
    private val vehicleDao: VehicleDao,
    private val fuelStopDao: FuelStopDao,
    private val expenseDao: VehicleExpenseDao,
    private val maintenanceDao: MaintenanceDao,
    private val clock: AppClock
) : VehicleRepository {

    // ---- mezzi ----

    override fun observeVehicles(): Flow<List<Vehicle>> =
        vehicleDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun vehicleById(id: Long): Vehicle? = vehicleDao.byId(id)?.toDomain()

    override suspend fun activeVehicle(): Vehicle? = vehicleDao.firstActive()?.toDomain()

    override suspend fun saveVehicle(vehicle: Vehicle): Long {
        val adesso = clock.now().toEpochMilli()
        val riga = vehicle.toEntity().copy(
            createdAt = if (vehicle.id == 0L) adesso else vehicle.createdAt.toEpochMilli(),
            updatedAt = adesso
        )
        return vehicleDao.insert(riga)
    }

    override suspend fun deleteVehicle(vehicle: Vehicle) {
        vehicleDao.byId(vehicle.id)?.let { vehicleDao.delete(it) }
    }

    override suspend fun vehicleCount(): Int = vehicleDao.count()

    // ---- rifornimenti ----

    override fun observeFuelStops(vehicleId: Long): Flow<List<FuelStop>> =
        fuelStopDao.observeForVehicle(vehicleId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun fuelStops(
        vehicleId: Long,
        from: LocalDate,
        to: LocalDate
    ): List<FuelStop> =
        fuelStopDao.betweenForVehicle(vehicleId, from.toEpochDay(), to.toEpochDay())
            .map { it.toDomain() }

    override suspend fun saveFuelStop(stop: FuelStop): Long {
        val riga = stop.toEntity().copy(
            createdAt = if (stop.id == 0L) clock.now().toEpochMilli()
            else stop.createdAt.toEpochMilli()
        )
        return fuelStopDao.insert(riga)
    }

    override suspend fun deleteFuelStop(stop: FuelStop) {
        fuelStopDao.byId(stop.id)?.let { fuelStopDao.delete(it) }
    }

    // ---- spese ----

    override fun observeExpenses(vehicleId: Long): Flow<List<VehicleExpense>> =
        expenseDao.observeForVehicle(vehicleId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun expenses(
        vehicleId: Long,
        from: LocalDate,
        to: LocalDate
    ): List<VehicleExpense> =
        expenseDao.betweenForVehicle(vehicleId, from.toEpochDay(), to.toEpochDay())
            .map { it.toDomain() }

    override suspend fun saveExpense(expense: VehicleExpense): Long {
        val riga = expense.toEntity().copy(
            createdAt = if (expense.id == 0L) clock.now().toEpochMilli()
            else expense.createdAt.toEpochMilli()
        )
        return expenseDao.insert(riga)
    }

    override suspend fun deleteExpense(expense: VehicleExpense) {
        expenseDao.byId(expense.id)?.let { expenseDao.delete(it) }
    }

    // ---- interventi ----

    override fun observeMaintenances(vehicleId: Long): Flow<List<Maintenance>> =
        maintenanceDao.observeForVehicle(vehicleId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun allMaintenances(vehicleId: Long): List<Maintenance> =
        maintenanceDao.allForVehicle(vehicleId).map { it.toDomain() }

    override suspend fun maintenances(
        vehicleId: Long,
        from: LocalDate,
        to: LocalDate
    ): List<Maintenance> =
        maintenanceDao.betweenForVehicle(vehicleId, from.toEpochDay(), to.toEpochDay())
            .map { it.toDomain() }

    override suspend fun saveMaintenance(maintenance: Maintenance): Long {
        val riga = maintenance.toEntity().copy(
            createdAt = if (maintenance.id == 0L) clock.now().toEpochMilli()
            else maintenance.createdAt.toEpochMilli()
        )
        return maintenanceDao.insert(riga)
    }

    override suspend fun deleteMaintenance(maintenance: Maintenance) {
        maintenanceDao.byId(maintenance.id)?.let { maintenanceDao.delete(it) }
    }

    // ---- conversioni ----

    private fun VehicleEntity.toDomain() = Vehicle(
        id = id,
        name = name,
        plate = plate,
        active = active,
        notes = notes,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt)
    )

    private fun Vehicle.toEntity() = VehicleEntity(
        id = id,
        name = name,
        plate = plate,
        active = active,
        notes = notes,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli()
    )

    private fun FuelStopEntity.toDomain() = FuelStop(
        id = id,
        vehicleId = vehicleId,
        date = LocalDate.ofEpochDay(date),
        liters = liters,
        amountCents = amountCents,
        odometerKm = odometerKm,
        fullTank = fullTank,
        station = station,
        notes = notes,
        createdAt = Instant.ofEpochMilli(createdAt)
    )

    private fun FuelStop.toEntity() = FuelStopEntity(
        id = id,
        vehicleId = vehicleId,
        date = date.toEpochDay(),
        liters = liters,
        amountCents = amountCents,
        odometerKm = odometerKm,
        fullTank = fullTank,
        station = station,
        notes = notes,
        createdAt = createdAt.toEpochMilli()
    )

    private fun VehicleExpenseEntity.toDomain() = VehicleExpense(
        id = id,
        vehicleId = vehicleId,
        workDayId = workDayId,
        date = LocalDate.ofEpochDay(date),
        type = ExpenseType.fromName(type),
        amountCents = amountCents,
        place = place,
        notes = notes,
        createdAt = Instant.ofEpochMilli(createdAt)
    )

    private fun VehicleExpense.toEntity() = VehicleExpenseEntity(
        id = id,
        vehicleId = vehicleId,
        workDayId = workDayId,
        date = date.toEpochDay(),
        type = type.name,
        amountCents = amountCents,
        place = place,
        notes = notes,
        createdAt = createdAt.toEpochMilli()
    )

    private fun MaintenanceEntity.toDomain() = Maintenance(
        id = id,
        vehicleId = vehicleId,
        date = LocalDate.ofEpochDay(date),
        type = MaintenanceType.fromName(type),
        description = description,
        odometerKm = odometerKm,
        amountCents = amountCents,
        workshop = workshop,
        nextDueDate = nextDueDate?.let { LocalDate.ofEpochDay(it) },
        nextDueKm = nextDueKm,
        notes = notes,
        createdAt = Instant.ofEpochMilli(createdAt)
    )

    private fun Maintenance.toEntity() = MaintenanceEntity(
        id = id,
        vehicleId = vehicleId,
        date = date.toEpochDay(),
        type = type.name,
        description = description,
        odometerKm = odometerKm,
        amountCents = amountCents,
        workshop = workshop,
        nextDueDate = nextDueDate?.toEpochDay(),
        nextDueKm = nextDueKm,
        notes = notes,
        createdAt = createdAt.toEpochMilli()
    )
}
