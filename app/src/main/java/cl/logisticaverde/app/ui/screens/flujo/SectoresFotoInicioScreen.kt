package cl.logisticaverde.app.ui.screens.flujo

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
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
import cl.logisticaverde.app.data.local.entity.SectorCatalogoEntity
import cl.logisticaverde.app.domain.util.ImageCompressor
import cl.logisticaverde.app.ui.theme.AzulMarino
import cl.logisticaverde.app.ui.theme.AzulMarinoSuave
import cl.logisticaverde.app.ui.theme.Blanco
import cl.logisticaverde.app.ui.theme.VerdeAccion
import java.io.File

/**
 * Pantalla 3 "Seleccionar sector a limpiar" (diseño ISSUE-46):
 * - Tarjeta 1: sectores, uno por fila a todo el ancho (selección múltiple; los m² se calculan
 *   internamente y no se muestran).
 * - Tarjeta 2: foto de sector inicio (comprimida a 720p) en un visor con el botón encima,
 *   o "Continuar sin fotografía" con motivo obligatorio (ISSUE-35) sobre un fondo "Sin fotografía".
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
    val tomarFoto = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            val (file, uri) = ImageCompressor.createTempImageUri(context, "INICIO")
            tempPhotoFile = file
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val hasValidSector = uiState.selectedSectoresIds.isNotEmpty()
    val hasPhoto = uiState.pathFotoInicio.isNotBlank() && File(uiState.pathFotoInicio).exists()
    val canProceed = hasValidSector && uiState.fotoInicioResuelta && !uiState.isSavingPhoto

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        TituloPantallaFlujo("Seleccionar sector a limpiar")
        Spacer(Modifier.height(12.dp))

        // TARJETA 1: selección múltiple de sectores
        TarjetaFlujo(modifier = Modifier.weight(0.45f)) {
            SubtituloFlujo("Seleccionar sector")
            Spacer(Modifier.height(12.dp))
            if (sectores.isEmpty()) {
                Text(
                    text = "No hay sectores para la playa elegida. Vuelve atrás y revisa la playa.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AzulMarinoSuave
                )
            }
            // Un sector por fila, a todo el ancho de la tarjeta
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(sectores, key = { it.idSector }) { sector ->
                    BotonSeleccionFlujo(
                        texto = sector.nombreSector,
                        activo = sector.idSector in uiState.selectedSectoresIds,
                        onClick = { onToggleSector(sector.idSector) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // TARJETA 2: foto de sector inicio + "Continuar sin fotografía"
        TarjetaFlujo(modifier = Modifier.weight(0.55f)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                SubtituloFlujo("Foto de sector inicio", modifier = Modifier.weight(1f))
                if (hasPhoto || (uiState.omitirFotoInicio && EquipoSeleccionUiState.esMotivoValido(uiState.motivoOmisionInicio))) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Listo",
                        tint = VerdeAccion,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when {
                    uiState.isSavingPhoto -> CircularProgressIndicator(color = VerdeAccion)
                    // Sin foto: fondo propio "Sin fotografía" y el motivo encima
                    uiState.omitirFotoInicio -> FondoVacioFlujo(Icons.Outlined.NoPhotography, "Sin fotografía") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Blanco,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                        ) {
                            MotivoOmisionField(
                                motivo = uiState.motivoOmisionInicio,
                                onMotivoChange = onMotivoOmisionChange,
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)
                            )
                        }
                    }
                    // Ilustración: res/drawable-nodpi/playa_sucia_p3.webp (original en Mockup/assets/background_playa_sucia.jpg)
                    else -> VisorFotoFlujo(
                        pathFoto = if (hasPhoto) uiState.pathFotoInicio else null,
                        ilustracion = R.drawable.playa_sucia_p3,
                        descripcionFoto = "Vista previa sector inicio"
                    ) {
                        BotonCamaraFlujo(
                            texto = if (hasPhoto) "Repetir foto de inicio" else "Tomar foto de inicio",
                            onClick = tomarFoto
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleOmitirFoto(!uiState.omitirFotoInicio) }
            ) {
                Checkbox(
                    checked = uiState.omitirFotoInicio,
                    onCheckedChange = onToggleOmitirFoto,
                    colors = coloresCheckboxFlujo()
                )
                Text(
                    text = "Continuar sin fotografía",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AzulMarino
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        BotonSiguienteFlujo(enabled = canProceed, onClick = onSiguienteClick)
    }
}
