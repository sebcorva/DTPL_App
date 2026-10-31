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
import cl.logisticaverde.app.domain.sync.RegistroErrores
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import cl.logisticaverde.app.R
import cl.logisticaverde.app.data.local.entity.HerramientaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.HerramientaJornadaEntity
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Pantalla 4:
 * 1. Arriba: Visor de cámara de escáner QR siempre visible.
 * 2. Debajo del visor QR: Input desplegable (ExposedDropdownMenuBox) + botón Agregar.
 * 3. Casilla "Esta brigada no lleva herramientas" (ISSUE-34): permite avanzar sin herramientas y salta P7.
 * 4. Abajo: Lista de herramientas agregadas y botón Siguiente.
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

    // Herramientas disponibles en el dropdown (excluye las ya agregadas a la jornada actual)
    val codigosAgregados = remember(herramientasAgregadas) {
        herramientasAgregadas.map { it.qrCodigo }.toSet()
    }
    val opcionesDropdown = remember(catalogoHerramientas, codigosAgregados) {
        catalogoHerramientas.filter { it.qrCodigo !in codigosAgregados }
    }

    var dropdownExpanded by remember { mutableStateOf(false) }
    var selectedHerramientaDropdown by remember(opcionesDropdown) {
        mutableStateOf(opcionesDropdown.firstOrNull())
    }

    val canProceed = herramientasAgregadas.isNotEmpty() || uiState.sinHerramientas

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Herramientas de la Brigada",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleSinHerramientas(!uiState.sinHerramientas) }
            ) {
                Checkbox(
                    checked = uiState.sinHerramientas,
                    onCheckedChange = onToggleSinHerramientas
                )
                Text(
                    text = "Esta brigada no lleva herramientas",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            // 1. ARRIBA: Visor del Escáner QR siempre visible
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Escanear QR de la herramienta",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.sinHerramientas) {
                            Text(
                                text = "La brigada no lleva herramientas: no es necesario escanear.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (hasCameraPermission) {
                            QrCameraPreview(
                                onQrCodeScanned = { code ->
                                    onQrDetected(code)
                                }
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Se requiere permiso de cámara para el visor QR.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
                                ) {
                                    Text("Conceder permiso de cámara")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. DEBAJO DEL VISOR QR: Input Desplegable de Selección Manual + Botón Agregar
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "O seleccionarla de la lista",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = {
                                if (opcionesDropdown.isNotEmpty() && !uiState.sinHerramientas) {
                                    dropdownExpanded = !dropdownExpanded
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            val displayValue = selectedHerramientaDropdown?.let {
                                nombreConEstado(it.nombreHerramienta, it.estado)
                            } ?: if (opcionesDropdown.isEmpty()) {
                                "Todas las herramientas disponibles fueron agregadas"
                            } else {
                                "Selecciona una herramienta..."
                            }

                            OutlinedTextField(
                                value = displayValue,
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                                },
                                textStyle = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                opcionesDropdown.forEach { item ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(nombreConEstado(item.nombreHerramienta, item.estado))
                                        },
                                        onClick = {
                                            selectedHerramientaDropdown = item
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                selectedHerramientaDropdown?.let { item ->
                                    onQrDetected(item.qrCodigo)
                                }
                            },
                            enabled = selectedHerramientaDropdown != null && opcionesDropdown.isNotEmpty() &&
                                !uiState.sinHerramientas,
                            modifier = Modifier.height(54.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Agregar")
                            Text(text = "Agregar", modifier = Modifier.padding(start = 4.dp))
                        }
                    }

                    if (!uiState.qrFeedbackMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = uiState.qrFeedbackMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. ABAJO: Lista de Herramientas Agregadas
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Herramientas Agregadas (${herramientasAgregadas.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider()

                    if (herramientasAgregadas.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (uiState.sinHerramientas) {
                                    "Sin herramientas para esta jornada."
                                } else {
                                    "Escanea un QR arriba o selecciona una herramienta de la lista."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(herramientasAgregadas, key = { it.id }) { herramienta ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = herramienta.nombreHerramienta,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "Estado: ${herramienta.estadoFinal}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (herramienta.estadoFinal.equals(HerramientaCatalogoEntity.ESTADO_DANADO, ignoreCase = true)) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                    IconButton(
                                        onClick = { onEliminarHerramienta(herramienta.id) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Quitar herramienta",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Botón Siguiente
        Button(
            onClick = onSiguienteClick,
            enabled = canProceed,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text(
                text = stringResource(id = R.string.btn_next),
                style = MaterialTheme.typography.labelLarge
            )
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
            val previewView = PreviewView(ctx)
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
