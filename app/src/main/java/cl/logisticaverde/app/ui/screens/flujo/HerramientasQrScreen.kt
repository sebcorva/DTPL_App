package cl.logisticaverde.app.ui.screens.flujo

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import cl.logisticaverde.app.data.local.entity.HerramientaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.HerramientaJornadaEntity
import cl.logisticaverde.app.domain.sync.RegistroErrores
import cl.logisticaverde.app.ui.theme.AzulMarino
import cl.logisticaverde.app.ui.theme.AzulMarinoSuave
import cl.logisticaverde.app.ui.theme.Blanco
import cl.logisticaverde.app.ui.theme.BordeSuave
import cl.logisticaverde.app.ui.theme.ErrorRojo
import cl.logisticaverde.app.ui.theme.SyncGrisFondo
import cl.logisticaverde.app.ui.theme.SyncGrisIcono
import cl.logisticaverde.app.ui.theme.VerdeAccion
import cl.logisticaverde.app.ui.theme.VerdeAccionOscuro
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

/** Duración del aviso verde "Agregada: …" en la P4 (decisión del 07-11-2026). */
private const val DURACION_AVISO_AGREGADA_MS = 2_000L

/** El aviso se desvanece suavemente y luego se cierra el espacio (ajuste del 07-11-2026). */
private const val DURACION_DESVANECER_MS = 600

