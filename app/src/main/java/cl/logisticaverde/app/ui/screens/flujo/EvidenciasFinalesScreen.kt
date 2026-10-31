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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import cl.logisticaverde.app.domain.util.ImageCompressor
import java.io.File

/**
 * Pantalla 6:
 * - Foto de sector limpio (720p) o marcar "Continuar sin fotografía (omitir)".
 * - Foto de sacos llenos (720p) o marcar "Continuar sin fotografía (omitir)".
 * - Botón Siguiente.
 */
@Composable
fun EvidenciasFinalesScreen(
    uiState: EquipoSeleccionUiState,
    onFotoLimpioCapturada: (File) -> Unit,
    onToggleOmitirFotoLimpio: (Boolean) -> Unit,
    onMotivoOmisionLimpioChange: (String) -> Unit,
    onFotoSacosCapturada: (File) -> Unit,
    onToggleOmitirFotoSacos: (Boolean) -> Unit,
    onMotivoOmisionSacosChange: (String) -> Unit,
    onSiguienteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var tempFileLimpio by remember { mutableStateOf<File?>(null) }
    var tempFileSacos by remember { mutableStateOf<File?>(null) }
    var pendingCaptureTarget by remember { mutableStateOf("LIMPIO") }

    val takePictureLimpioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val captured = tempFileLimpio
        if (success && captured != null && captured.exists()) {
            onFotoLimpioCapturada(captured)
        }
    }

    val takePictureSacosLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val captured = tempFileSacos
        if (success && captured != null && captured.exists()) {
            onFotoSacosCapturada(captured)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (pendingCaptureTarget == "LIMPIO") {
                val (file, uri) = ImageCompressor.createTempImageUri(context, "LIMPIO")
                tempFileLimpio = file
                takePictureLimpioLauncher.launch(uri)
            } else {
                val (file, uri) = ImageCompressor.createTempImageUri(context, "SACOS")
                tempFileSacos = file
                takePictureSacosLauncher.launch(uri)
            }
        } else {
            Toast.makeText(context, "Se requiere permiso de cámara", Toast.LENGTH_SHORT).show()
        }
    }

    val hasFotoLimpio = uiState.pathFotoLimpio.isNotBlank() && File(uiState.pathFotoLimpio).exists()
    val hasFotoSacos = uiState.pathFotoSacos.isNotBlank() && File(uiState.pathFotoSacos).exists()
    val canProceed = uiState.fotosFinalesResueltas && !uiState.isSavingPhoto

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Fotografías de Evidencia Final",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // CARD 1: Foto de Sector Limpio
            EvidencePhotoCard(
                title = "1. Foto de Sector Limpio",
                emptyHint = "Toma una fotografía del sector una vez finalizada la limpieza.",
                photoPath = uiState.pathFotoLimpio,
                hasPhoto = hasFotoLimpio,
                isOmitted = uiState.omitirFotoLimpio,
                motivoOmision = uiState.motivoOmisionLimpio,
                onMotivoOmisionChange = onMotivoOmisionLimpioChange,
                isSaving = uiState.isSavingPhoto,
                buttonText = if (hasFotoLimpio) "Repetir Foto Sector Limpio" else "Tomar Foto Sector Limpio",
                onCaptureClick = {
                    pendingCaptureTarget = "LIMPIO"
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        val (file, uri) = ImageCompressor.createTempImageUri(context, "LIMPIO")
                        tempFileLimpio = file
                        takePictureLimpioLauncher.launch(uri)
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onToggleOmitir = onToggleOmitirFotoLimpio,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // CARD 2: Foto de Sacos Llenos
            EvidencePhotoCard(
                title = "2. Foto de Sacos Recolectados",
                emptyHint = "Toma una fotografía de los sacos llenos recolectados por tu brigada.",
                photoPath = uiState.pathFotoSacos,
                hasPhoto = hasFotoSacos,
                isOmitted = uiState.omitirFotoSacos,
                motivoOmision = uiState.motivoOmisionSacos,
                onMotivoOmisionChange = onMotivoOmisionSacosChange,
                isSaving = uiState.isSavingPhoto,
                buttonText = if (hasFotoSacos) "Repetir Foto de Sacos" else "Tomar Foto de Sacos",
                onCaptureClick = {
                    pendingCaptureTarget = "SACOS"
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        val (file, uri) = ImageCompressor.createTempImageUri(context, "SACOS")
                        tempFileSacos = file
                        takePictureSacosLauncher.launch(uri)
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onToggleOmitir = onToggleOmitirFotoSacos,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f)
            )
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

@Composable
private fun EvidencePhotoCard(
    title: String,
    emptyHint: String,
    photoPath: String,
    hasPhoto: Boolean,
    isOmitted: Boolean,
    motivoOmision: String,
    onMotivoOmisionChange: (String) -> Unit,
    isSaving: Boolean,
    buttonText: String,
    onCaptureClick: () -> Unit,
    onToggleOmitir: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (hasPhoto || (isOmitted && motivoOmision.isNotBlank())) {
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
                    isSaving -> CircularProgressIndicator()
                    isOmitted -> {
                        MotivoOmisionField(
                            motivo = motivoOmision,
                            onMotivoChange = onMotivoOmisionChange
                        )
                    }
                    hasPhoto -> {
                        val bitmap = remember(photoPath) {
                            BitmapFactory.decodeFile(photoPath)
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = emptyHint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onCaptureClick,
                enabled = !isOmitted,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(text = buttonText)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleOmitir(!isOmitted) }
            ) {
                Checkbox(
                    checked = isOmitted,
                    onCheckedChange = onToggleOmitir
                )
                Text(
                    text = "Continuar sin fotografía (omitir)",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
