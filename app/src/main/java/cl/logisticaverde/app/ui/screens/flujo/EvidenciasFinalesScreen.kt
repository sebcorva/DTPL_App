package cl.logisticaverde.app.ui.screens.flujo

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cl.logisticaverde.app.R
import cl.logisticaverde.app.domain.util.ImageCompressor
import cl.logisticaverde.app.ui.theme.AzulMarino
import cl.logisticaverde.app.ui.theme.Blanco
import cl.logisticaverde.app.ui.theme.VerdeAccion
import java.io.File

/**
 * Pantalla 6 "Fotografías de evidencia" (diseño ISSUE-51, mismo formato de la P3):
 * - Tarjeta 1: foto de sector limpio (720p) en un visor con el botón encima, o "Continuar sin fotografía"
 *   con motivo obligatorio (ISSUE-35).
 * - Tarjeta 2: foto de sacos llenos, igual.
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

    val lanzarCamara = { destino: String ->
        val (file, uri) = ImageCompressor.createTempImageUri(context, destino)
        if (destino == "LIMPIO") {
            tempFileLimpio = file
            takePictureLimpioLauncher.launch(uri)
        } else {
            tempFileSacos = file
            takePictureSacosLauncher.launch(uri)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            lanzarCamara(pendingCaptureTarget)
        } else {
            Toast.makeText(context, "Se requiere permiso de cámara", Toast.LENGTH_SHORT).show()
        }
    }

    val tomarFoto = { destino: String ->
        pendingCaptureTarget = destino
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) lanzarCamara(destino) else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val hasFotoLimpio = uiState.pathFotoLimpio.isNotBlank() && File(uiState.pathFotoLimpio).exists()
    val hasFotoSacos = uiState.pathFotoSacos.isNotBlank() && File(uiState.pathFotoSacos).exists()
    val canProceed = uiState.fotosFinalesResueltas && !uiState.isSavingPhoto

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        TituloPantallaFlujo("Fotografías de evidencia")
        Spacer(Modifier.height(12.dp))

        // TARJETA 1: foto de sector limpio
        TarjetaFotoEvidencia(
            titulo = "Foto de sector limpio",
            // Ilustración: res/drawable-nodpi/playa_limpia_p6.webp (original en Mockup/assets/background_playa_limpia.jpg)
            ilustracion = R.drawable.playa_limpia_p6,
            pathFoto = uiState.pathFotoLimpio,
            hasFoto = hasFotoLimpio,
            omitida = uiState.omitirFotoLimpio,
            motivoOmision = uiState.motivoOmisionLimpio,
            onMotivoOmisionChange = onMotivoOmisionLimpioChange,
            guardando = uiState.isSavingPhoto,
            textoBoton = if (hasFotoLimpio) "Repetir foto de sector limpio" else "Tomar foto de sector limpio",
            onTomarFoto = { tomarFoto("LIMPIO") },
            onToggleOmitir = onToggleOmitirFotoLimpio,
            modifier = Modifier.weight(0.5f)
        )

        Spacer(Modifier.height(16.dp))

        // TARJETA 2: foto de sacos llenos
        TarjetaFotoEvidencia(
            titulo = "Foto de sacos llenos",
            // Ilustración: res/drawable-nodpi/sacos_p6.webp (original en Mockup/assets/background_sacos.jpg)
            ilustracion = R.drawable.sacos_p6,
            pathFoto = uiState.pathFotoSacos,
            hasFoto = hasFotoSacos,
            omitida = uiState.omitirFotoSacos,
            motivoOmision = uiState.motivoOmisionSacos,
            onMotivoOmisionChange = onMotivoOmisionSacosChange,
            guardando = uiState.isSavingPhoto,
            textoBoton = if (hasFotoSacos) "Repetir foto de sacos" else "Tomar foto de sacos",
            onTomarFoto = { tomarFoto("SACOS") },
            onToggleOmitir = onToggleOmitirFotoSacos,
            modifier = Modifier.weight(0.5f)
        )

        Spacer(Modifier.height(16.dp))
        BotonSiguienteFlujo(enabled = canProceed, onClick = onSiguienteClick)
    }
}

/** Tarjeta de una foto de evidencia: subtítulo con ✓, visor (o "Sin fotografía" + motivo) y "Continuar sin fotografía". */
@Composable
private fun TarjetaFotoEvidencia(
    titulo: String,
    @DrawableRes ilustracion: Int,
    pathFoto: String,
    hasFoto: Boolean,
    omitida: Boolean,
    motivoOmision: String,
    onMotivoOmisionChange: (String) -> Unit,
    guardando: Boolean,
    textoBoton: String,
    onTomarFoto: () -> Unit,
    onToggleOmitir: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    TarjetaFlujo(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SubtituloFlujo(titulo, modifier = Modifier.weight(1f))
            if (hasFoto || (omitida && motivoOmision.isNotBlank())) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Listo",
                    tint = VerdeAccion,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when {
                guardando -> CircularProgressIndicator(color = VerdeAccion)
                // Sin foto: fondo propio "Sin fotografía" y el motivo encima
                omitida -> FondoVacioFlujo(Icons.Outlined.NoPhotography, "Sin fotografía") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Blanco,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    ) {
                        MotivoOmisionField(
                            motivo = motivoOmision,
                            onMotivoChange = onMotivoOmisionChange,
                            minLines = 1,
                            maxLines = 2,
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp)
                        )
                    }
                }
                else -> VisorFotoFlujo(
                    pathFoto = if (hasFoto) pathFoto else null,
                    ilustracion = ilustracion,
                    descripcionFoto = "Vista previa: $titulo"
                ) {
                    BotonCamaraFlujo(texto = textoBoton, onClick = onTomarFoto)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onToggleOmitir(!omitida) }
        ) {
            Checkbox(
                checked = omitida,
                onCheckedChange = onToggleOmitir,
                colors = coloresCheckboxFlujo()
            )
            Text(
                text = "Continuar sin fotografía",
                style = MaterialTheme.typography.bodyLarge,
                color = AzulMarino
            )
        }
    }
}
