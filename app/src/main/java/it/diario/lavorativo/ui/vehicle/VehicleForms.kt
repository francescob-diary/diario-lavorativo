package it.diario.lavorativo.ui.vehicle

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.unit.dp
import it.diario.lavorativo.core.util.MoneyFormat
import it.diario.lavorativo.domain.model.ExpenseType
import it.diario.lavorativo.domain.model.FuelStop
import it.diario.lavorativo.domain.model.Maintenance
import it.diario.lavorativo.domain.model.MaintenanceType
import it.diario.lavorativo.domain.model.Vehicle
import it.diario.lavorativo.domain.model.VehicleExpense
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * I moduli di inserimento del mezzo.
 *
 * Stanno in un file a parte perche' la schermata era gia' lunga e questi
 * sono quattro dialoghi che non parlano fra loro.
 *
 * Regola comune: la data si scrive a mano nel formato gg/mm/aaaa e parte
 * gia' compilata con oggi. Un calendario a comparsa sarebbe piu' bello, ma
 * il novanta per cento delle volte la data giusta e' quella di oggi, e per
 * il resto scrivere sei cifre e' piu' veloce che cercare il giorno in una
 * griglia con i guanti addosso.
 */

private val DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALIAN)

/** Legge una data scritta a mano; null se non si capisce. */
internal fun parseDate(text: String): LocalDate? = try {
    LocalDate.parse(text.trim(), DATE_FORMAT)
} catch (e: Exception) {
    null
}

internal fun formatDate(date: LocalDate): String = date.format(DATE_FORMAT)

@Composable
private fun DateField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("Data (gg/mm/aaaa)") },
        singleLine = true,
        isError = parseDate(value) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

// --------------------------------------------------------------- mezzo

