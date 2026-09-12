package it.diario.lavorativo.domain.repository

import it.diario.lavorativo.domain.model.FuelStop
import it.diario.lavorativo.domain.model.Maintenance
import it.diario.lavorativo.domain.model.Vehicle
import it.diario.lavorativo.domain.model.VehicleExpense
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * L'accesso ai dati del mezzo aziendale.
 *
 * Un'interfaccia sola per tutte e quattro le tabelle: mezzo, rifornimenti,
 * spese e interventi si guardano sempre insieme, e spezzarli in quattro
 * repository vorrebbe dire quattro oggetti da mettere d'accordo per fare
 * un totale.
 */
interface VehicleRepository {

    // ---- mezzi ----

    fun observeVehicles(): Flow<List<Vehicle>>

    suspend fun vehicleById(id: Long): Vehicle?

    /** Il mezzo in uso adesso, se ce n'e' uno. */
    suspend fun activeVehicle(): Vehicle?

    suspend fun saveVehicle(vehicle: Vehicle): Long

    suspend fun deleteVehicle(vehicle: Vehicle)

    suspend fun vehicleCount(): Int

    // ---- rifornimenti ----

    fun observeFuelStops(vehicleId: Long): Flow<List<FuelStop>>

    suspend fun fuelStops(vehicleId: Long, from: LocalDate, to: LocalDate): List<FuelStop>

    suspend fun saveFuelStop(stop: FuelStop): Long

    suspend fun deleteFuelStop(stop: FuelStop)

    // ---- spese ----

    fun observeExpenses(vehicleId: Long): Flow<List<VehicleExpense>>

    suspend fun expenses(vehicleId: Long, from: LocalDate, to: LocalDate): List<VehicleExpense>

    suspend fun saveExpense(expense: VehicleExpense): Long

    suspend fun deleteExpense(expense: VehicleExpense)

    // ---- interventi ----

    fun observeMaintenances(vehicleId: Long): Flow<List<Maintenance>>

    /** Tutti, non solo quelli del periodo: le scadenze guardano indietro. */
    suspend fun allMaintenances(vehicleId: Long): List<Maintenance>

    suspend fun maintenances(vehicleId: Long, from: LocalDate, to: LocalDate): List<Maintenance>

    suspend fun saveMaintenance(maintenance: Maintenance): Long

    suspend fun deleteMaintenance(maintenance: Maintenance)
}
