package cl.logisticaverde.app.ui.screens.resumen

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.logisticaverde.app.domain.resumen.ResumenImpacto
import cl.logisticaverde.app.domain.sync.RegistroErrores
import cl.logisticaverde.app.ui.screens.flujo.FondoVacioFlujo
import cl.logisticaverde.app.ui.screens.flujo.FormaTarjetaFlujo
import cl.logisticaverde.app.ui.screens.flujo.MensajeFlujo
import cl.logisticaverde.app.ui.screens.flujo.TituloPantallaFlujo
import cl.logisticaverde.app.ui.theme.AzulMarino
import cl.logisticaverde.app.ui.theme.AzulPetroleo
import cl.logisticaverde.app.ui.theme.Blanco
import cl.logisticaverde.app.ui.theme.BordeSuave
import cl.logisticaverde.app.ui.theme.CieloClaro
import cl.logisticaverde.app.ui.theme.SyncGrisFondo
import cl.logisticaverde.app.ui.theme.SyncGrisIcono
import cl.logisticaverde.app.ui.theme.VerdeAccion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pantalla 10 "Resumen de impacto" (RF-P10, diseño ISSUE-56). La vista previa es el mismo bitmap 1080×1920
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
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        TituloPantallaFlujo("Resumen de impacto")
        Spacer(Modifier.height(12.dp))
        when (uiState) {
            // Sin ruedita: mientras carga se ve la tarjeta vacía y los botones grises (decisión 3)
            ResumenImpactoUiState.Cargando -> ContenidoResumen(resumen = null, modifier = Modifier.weight(1f))
            is ResumenImpactoUiState.Listo -> ContenidoResumen(resumen = uiState.resumen, modifier = Modifier.weight(1f))
            ResumenImpactoUiState.NoEncontrada -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    FondoVacioFlujo(Icons.Outlined.SearchOff, "No se encontró la jornada en este teléfono")
                }
            }
        }
        TextButton(onClick = onVolverAlInicio, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Volver al inicio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AzulPetroleo
            )
        }
    }
}

@Composable
private fun ContenidoResumen(resumen: ResumenImpacto?, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var ocupado by remember { mutableStateOf(false) }

    // Aviso de una línea (decisión 5): el verde se desvanece solo; el rojo queda hasta el siguiente intento
    var mensaje by remember { mutableStateOf("") }
    var mensajeEsError by remember { mutableStateOf(false) }
    var mensajeSeq by remember { mutableIntStateOf(0) }
    var mensajeVisible by remember { mutableStateOf(false) }
    fun avisar(texto: String, esError: Boolean) {
        mensaje = texto
        mensajeEsError = esError
        mensajeSeq++
    }
    LaunchedEffect(mensajeSeq) {
        mensajeVisible = mensajeSeq > 0
        if (mensajeVisible && !mensajeEsError) {
            delay(DURACION_AVISO_OK_MS)
            mensajeVisible = false
        }
    }

    LaunchedEffect(resumen) {
        if (resumen == null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.Default) {
            runCatching { ResumenImpactoGenerator.generar(context, resumen) }
                .onFailure { RegistroErrores.advertencia("ResumenImpacto", "No se pudo generar la tarjeta", it) }
                .getOrNull()
        }
        if (bitmap == null) avisar("No se pudo generar la imagen del resumen.", esError = true)
    }

    fun guardar() {
        val bmp = bitmap ?: return
        val id = resumen?.idRegistro ?: return
        ocupado = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { CompartirResumen.guardarEnGaleria(context, bmp, id) }
            if (ok) {
                avisar("Imagen guardada en Galería › DejaTuPlayaLimpia.", esError = false)
            } else {
                avisar("No se pudo guardar la imagen.", esError = true)
            }
            ocupado = false
        }
    }

    val permisoGaleria = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) guardar() else avisar("Sin permiso de almacenamiento no se puede guardar en la galería.", esError = true)
    }

    val listo = bitmap != null && resumen != null && !ocupado

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Vista previa en una tarjeta blanca (decisión 3)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Surface(
                shape = FormaTarjetaFlujo,
                color = Blanco,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                    .shadow(4.dp, FormaTarjetaFlujo)
            ) {
                Box(
                    modifier = Modifier
                        .padding(10.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    // Hueco celeste mientras se genera; la imagen entra con un fundido
                    val opacidadImagen by animateFloatAsState(
                        targetValue = if (bitmap != null) 1f else 0f,
                        animationSpec = tween(DURACION_FUNDIDO_MS),
                        label = "fundidoResumen"
                    )
                    // Hueco celeste mientras se genera; se apaga a medida que entra la imagen
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = 1f - opacidadImagen }
                            .background(CieloClaro.copy(alpha = 0.5f))
                    )
                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Tarjeta del resumen de impacto",
                            // Crop: el hueco no es exactamente 9:16 por el margen; recorta píxeles, no franjas
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = opacidadImagen }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = mensajeVisible && mensaje.isNotBlank(),
            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
            exit = fadeOut(tween(600)) + shrinkVertically(tween(600, delayMillis = 300))
        ) {
            MensajeFlujo(texto = mensaje, esError = mensajeEsError, modifier = Modifier.padding(top = 12.dp))
        }

        Spacer(Modifier.height(16.dp))
        // Compartir: píldora verde principal (decisión 4)
        Button(
            onClick = {
                val bmp = bitmap ?: return@Button
                val id = resumen?.idRegistro ?: return@Button
                ocupado = true
                scope.launch {
                    val archivo = withContext(Dispatchers.IO) {
                        runCatching { ResumenImpactoGenerator.guardarParaCompartir(context, bmp, id) }.getOrNull()
                    }
                    ocupado = false
                    if (archivo == null) {
                        avisar("No se pudo preparar la imagen.", esError = true)
                    } else {
                        runCatching { CompartirResumen.compartir(context, archivo) }
                            .onFailure { avisar("No hay ninguna app para compartir la imagen.", esError = true) }
                    }
                }
            },
            enabled = listo,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = VerdeAccion,
                contentColor = Blanco,
                disabledContainerColor = SyncGrisFondo,
                disabledContentColor = SyncGrisIcono
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
        ) {
            ContenidoBoton(Icons.Filled.Share, "Compartir en Instagram")
        }

        Spacer(Modifier.height(10.dp))
        // Guardar: píldora blanca con borde (decisión 4)
        OutlinedButton(
            onClick = {
                if (CompartirResumen.requierePermisoGaleria) {
                    permisoGaleria.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    guardar()
                }
            },
            enabled = listo,
            shape = CircleShape,
            border = BorderStroke(1.dp, BordeSuave),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Blanco,
                contentColor = AzulMarino,
                disabledContainerColor = Blanco,
                disabledContentColor = SyncGrisIcono
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            ContenidoBoton(Icons.Outlined.Download, "Guardar en galería")
        }
    }
}

@Composable
private fun ContenidoBoton(icono: ImageVector, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icono, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(text = texto, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    }
}

private const val DURACION_AVISO_OK_MS = 3_000L
private const val DURACION_FUNDIDO_MS = 400
