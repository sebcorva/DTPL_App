package cl.logisticaverde.app.ui.screens.resumen

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cl.logisticaverde.app.domain.resumen.ResumenImpacto
import cl.logisticaverde.app.domain.sync.RegistroErrores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pantalla 10 — Resumen de Impacto (RF-P10). La vista previa es el mismo bitmap 1080×1920
 * que se comparte, así lo que ve el líder es exactamente lo que se publica.
 */
@Composable
fun ResumenImpactoScreen(
    uiState: ResumenImpactoUiState,
    onVolverAlInicio: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Resumen de Impacto",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        when (uiState) {
            ResumenImpactoUiState.Cargando -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            ResumenImpactoUiState.NoEncontrada -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("No se encontró la jornada en este teléfono.", textAlign = TextAlign.Center)
            }
            is ResumenImpactoUiState.Listo -> ContenidoResumen(uiState.resumen, Modifier.weight(1f))
        }
        TextButton(onClick = onVolverAlInicio, modifier = Modifier.fillMaxWidth()) {
            Text("Volver al inicio")
        }
    }
}

@Composable
private fun ContenidoResumen(resumen: ResumenImpacto, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }

    LaunchedEffect(resumen) {
        bitmap = withContext(Dispatchers.Default) {
            runCatching { ResumenImpactoGenerator.generar(context, resumen) }
                .onFailure { RegistroErrores.advertencia("ResumenImpacto", "No se pudo generar la tarjeta", it) }
                .getOrNull()
        }
        if (bitmap == null) mensaje = "No se pudo generar la imagen del resumen."
    }

    fun guardar() {
        val bmp = bitmap ?: return
        ocupado = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                CompartirResumen.guardarEnGaleria(context, bmp, resumen.idRegistro)
            }
            mensaje = if (ok) "Imagen guardada en Galería › DejaTuPlayaLimpia." else "No se pudo guardar la imagen."
            ocupado = false
        }
    }

    val permisoGaleria = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) guardar() else mensaje = "Sin permiso de almacenamiento no se puede guardar en la galería."
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val bmp = bitmap
            if (bmp == null) {
                CircularProgressIndicator()
            } else {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Tarjeta del resumen de impacto",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
        }

        mensaje?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }

        Button(
            onClick = {
                val bmp = bitmap ?: return@Button
                ocupado = true
                scope.launch {
                    val archivo = withContext(Dispatchers.IO) {
                        runCatching { ResumenImpactoGenerator.guardarParaCompartir(context, bmp, resumen.idRegistro) }.getOrNull()
                    }
                    ocupado = false
                    if (archivo == null) {
                        mensaje = "No se pudo preparar la imagen."
                    } else {
                        runCatching { CompartirResumen.compartir(context, archivo) }
                            .onFailure { mensaje = "No hay ninguna app para compartir la imagen." }
                    }
                }
            },
            enabled = bitmap != null && !ocupado,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Compartir en Instagram", style = MaterialTheme.typography.titleMedium)
        }

        OutlinedButton(
            onClick = {
                if (CompartirResumen.requierePermisoGaleria) {
                    permisoGaleria.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    guardar()
                }
            },
            enabled = bitmap != null && !ocupado,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Guardar en galería")
        }
    }
}