@Composable
fun VehicleDialog(
    existing: Vehicle?,
    onDismiss: () -> Unit,
    onSave: (name: String, plate: String?, active: Boolean) -> Unit
) {
    var nome by remember { mutableStateOf(existing?.name.orEmpty()) }
    var targa by remember { mutableStateOf(existing?.plate.orEmpty()) }
    var attivo by remember { mutableStateOf(existing?.active ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nuovo mezzo" else "Modifica mezzo") },
        text = {
            Column {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Modello") },
                    placeholder = { Text("Fiat Doblo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = targa,
                    onValueChange = { targa = it.uppercase() },
                    label = { Text("Targa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = attivo, onCheckedChange = { attivo = it })
                    Spacer(Modifier.fillMaxWidth(0.06f))
                    Column {
                        Text("Mezzo in uso adesso")
                        Text(
                            "Spegnendolo resta nello storico con i suoi dati",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(nome, targa.takeIf { it.isNotBlank() }, attivo) },
                enabled = nome.isNotBlank()
            ) { Text("SALVA") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ANNULLA") } }
    )
}

// -------------------------------------------------------- rifornimento

@Composable
fun FuelStopDialog(
    existing: FuelStop?,
    today: LocalDate,
    lastOdometerKm: Int?,
    onDismiss: () -> Unit,
    onSave: (
        date: LocalDate,
        liters: Double,
        amountCents: Long,
        odometerKm: Int?,
        fullTank: Boolean,
        station: String?
    ) -> Unit
) {
    var data by remember { mutableStateOf(formatDate(existing?.date ?: today)) }
    var litri by remember {
        mutableStateOf(existing?.let { MoneyFormat.formatDecimal(it.liters, 2) }.orEmpty())
    }
    var importo by remember {
        mutableStateOf(existing?.let { MoneyFormat.formatCents(it.amountCents) }.orEmpty())
    }
    var km by remember { mutableStateOf(existing?.odometerKm?.toString().orEmpty()) }
    var pieno by remember { mutableStateOf(existing?.fullTank ?: true) }
    var distributore by remember { mutableStateOf(existing?.station.orEmpty()) }

    val litriValidi = MoneyFormat.parseDecimal(litri)
    val importoValido = MoneyFormat.parseCents(importo)
    val dataValida = parseDate(data)

    // Prezzo al litro calcolato mentre si scrive: e' il controllo piu'
    // veloce che ci sia. Se esce 17 euro al litro, una cifra e' storta.
    val prezzo = if (litriValidi != null && litriValidi > 0.0 && importoValido != null) {
        MoneyFormat.formatDecimal(importoValido / 100.0 / litriValidi, 3) + " al litro"
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Rifornimento" else "Modifica rifornimento") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                DateField(data) { data = it }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = litri,
                    onValueChange = { litri = it },
                    label = { Text("Litri") },
                    singleLine = true,
                    isError = litri.isNotBlank() && litriValidi == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = importo,
                    onValueChange = { importo = it },
                    label = { Text("Importo in euro") },
                    singleLine = true,
                    isError = importo.isNotBlank() && importoValido == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (prezzo != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        prezzo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = km,
                    onValueChange = { km = it },
                    label = { Text("Contachilometri") },
                    placeholder = {
                        Text(lastOdometerKm?.let { "ultimo: " + it } ?: "senza, niente consumo")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = pieno, onCheckedChange = { pieno = it })
                    Spacer(Modifier.fillMaxWidth(0.06f))
                    Column {
                        Text(if (pieno) "Pieno" else "Rabbocco")
                        Text(
                            "Il consumo si misura solo da pieno a pieno",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = distributore,
                    onValueChange = { distributore = it },
                    label = { Text("Distributore") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val d = dataValida
                    val l = litriValidi
                    val i = importoValido
                    if (d != null && l != null && i != null) {
                        onSave(
                            d, l, i,
                            MoneyFormat.parseInt(km),
                            pieno,
                            distributore.takeIf { it.isNotBlank() }
                        )
                    }
                },
                enabled = dataValida != null && litriValidi != null &&
                    litriValidi > 0.0 && importoValido != null && importoValido > 0L
            ) { Text("SALVA") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ANNULLA") } }
    )
}

// ---------------------------------------------------- pedaggi e spese

@Composable
fun ExpenseDialog(
    existing: VehicleExpense?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (
        date: LocalDate,
        type: ExpenseType,
        amountCents: Long,
        place: String?
    ) -> Unit
) {
    var data by remember { mutableStateOf(formatDate(existing?.date ?: today)) }
    var tipo by remember { mutableStateOf(existing?.type ?: ExpenseType.PEDAGGIO) }
    var importo by remember {
        mutableStateOf(existing?.let { MoneyFormat.formatCents(it.amountCents) }.orEmpty())
    }
    var luogo by remember { mutableStateOf(existing?.place.orEmpty()) }

    val importoValido = MoneyFormat.parseCents(importo)
    val dataValida = parseDate(data)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Spesa" else "Modifica spesa") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    ExpenseType.entries.forEach { t ->
                        FilterChip(
                            selected = tipo == t,
                            onClick = { tipo = t },
                            label = { Text(t.label) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                DateField(data) { data = it }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = importo,
                    onValueChange = { importo = it },
                    label = { Text("Importo in euro") },
                    singleLine = true,
                    isError = importo.isNotBlank() && importoValido == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = luogo,
                    onValueChange = { luogo = it },
                    label = { Text("Dove") },
                    placeholder = { Text("A4 Brescia est") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val d = dataValida
                    val i = importoValido
                    if (d != null && i != null) {
                        onSave(d, tipo, i, luogo.takeIf { it.isNotBlank() })
                    }
                },
                enabled = dataValida != null && importoValido != null && importoValido > 0L
            ) { Text("SALVA") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ANNULLA") } }
    )
}

// ------------------------------------------------------------ officina

@Composable
fun MaintenanceDialog(
    existing: Maintenance?,
    today: LocalDate,
    lastOdometerKm: Int?,
    suggestNextKm: (MaintenanceType, Int?) -> Int?,
    onDismiss: () -> Unit,
    onSave: (
        date: LocalDate,
        type: MaintenanceType,
        description: String?,
        odometerKm: Int?,
        amountCents: Long?,
        workshop: String?,
        nextDueDate: LocalDate?,
        nextDueKm: Int?
    ) -> Unit
) {
    var data by remember { mutableStateOf(formatDate(existing?.date ?: today)) }
    var tipo by remember { mutableStateOf(existing?.type ?: MaintenanceType.TAGLIANDO) }
    var descrizione by remember { mutableStateOf(existing?.description.orEmpty()) }
    var km by remember {
        mutableStateOf(
            existing?.odometerKm?.toString() ?: lastOdometerKm?.toString().orEmpty()
        )
    }
    var costo by remember {
        mutableStateOf(existing?.amountCents?.let { MoneyFormat.formatCents(it) }.orEmpty())
    }
    var officina by remember { mutableStateOf(existing?.workshop.orEmpty()) }
    var prossimaData by remember {
        mutableStateOf(existing?.nextDueDate?.let { formatDate(it) }.orEmpty())
    }
    var prossimiKm by remember { mutableStateOf(existing?.nextDueKm?.toString().orEmpty()) }

    val dataValida = parseDate(data)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Intervento" else "Modifica intervento") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    MaintenanceType.entries.forEach { t ->
                        FilterChip(
                            selected = tipo == t,
                            onClick = {
                                tipo = t
                                // Proposta di chilometri per la prossima volta,
                                // solo se il campo e' ancora vuoto: quello che
                                // ha scritto l'utente non si tocca mai.
                                if (prossimiKm.isBlank()) {
                                    val proposta = suggestNextKm(t, MoneyFormat.parseInt(km))
                                    if (proposta != null) prossimiKm = proposta.toString()
                                }
                            },
                            label = { Text(t.label) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                DateField(data) { data = it }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = km,
                    onValueChange = { km = it },
                    label = { Text("Contachilometri") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = descrizione,
                    onValueChange = { descrizione = it },
                    label = { Text("Cosa e' stato fatto") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = costo,
                    onValueChange = { costo = it },
                    label = { Text("Costo in euro") },
                    placeholder = { Text("vuoto se in garanzia") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = officina,
                    onValueChange = { officina = it },
                    label = { Text("Officina") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Quando tornare",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "Compila solo quello che ti hanno detto. Quello che lasci " +
                        "vuoto non diventa un avviso.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = prossimaData,
                    onValueChange = { prossimaData = it },
                    label = { Text("Prossima scadenza (gg/mm/aaaa)") },
                    singleLine = true,
                    isError = prossimaData.isNotBlank() && parseDate(prossimaData) == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = prossimiKm,
                    onValueChange = { prossimiKm = it },
                    label = { Text("Prossimi chilometri") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val d = dataValida ?: return@TextButton
                    onSave(
                        d,
                        tipo,
                        descrizione.takeIf { it.isNotBlank() },
                        MoneyFormat.parseInt(km),
                        MoneyFormat.parseCents(costo),
                        officina.takeIf { it.isNotBlank() },
                        parseDate(prossimaData),
                        MoneyFormat.parseInt(prossimiKm)
                    )
                },
                enabled = dataValida != null
            ) { Text("SALVA") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ANNULLA") } }
    )
}