/**
 * Pantalla 4 "Herramientas de la brigada" (diseño ISSUE-48, mismo formato que la P2 y la P3):
 * - Tarjeta 1: visor del escáner QR con esquinas, desplegable "Elige una herramienta" con botón
 *   redondo [+], mensaje del escaneo (verde/rojo) y casilla "Esta brigada no lleva herramientas"
 *   (ISSUE-34: permite avanzar sin herramientas y salta P7).
 * - Tarjeta 2: herramientas agregadas con globo de cantidad; cada fila con basurero y etiqueta "Dañado" si corresponde.
 * - Botón Siguiente.
 * Los códigos QR nunca se muestran: solo el nombre y número de la herramienta ("Pinza 1").
 */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HerramientasQrScreen(
    uiState: EquipoSeleccionUiState,
    herramientasAgregadas: List<HerramientaJornadaEntity>,
    catalogoHerramientas: List<HerramientaCatalogoEntity>,
    onQrDetected: (String) -> Unit,
    onEliminarHerramienta: (Int) -> Unit,
    onToggleSinHerramientas: (Boolean) -> Unit,
    onSiguienteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Herramientas disponibles en el desplegable (excluye las ya agregadas a la jornada actual)
    val codigosAgregados = remember(herramientasAgregadas) {
        herramientasAgregadas.map { it.qrCodigo }.toSet()
    }
    val opcionesDropdown = remember(catalogoHerramientas, codigosAgregados) {
        catalogoHerramientas.filter { it.qrCodigo !in codigosAgregados }
    }

    var dropdownExpanded by remember { mutableStateOf(false) }
    // Sin preselección: vuelve al placeholder al agregar o si cambian las opciones (decisión 5)
    var seleccionada by remember(opcionesDropdown) { mutableStateOf<HerramientaCatalogoEntity?>(null) }
    val desplegableActivo = !uiState.sinHerramientas && opcionesDropdown.isNotEmpty()

    val canProceed = herramientasAgregadas.isNotEmpty() || uiState.sinHerramientas

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        TituloPantallaFlujo("Herramientas de la brigada")
        Spacer(Modifier.height(12.dp))

        // TARJETA 1: escáner, desplegable y "no lleva herramientas"
        TarjetaFlujo(modifier = Modifier.weight(0.6f)) {
            SubtituloFlujo("Escanear herramienta")
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when {
                    uiState.sinHerramientas -> FondoVacioFlujo(Icons.Outlined.Handyman, "Sin herramientas")
                    hasCameraPermission -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(AzulMarino)
                            .esquinasVisor(Blanco)
                    ) {
                        QrCameraPreview(onQrCodeScanned = onQrDetected)
                    }
                    else -> FondoVacioFlujo(Icons.Filled.CameraAlt, "Se necesita la cámara") {
                        BotonPermitirCamara(
                            onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { if (desplegableActivo) dropdownExpanded = !dropdownExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = seleccionada?.let { nombreConEstado(it.nombreHerramienta, it.estado) }.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        enabled = desplegableActivo,
                        placeholder = {
                            Text(
                                if (opcionesDropdown.isEmpty() && !uiState.sinHerramientas) {
                                    "Todas fueron agregadas"
                                } else {
                                    "Elige una herramienta"
                                }
                            )
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        shape = FormaCampoFlujo,
                        colors = coloresCampoFlujo(),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = desplegableActivo)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        opcionesDropdown.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(nombreConEstado(item.nombreHerramienta, item.estado)) },
                                onClick = {
                                    seleccionada = item
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                FilledIconButton(
                    onClick = {
                        seleccionada?.let { onQrDetected(it.qrCodigo) }
                        seleccionada = null
                    },
                    enabled = seleccionada != null && desplegableActivo,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = VerdeAccion,
                        contentColor = Blanco,
                        disabledContainerColor = SyncGrisFondo,
                        disabledContentColor = SyncGrisIcono
                    ),
                    modifier = Modifier.size(58.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Agregar herramienta", modifier = Modifier.size(32.dp))
                }
            }
            // "Agregada" se oculta a los 2 s; los errores quedan hasta el siguiente intento
            var mensajeVisible by remember { mutableStateOf(false) }
            LaunchedEffect(uiState.qrFeedbackSeq) {
                mensajeVisible = uiState.qrFeedbackSeq > 0
                if (mensajeVisible && !uiState.qrFeedbackEsError) {
                    delay(DURACION_AVISO_AGREGADA_MS)
                    mensajeVisible = false
                }
            }
            // Último texto mostrado: se conserva mientras el aviso se desvanece
            var ultimoMensaje by remember { mutableStateOf("") }
            uiState.qrFeedbackMessage?.takeIf { it.isNotBlank() }?.let { ultimoMensaje = it }
            AnimatedVisibility(
                visible = mensajeVisible && !uiState.qrFeedbackMessage.isNullOrBlank(),
                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                exit = fadeOut(tween(DURACION_DESVANECER_MS)) +
                    shrinkVertically(tween(DURACION_DESVANECER_MS, delayMillis = DURACION_DESVANECER_MS / 2))
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    MensajeFlujo(texto = ultimoMensaje, esError = uiState.qrFeedbackEsError)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleSinHerramientas(!uiState.sinHerramientas) }
            ) {
                Checkbox(
                    checked = uiState.sinHerramientas,
                    onCheckedChange = onToggleSinHerramientas,
                    colors = coloresCheckboxFlujo()
                )
                Text(
                    text = "Esta brigada no lleva herramientas",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AzulMarino
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // TARJETA 2: herramientas agregadas
        TarjetaFlujo(modifier = Modifier.weight(0.4f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SubtituloFlujo("Herramientas agregadas", modifier = Modifier.weight(1f))
                val cantidad = herramientasAgregadas.size
                BadgedBox(
                    badge = {
                        Badge(containerColor = VerdeAccionOscuro, contentColor = Blanco) {
                            Text(text = cantidad.toString(), fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier.semantics {
                        contentDescription = if (cantidad == 1) "1 herramienta" else "$cantidad herramientas"
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Handyman,
                        contentDescription = null,
                        tint = AzulMarino,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = BordeSuave)
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (herramientasAgregadas.isEmpty()) {
                    item {
                        Text(
                            text = if (uiState.sinHerramientas) "Sin herramientas para esta jornada" else "Aún no hay herramientas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AzulMarinoSuave,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        )
                    }
                }
                items(herramientasAgregadas, key = { it.id }) { herramienta ->
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp)
                        ) {
                            // Nombre + etiqueta ocupan todo el espacio libre; el basurero queda pegado a la derecha
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Text(
                                    text = herramienta.nombreHerramienta,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = AzulMarino,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (herramienta.estadoFinal.equals(HerramientaCatalogoEntity.ESTADO_DANADO, ignoreCase = true)) {
                                    Spacer(Modifier.width(8.dp))
                                    EtiquetaDanado()
                                }
                            }
                            IconButton(onClick = { onEliminarHerramienta(herramienta.id) }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Quitar ${herramienta.nombreHerramienta}",
                                    tint = AzulMarinoSuave
                                )
                            }
                        }
                        HorizontalDivider(color = BordeSuave)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        BotonSiguienteFlujo(enabled = canProceed, onClick = onSiguienteClick)
    }
}

/** Etiqueta roja pequeña para herramientas dañadas (decisión 7). */
@Composable
private fun EtiquetaDanado() {
    Surface(shape = CircleShape, color = ErrorRojo.copy(alpha = 0.12f), contentColor = ErrorRojo) {
        Text(
            text = HerramientaCatalogoEntity.ESTADO_DANADO,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
        )
    }
}

/** Botón "Permitir cámara" con el estilo del flujo (decisión 8). */
@Composable
private fun BotonPermitirCamara(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = VerdeAccion,
        contentColor = Blanco,
        shadowElevation = 4.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("Permitir cámara", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** "Pinza 1", o "Pinza 1 (Dañado)" si no está operativa. Nunca el código QR. */
private fun nombreConEstado(nombre: String, estado: String): String =
    if (estado.equals(HerramientaCatalogoEntity.ESTADO_OPERATIVO, ignoreCase = true)) nombre else "$nombre ($estado)"

@OptIn(ExperimentalGetImage::class)
@Composable
private fun QrCameraPreview(
    onQrCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var lastScanTimestamp by remember { mutableLongStateOf(0L) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            // TextureView (COMPATIBLE) para que el visor respete las esquinas redondeadas de la tarjeta
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
                val scanner = BarcodeScanning.getClient(options)

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                val now = System.currentTimeMillis()
                                if (barcodes.isNotEmpty() && now - lastScanTimestamp > 1500L) {
                                    val rawValue = barcodes.first().rawValue
                                    if (!rawValue.isNullOrBlank()) {
                                        lastScanTimestamp = now
                                        onQrCodeScanned(rawValue)
                                    }
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    RegistroErrores.advertencia("EscanerQR", "No se pudo abrir la cámara del escáner", e)
                }
            }, ContextCompat.getMainExecutor(context))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}
