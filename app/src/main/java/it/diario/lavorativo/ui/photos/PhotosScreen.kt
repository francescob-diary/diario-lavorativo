package it.diario.lavorativo.ui.photos

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.diario.lavorativo.core.di.appContainer
import it.diario.lavorativo.domain.model.Photo
import it.diario.lavorativo.ui.history.LONG_DATE
import kotlinx.coroutines.launch

/**
 * Foto di una giornata.
 *
 * Due soli modi per aggiungerle: scatto e galleria. Nessun permesso di
 * archiviazione: il selettore di sistema restituisce l'immagine scelta senza
 * dare all'app accesso a tutte le altre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    epochDay: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PhotosViewModel = viewModel(
        key = "foto-" + epochDay.toString(),
        factory = PhotosViewModel.factory(epochDay)
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var pendingCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var needsLegacyPermission by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        pendingCameraUri?.let { viewModel.onCameraResult(it, success) }
        pendingCameraUri = null
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.onGalleryPicked(uri)
    }

    val scope = rememberCoroutineScope()

    // Su Android 9 e precedenti serve il permesso per scrivere in galleria.
    val legacyStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                viewModel.prepareCameraTarget()?.let { uri ->
                    pendingCameraUri = uri
                    cameraLauncher.launch(uri)
                }
            }
        }
    }

    fun scatta() {
        scope.launch {
            val uri = viewModel.prepareCameraTarget()
            if (uri == null) {
                needsLegacyPermission = true
                return@launch
            }
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    LaunchedEffect(needsLegacyPermission) {
        if (needsLegacyPermission) {
            snackbarHostState.showSnackbar(
                "Non riesco a scrivere in galleria. Controlla i permessi dell'app."
            )
            needsLegacyPermission = false
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.date.format(LONG_DATE)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            legacyStorageLauncher.launch(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            )
                        } else {
                            scatta()
                        }
                    },
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text("  SCATTA")
                }
                OutlinedButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Text("  GALLERIA")
                }
            }

            Spacer(Modifier.height(16.dp))

            when {
                state.importing -> Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                state.isEmpty -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nessuna foto per questa giornata.\n\n" +
                            "Le foto scattate finiscono in galleria, " +
                            "nell'album Diario Lavorativo, e l'app ne tiene " +
                            "una copia ridotta.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.photos, key = { it.id }) { photo ->
                        PhotoThumbnail(
                            photo = photo,
                            onClick = { viewModel.openPhoto(photo) }
                        )
                    }
                }
            }
        }
    }

    state.openPhoto?.let { photo ->
        PhotoDetailDialog(
            photo = photo,
            activities = state.activities,
            events = state.events,
            onCaption = { viewModel.setCaption(photo.id, it) },
            onLinkActivity = { viewModel.linkToActivity(photo.id, it) },
            onLinkEvent = { viewModel.linkToEvent(photo.id, it) },
            onDelete = { viewModel.deletePhoto(photo) },
            onDismiss = viewModel::closePhoto
        )
    }
}

/**
 * Miniatura. L'immagine viene decodifica in un thread di sfondo tramite
 * produceState: caricare i file nel thread grafico farebbe scattare la
 * griglia a ogni scorrimento.
 */
@Composable
fun PhotoThumbnail(photo: Photo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val storage = remember(context) { appContainer(context).photoStorage }
    val bitmap by produceState<Bitmap?>(initialValue = null, photo.fileName) {
        value = storage.load(photo.fileName, targetPx = 400)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val current = bitmap
        if (current == null) {
            CircularProgressIndicator(modifier = Modifier.height(24.dp))
        } else {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = photo.caption ?: "Foto di cantiere",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (photo.hasCaption) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            ) {
                Text(
                    text = photo.caption.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    maxLines = 1,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
    }
}
