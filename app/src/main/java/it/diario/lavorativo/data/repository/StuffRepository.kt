package it.diario.lavorativo.data.repository

import it.diario.lavorativo.core.time.AppClock
import it.diario.lavorativo.data.local.dao.SiteDao
import it.diario.lavorativo.data.local.dao.StuffDao
import it.diario.lavorativo.data.local.entity.LoanEntity
import it.diario.lavorativo.data.local.entity.ToolEntity
import it.diario.lavorativo.data.mapper.toDomain
import it.diario.lavorativo.domain.model.Loan
import it.diario.lavorativo.domain.model.Site
import it.diario.lavorativo.domain.model.Tool
import it.diario.lavorativo.domain.model.ToolPlaceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/** Attrezzi del deposito virtuale e cose prestate. */
class StuffRepository(
    private val dao: StuffDao,
    private val siteDao: SiteDao,
    private val clock: AppClock
) {

    fun observeTools(sites: Flow<List<Site>>): Flow<List<Tool>> =
        combine(dao.observeTools(), sites) { tools, siteList ->
            val byId = siteList.associateBy { it.id }
            tools.map { t ->
                Tool(
                    id = t.id,
                    name = t.name,
                    placeType = ToolPlaceType.fromStorage(t.placeType),
                    site = t.siteId?.let { byId[it] },
                    placeDetail = t.placeDetail,
                    placeName = t.placeName,
                    notes = t.notes,
                    movedAt = Instant.ofEpochMilli(t.movedAt)
                )
            }
        }

    suspend fun saveTool(tool: Tool): Long {
        val now = clock.now().toEpochMilli()
        val e = ToolEntity(
            id = tool.id,
            name = tool.name.trim(),
            placeType = tool.placeType.name,
            siteId = if (tool.placeType == ToolPlaceType.CANTIERE) tool.site?.id else null,
            placeDetail = tool.placeDetail?.trim()?.ifBlank { null },
            placeName = if (tool.placeType == ToolPlaceType.ALTRO) tool.placeName?.trim()?.ifBlank { null } else null,
            notes = tool.notes?.trim()?.ifBlank { null },
            movedAt = now,
            createdAt = now
        )
        return if (tool.id == 0L) dao.insertTool(e) else {
            dao.updateTool(e)
            tool.id
        }
    }

    suspend fun deleteTool(id: Long) = dao.deleteTool(id)

    fun observeLoans(): Flow<List<Loan>> = dao.observeLoans().map { list -> list.map { it.toLoan() } }

    suspend fun openLoans(): List<Loan> = dao.openLoans().map { it.toLoan() }

    suspend fun saveLoan(loan: Loan): Long {
        val e = LoanEntity(
            id = loan.id,
            what = loan.what.trim(),
            toWhom = loan.toWhom.trim(),
            loanDate = loan.loanDate.toEpochDay(),
            returnedDate = loan.returnedDate?.toEpochDay(),
            notes = loan.notes?.trim()?.ifBlank { null },
            createdAt = clock.now().toEpochMilli()
        )
        return if (loan.id == 0L) dao.insertLoan(e) else {
            dao.updateLoan(e)
            loan.id
        }
    }

    suspend fun setReturned(id: Long, date: LocalDate?) = dao.setReturned(id, date?.toEpochDay())

    suspend fun deleteLoan(id: Long) = dao.deleteLoan(id)

    private fun LoanEntity.toLoan() = Loan(
        id = id,
        what = what,
        toWhom = toWhom,
        loanDate = LocalDate.ofEpochDay(loanDate),
        returnedDate = returnedDate?.let { LocalDate.ofEpochDay(it) },
        notes = notes
    )
}
