package cl.logisticaverde.app.ui.screens.flujo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cl.logisticaverde.app.R
import cl.logisticaverde.app.data.local.entity.SectorCatalogoEntity
import cl.logisticaverde.app.domain.util.ImageCompressor
import java.io.File

/**
 * Pantalla 3:
 * - Seleccionar sector(es) a limpiar (permite seleccionar más de uno y calcula m² internamente).
 * - Tomar Foto de Sector Inicio (sucio), comprimida a 720p, o marcar "Continuar sin fotografía (omitir)".
 * - Botón Siguiente.
 */
@Composable
fun SectoresFotoInicioScreen(
    uiState: EquipoSeleccionUiState,
    sectores: List<SectorCatalogoEntity>,
    onToggleSector: (String) -> Unit,
    onFotoCapturada: (File) -> Unit,
    onToggleOmitirFoto: (Boolean) -> Unit,
    onMotivoOmisionChange: (String) -> Unit,
    onSiguienteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var tempPhotoFile by remember { mutableStateOf<File?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedFile = tempPhotoFile
        if (success && capturedFile != null && capturedFile.exists()) {
            onFotoCapturada(capturedFile)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val (file, uri) = ImageCompressor.createTempImageUri(context, "INICIO")
            tempPhotoFile = file
            takePictureLauncher.launch(uri)
        } else {
            Toast.makeText(
                context,
                "Se requiere permiso de cámara para tomar la foto del sector",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val areaSeleccionadaM2 = sectores
        .filter { it.idSector in uiState.selectedSectoresIds }
        .sumOf { it.areaM2 }
    val hasValidSector = uiState.selectedSectoresIds.isNotEmpty()
    val hasPhoto = uiState.pathFotoInicio.isNotBlank() && File(uiState.pathFotoInicio).exists()
    val canProceed = hasValidSector && uiState.fotoInicioResuelta && !uiState.isSavingPhoto

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Sector a Limpiar y Foto Inicial",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // CARD 1: Selección Múltiple de Sectores (m²)
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.46f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Seleccionar Sector(es) — Total: $areaSeleccionadaM2 m²",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider()

                    if (sectores.isEmpty()) {
                        Text(
                            text = "No hay sectores para la playa elegida. Vuelve atrás y revisa la playa.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(sectores, key = { it.idSector }) { sector ->
                            val isChecked = sector.idSector in uiState.selectedSectoresIds
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleSector(sector.idSector) }
                                    .padding(vertical = 6.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { onToggleSector(sector.idSector) }
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(
                                        text = sector.nombreSector,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "Superficie: ${sector.areaM2} m²",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // CARD 2: Captura de Foto del Sector al Inicio (Sucio) + Checkbox Omitir
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.54f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Foto de Sector Inicio (Sucio)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        if (hasPhoto || (uiState.omitirFotoInicio && uiState.motivoOmisionInicio.isNotBlank())) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Listo",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            uiState.isSavingPhoto -> CircularProgressIndicator()
                            uiState.omitirFotoInicio -> {
                                MotivoOmisionField(
                                    motivo = uiState.motivoOmisionInicio,
                                    onMotivoChange = onMotivoOmisionChange
                                )
                            }
                            hasPhoto -> {
                                val bitmap = remember(uiState.pathFotoInicio) {
                                    BitmapFactory.decodeFile(uiState.pathFotoInicio)
                                }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Vista previa sector inicio",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            else -> {
                                Text(
                                    text = "Toma una fotografía del sector antes de comenzar o marca omitir abajo.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                val (file, uri) = ImageCompressor.createTempImageUri(context, "INICIO")
                                tempPhotoFile = file
                                takePictureLauncher.launch(uri)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        enabled = !uiState.omitirFotoInicio,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(text = if (hasPhoto) "Repetir Foto de Inicio" else "Tomar Foto de Inicio")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleOmitirFoto(!uiState.omitirFotoInicio) }
                    ) {
                        Checkbox(
                            checked = uiState.omitirFotoInicio,
                            onCheckedChange = onToggleOmitirFoto
                        )
                        Text(
                            text = "Continuar sin fotografía (omitir)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
