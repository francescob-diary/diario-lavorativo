package it.diario.lavorativo.ui.dictation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.diario.lavorativo.core.speech.ContinuousRecognizer
import it.diario.lavorativo.core.speech.Speaker
import it.diario.lavorativo.domain.model.DayType
import it.diario.lavorativo.domain.model.Site
import it.diario.lavorativo.ui.history.LONG_DATE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictationScreen(
    epochDay: Long,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    viewModel: DictationViewModel = viewModel(
        key = "dettatura-" + epochDay.toString(),
        factory = DictationViewModel.factory(epochDay)
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    // Due ascolti separati: uno per il racconto, uno per le risposte.
    val racconto = remember { ContinuousRecognizer(context) }
    val risposta = remember { ContinuousRecognizer(context) }
    val voce = remember { Speaker(context) }
    DisposableEffect(Unit) {
        onDispose {
            racconto.release()
            risposta.release()
            voce.release()
        }
    }

    // Si esce dall'app (tasto Home, altra app, schermo spento): il microfono
    // si spegne subito e la voce smette di parlare. Il testo gia' dettato
    // resta; per continuare basta ritoccare il microfono.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                racconto.stop()
                risposta.stop()
                voce.stop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val ascolto by racconto.state.collectAsStateWithLifecycle()
    val ascoltoRisposta by risposta.state.collectAsStateWithLifecycle()

    var permesso by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val chiediPermesso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        permesso = ok
        if (ok) {
            racconto.start(state.transcript)
        } else {
            android.widget.Toast.makeText(
                context,
                "Senza il permesso del microfono non posso ascoltarti. " +
                    "Puoi darlo da Impostazioni > App > Diario Lavorativo > Autorizzazioni.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    // Riserva: la finestrella di dettatura di Google, che ha i suoi permessi.
    // Ogni volta si dice una frase; il testo si aggiunge in fondo.
    var dettaturaPerRisposta by remember { mutableStateOf(false) }
    val microfonoGoogle = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val detto = result.data
            ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (detto.isNotEmpty()) {
            if (dettaturaPerRisposta) {
                risposta.append(detto)
            } else {
                racconto.append(detto)
                viewModel.setTranscript(racconto.state.value.text)
            }
        }
    }
    fun apriMicrofonoGoogle(perRisposta: Boolean) {
        dettaturaPerRisposta = perRisposta
        try {
            microfonoGoogle.launch(it.diario.lavorativo.ui.components.dictationIntent("Racconta"))
        } catch (e: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(
                context,
                "Su questo telefono manca il riconoscimento vocale di Google",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    // Il testo riconosciuto finisce nel racconto, man mano.
    LaunchedEffect(ascolto.text) {
        if (ascolto.text != state.transcript && ascolto.text.isNotEmpty()) {
            viewModel.setTranscript(ascolto.text)
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // Arriva una domanda: la si legge ad alta voce e poi si ascolta la risposta.
    LaunchedEffect(state.currentQuestion) {
        val q = state.currentQuestion ?: return@LaunchedEffect
        risposta.stop()
        risposta.setText("")
        voce.speak(q) {
            if (permesso) risposta.start("")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Racconta la giornata")
                        Text(
                            state.date.format(LONG_DATE),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        racconto.stop()
                        risposta.stop()
                        viewModel.cancelProcessing()
                        onBack()
                    }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Indietro") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            when (state.stage) {
                DictationStage.RACCONTO -> StoryStep(
                    error = ascolto.error,
                    onGoogleMic = {
                        racconto.clearError()
                        apriMicrofonoGoogle(perRisposta = false)
                    },
                    transcript = state.transcript,
                    partial = ascolto.partial,
                    listening = ascolto.listening,
                    hasModel = state.hasModel,
                    onMic = {
                        when {
                            ascolto.listening -> racconto.stop()
                            permesso -> racconto.start(state.transcript)
                            else -> chiediPermesso.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onEdit = {
                        racconto.setText(it)
                        viewModel.setTranscript(it)
                    },
                    onDone = {
                        racconto.stop()
                        // Una frase detta proprio all'ultimo non va persa.
                        val finale = racconto.state.value.text
                        if (finale.isNotBlank()) viewModel.setTranscript(finale)
                        viewModel.analyze()
                    },
                    onOpenSettings = onOpenSettings
                )

                DictationStage.ELABORAZIONE -> ProcessingStep(
                    fraction = state.progress?.fraction,
                    label = state.progress?.label ?: "Leggo quello che hai detto...",
                    onCancel = viewModel::cancelProcessing
                )

                DictationStage.DOMANDE -> QuestionStep(
                    question = state.currentQuestion.orEmpty(),
                    answer = ascoltoRisposta.text + (
                        if (ascoltoRisposta.partial.isBlank()) "" else " " + ascoltoRisposta.partial
                        ),
                    listening = ascoltoRisposta.listening,
                    error = ascoltoRisposta.error,
                    onGoogleMic = {
                        risposta.clearError()
                        apriMicrofonoGoogle(perRisposta = true)
                    },
                    onMic = {
                        when {
                            ascoltoRisposta.listening -> risposta.stop()
                            permesso -> risposta.start()
                            else -> chiediPermesso.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onEdit = { risposta.setText(it) },
                    onAnswer = {
                        risposta.stop()
                        viewModel.answer(risposta.state.value.text)
                    },
                    onSkip = {
                        risposta.stop()
                        voce.stop()
                        viewModel.skipQuestions()
                    }
                )

                DictationStage.CONFERMA -> ConfirmStep(
                    form = state.form,
                    sites = state.sites,
                    usedModel = state.usedModel,
                    timings = state.timings,
                    onChange = viewModel::updateForm,
                    onRedo = viewModel::backToStory,
                    onSave = viewModel::save
                )

                DictationStage.SALVATO -> SavedStep(onClose = onBack)
            }
        }
    }
}

// ------------------------------------------------------------ racconto

@Composable
private fun StoryStep(
    error: String?,
    onGoogleMic: () -> Unit,
    transcript: String,
    partial: String,
    listening: Boolean,
    hasModel: Boolean,
    onMic: () -> Unit,
    onEdit: (String) -> Unit,
    onDone: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Text(
        text = "Tocca il microfono e racconta com'e' andata, con calma: orari, " +
            "cantieri, cosa hai fatto, materiali, spostamenti. Quando hai finito " +
            "premi HO FINITO.",
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(Modifier.height(20.dp))

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Button(
            onClick = onMic,
            shape = CircleShape,
            colors = if (listening) {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            } else {
                ButtonDefaults.buttonColors()
            },
            modifier = Modifier.size(120.dp)
        ) {
            Icon(
                if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = if (listening) "Ferma" else "Parla",
                modifier = Modifier.size(56.dp)
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = if (listening) "Ti ascolto..." else "Tocca per parlare",
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
        color = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (error != null) {
        Spacer(Modifier.height(12.dp))
        MicProblemCard(error, onGoogleMic)
    } else {
        // Sempre a portata: se l'ascolto continuo fa i capricci si usa questo.
        TextButton(onClick = onGoogleMic, modifier = Modifier.fillMaxWidth()) {
            Text("Non va? Usa il microfono di Google")
        }
    }

    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = transcript,
        onValueChange = onEdit,
        label = { Text("Il tuo racconto (puoi correggerlo)") },
        minLines = 5,
        modifier = Modifier.fillMaxWidth()
    )
    if (partial.isNotBlank()) {
        Text(
            text = partial,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp)
        )
    }

    if (!hasModel) {
        Spacer(Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "Modello non ancora scelto: capisco solo le frasi semplici, " +
                        "tipo \"dalle 8 alle 17, pausa dalle 12 alle 13, in viale Sarca\".",
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = onOpenSettings) { Text("SCEGLI IL MODELLO") }
            }
        }
    }

    Spacer(Modifier.height(20.dp))
    Button(
        onClick = onDone,
        enabled = transcript.isNotBlank() || partial.isNotBlank(),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
    ) { Text("HO FINITO") }
}

// --------------------------------------------------------- elaborazione

@Composable
private fun ProcessingStep(fraction: Float?, label: String, onCancel: () -> Unit) {
    Spacer(Modifier.height(40.dp))
    Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    if (fraction == null) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(10.dp))
    } else {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(10.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            (fraction * 100).toInt().toString() + "%",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(
        "Ci puo' volere anche un minuto: il modello lavora tutto sul telefono, " +
            "senza internet. Non chiudere l'app.",
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(24.dp))
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("ANNULLA") }
}

// ------------------------------------------------------------- domande

@Composable
private fun QuestionStep(
    question: String,
    answer: String,
    listening: Boolean,
    error: String?,
    onGoogleMic: () -> Unit,
    onMic: () -> Unit,
    onEdit: (String) -> Unit,
    onAnswer: () -> Unit,
    onSkip: () -> Unit
) {
    Text("Mi manca un dato", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Card(Modifier.fillMaxWidth()) {
        Text(
            question,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp)
        )
    }
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = onMic,
            shape = CircleShape,
            colors = if (listening) {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            } else ButtonDefaults.buttonColors(),
            modifier = Modifier.size(72.dp)
        ) {
            Icon(if (listening) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = "Rispondi a voce")
        }
        Spacer(Modifier.size(12.dp))
        Text(if (listening) "Ti ascolto..." else "Tocca e rispondi")
    }
    if (error != null) {
        Spacer(Modifier.height(8.dp))
        MicProblemCard(error, onGoogleMic)
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = answer,
        onValueChange = onEdit,
        label = { Text("Risposta") },
        minLines = 2,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = onAnswer, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text("RISPONDI")
    }
    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
        Text("SALTA LE DOMANDE, CORREGGO A MANO")
    }
}

// ------------------------------------------------------------ conferma

@Composable
private fun ConfirmStep(
    form: DictationForm,
    sites: List<Site>,
    usedModel: Boolean,
    timings: String?,
    onChange: ((DictationForm) -> DictationForm) -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit
) {
    Text("Controlla e conferma", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(
        text = if (usedModel) "Compilato dal modello sul telefono. Correggi quello che non va."
        else "Compilato con le regole semplici. Correggi quello che non va.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (usedModel && timings != null) {
        Text(timings, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
    Spacer(Modifier.height(12.dp))

    Section("Tipo di giornata")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayType.entries.forEach { t ->
            FilterChip(
                selected = form.dayType == t,
                onClick = { onChange { it.copy(dayType = t) } },
                label = { Text(t.name.lowercase().replaceFirstChar { c -> c.uppercase() }) }
            )
        }
    }

    if (form.dayType == DayType.LAVORO) {
        Section("Orari")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeField("Ingresso", form.start, Modifier.weight(1f)) { v -> onChange { it.copy(start = v) } }
            TimeField("Uscita", form.end, Modifier.weight(1f)) { v -> onChange { it.copy(end = v) } }
        }

        Section("Pause")
        form.breaks.forEachIndexed { i, b ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeField("Dalle", b.start, Modifier.weight(1f)) { v ->
                    onChange { f -> f.copy(breaks = f.breaks.mapIndexed { j, x -> if (j == i) x.copy(start = v) else x }) }
                }
                TimeField("Alle", b.end, Modifier.weight(1f)) { v ->
                    onChange { f -> f.copy(breaks = f.breaks.mapIndexed { j, x -> if (j == i) x.copy(end = v) else x }) }
                }
                RemoveButton { onChange { f -> f.copy(breaks = f.breaks.filterIndexed { j, _ -> j != i }) } }
            }
        }
        AddButton("PAUSA") {
            onChange { f ->
                f.copy(breaks = f.breaks + if (f.breaks.isEmpty()) FormBreak("12:00", "13:00") else FormBreak())
            }
        }

        Section("Cantieri (il primo e' il principale)")
        form.sites.forEachIndexed { i, s ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = s.name,
                            onValueChange = { v ->
                                onChange { f ->
                                    f.copy(sites = f.sites.mapIndexed { j, x ->
                                        if (j == i) x.copy(name = v, siteId = sites.firstOrNull { it.name.equals(v.trim(), true) }?.id)
                                        else x
                                    })
                                }
                            },
                            label = { Text(if (s.siteId == null && s.name.isNotBlank()) "Cantiere (nuovo)" else "Cantiere") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        RemoveButton { onChange { f -> f.copy(sites = f.sites.filterIndexed { j, _ -> j != i }) } }
                    }
                    if (sites.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            sites.forEach { site ->
                                FilterChip(
                                    selected = s.siteId == site.id,
                                    onClick = {
                                        onChange { f ->
                                            f.copy(sites = f.sites.mapIndexed { j, x ->
                                                if (j == i) x.copy(siteId = site.id, name = site.name) else x
                                            })
                                        }
                                    },
                                    label = { Text(site.name) }
                                )
                            }
                        }
                    }
                    if (i > 0) {
                        OutlinedTextField(
                            value = s.hours,
                            onValueChange = { v ->
                                onChange { f -> f.copy(sites = f.sites.mapIndexed { j, x -> if (j == i) x.copy(hours = v) else x }) }
                            },
                            label = { Text("Ore fatte qui (es. 4 o 3:30)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = s.work,
                            onValueChange = { v ->
                                onChange { f -> f.copy(sites = f.sites.mapIndexed { j, x -> if (j == i) x.copy(work = v) else x }) }
                            },
                            label = { Text("Lavorazione qui") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (form.sites.size > 1) {
                        Text(
                            "Le ore del principale sono il resto della giornata.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        AddButton("CANTIERE") { onChange { f -> f.copy(sites = f.sites + FormSite()) } }
    }

    Section("Lavoro svolto")
    OutlinedTextField(
        value = form.description,
        onValueChange = { v -> onChange { it.copy(description = v) } },
        minLines = 2,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = form.materials,
        onValueChange = { v -> onChange { it.copy(materials = v) } },
        label = { Text("Materiali") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = form.place,
        onValueChange = { v -> onChange { it.copy(place = v) } },
        label = { Text("Luogo") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Section("Spostamenti (non vanno nel rapportino)")
    form.trips.forEachIndexed { i, t ->
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = t.from,
                        onValueChange = { v -> onChange { f -> f.copy(trips = f.trips.mapIndexed { j, x -> if (j == i) x.copy(from = v) else x }) } },
                        label = { Text("Da") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = t.to,
                        onValueChange = { v -> onChange { f -> f.copy(trips = f.trips.mapIndexed { j, x -> if (j == i) x.copy(to = v) else x }) } },
                        label = { Text("A") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                    RemoveButton { onChange { f -> f.copy(trips = f.trips.filterIndexed { j, _ -> j != i }) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeField("Partenza", t.depart, Modifier.weight(1f)) { v ->
                        onChange { f -> f.copy(trips = f.trips.mapIndexed { j, x -> if (j == i) x.copy(depart = v) else x }) }
                    }
                    TimeField("Arrivo", t.arrive, Modifier.weight(1f)) { v ->
                        onChange { f -> f.copy(trips = f.trips.mapIndexed { j, x -> if (j == i) x.copy(arrive = v) else x }) }
                    }
                }
            }
        }
    }
    AddButton("SPOSTAMENTO") { onChange { f -> f.copy(trips = f.trips + FormTrip()) } }

    Section("Altro")
    OutlinedTextField(
        value = form.km,
        onValueChange = { v -> onChange { it.copy(km = v) } },
        label = { Text("Chilometri col mezzo") },
        singleLine = true,
        isError = form.km.isNotBlank() && form.km.trim().toIntOrNull() == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = form.notes,
        onValueChange = { v -> onChange { it.copy(notes = v) } },
        label = { Text("Note") },
        minLines = 2,
        modifier = Modifier.fillMaxWidth()
    )

    if (form.errors.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Da sistemare: " + form.errors.joinToString(", "),
            color = MaterialTheme.colorScheme.error
        )
    }

    Spacer(Modifier.height(20.dp))
    Button(
        onClick = onSave,
        enabled = form.errors.isEmpty(),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
    ) { Text("SALVA NEL DIARIO") }
    TextButton(onClick = onRedo, modifier = Modifier.fillMaxWidth()) {
        Text("TORNA AL RACCONTO")
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun SavedStep(onClose: () -> Unit) {
    Spacer(Modifier.height(40.dp))
    Text(
        "Giornata salvata nel diario.",
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("CHIUDI") }
}

// ------------------------------------------------------------ pezzetti

/** Il microfono continuo non va: si spiega perche' e si offre la riserva. */
@Composable
private fun MicProblemCard(message: String, onGoogleMic: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onGoogleMic, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Filled.Mic, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("USA IL MICROFONO DI GOOGLE")
            }
            Text(
                "Si apre la finestrella di Google: di' una parte del racconto, " +
                    "poi ripremi per continuare. Il testo si aggiunge in fondo.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun TimeField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text("--:--") },
        singleLine = true,
        isError = value.isNotBlank() &&
            it.diario.lavorativo.domain.service.TimeTextParser.parseClock(value) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
private fun RemoveButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.Filled.Close, contentDescription = "Togli") }
}

@Composable
private fun AddButton(what: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.size(6.dp))
        Text("AGGIUNGI $what")
    }
}
