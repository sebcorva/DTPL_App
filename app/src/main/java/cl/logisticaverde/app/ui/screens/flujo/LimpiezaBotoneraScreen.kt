package cl.logisticaverde.app.ui.screens.flujo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import cl.logisticaverde.app.R
import cl.logisticaverde.app.data.local.entity.BasuraRecolectadaEntity
import cl.logisticaverde.app.service.LimpiezaLocationService
import cl.logisticaverde.app.ui.theme.AzulMarino
import cl.logisticaverde.app.ui.theme.AzulMarinoSuave
import cl.logisticaverde.app.ui.theme.AzulPetroleo
import cl.logisticaverde.app.ui.theme.Blanco
import cl.logisticaverde.app.ui.theme.BordeSuave
import cl.logisticaverde.app.ui.theme.RojoCalido
import cl.logisticaverde.app.ui.theme.SyncGris
import cl.logisticaverde.app.ui.theme.VerdeAccion
import cl.logisticaverde.app.ui.theme.VerdeMenta
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

/** Formato chileno: punto de miles ("1.453") y coma decimal ("3,25 km"). */

/**
 * Pantalla 5 "Registro de residuos" (diseño ISSUE-49):
 * - Tarjeta superior: total de ítems sobre la ilustración; franja con la distancia (ícono verde si el GPS está activo)
 *   a la izquierda y el tiempo de limpieza en vivo a la derecha.
 * - Grilla de 3 por fila (las sobrantes se estiran para no quedar solas): un recuadro por tipo de residuo con [-1] [+1] (vibración breve al tocar; sin [+10] desde el 07-11-2026).
 * - Un botón abajo: "Comenzar limpieza" (la grilla se ve atenuada y bloqueada) → "Finalizar limpieza" (rojo cálido) con confirmación.
 */
@Composable
fun LimpiezaBotoneraScreen(
    uiState: EquipoSeleccionUiState,
    itemsBasura: List<BasuraRecolectadaEntity>,
    onComenzarLimpieza: () -> Unit,
    onModificarConteo: (idTipoBasura: String, delta: Int) -> Unit,
    onFinalizarLimpieza: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showConfirmFinalizarDialog by remember { mutableStateOf(false) }

    // Ubicación para los km (servicio en primer plano) y, en Android 13+, notificaciones para verlos
    val permisosLimpieza = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Sin permiso de ubicación la limpieza igual comienza (sin km); el servicio no se inicia
        onComenzarLimpieza()
        uiState.idRegistroActivo?.let { LimpiezaLocationService.iniciar(context, it) }
    }

    // Al volver a P5 (o reanudar la jornada) con la limpieza en curso, el GPS debe estar corriendo
    LaunchedEffect(uiState.isLimpiezaIniciada, uiState.horaFinMs, uiState.idRegistroActivo) {
        val id = uiState.idRegistroActivo
        if (uiState.isLimpiezaIniciada && uiState.horaFinMs == 0L && id != null) {
            LimpiezaLocationService.iniciar(context, id)
        }
    }

    val isStarted = uiState.isLimpiezaIniciada
    val totalUnidades = itemsBasura.sumOf { it.cantidad }
    val view = LocalView.current
    // GPS activo: limpieza en curso y permiso de ubicación concedido (si no, la limpieza sigue sin km)
    val gpsActivo = isStarted && uiState.horaFinMs == 0L && remember(isStarted) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        TituloPantallaFlujo("Registro de residuos")
        Spacer(Modifier.height(12.dp))

        // Tiempo de limpieza en vivo: se actualiza cada segundo mientras la limpieza está en curso
        var ahoraMs by remember { mutableStateOf(System.currentTimeMillis()) }
        LaunchedEffect(uiState.horaInicioMs, uiState.horaFinMs) {
            while (uiState.horaInicioMs > 0L && uiState.horaFinMs == 0L) {
                ahoraMs = System.currentTimeMillis()
                delay(1_000L)
            }
        }
        val transcurridoMs = when {
            uiState.horaInicioMs == 0L -> 0L
            uiState.horaFinMs > 0L -> uiState.horaFinMs - uiState.horaInicioMs
            else -> ahoraMs - uiState.horaInicioMs
        }

        TarjetaResumenLimpieza(
            total = totalUnidades,
            km = uiState.kmRecorridos,
            gpsActivo = gpsActivo,
            tiempo = formatoDuracion(transcurridoMs)
        )

        Spacer(Modifier.height(14.dp))

        // Grilla de residuos: atenuada y bloqueada hasta comenzar
        // 6 partes por fila: 3 tarjetas normales; las sobrantes se estiran (spanTarjetaResiduo)
        LazyVerticalGrid(
            columns = GridCells.Fixed(COLUMNAS_GRILLA_RESIDUOS),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .alpha(if (isStarted) 1f else 0.45f)
        ) {
            itemsIndexed(
                itemsBasura,
                key = { _, item -> item.idTipoBasura },
                span = { indice, _ -> GridItemSpan(spanTarjetaResiduo(indice, itemsBasura.size)) }
            ) { indice, item ->
                TarjetaResiduo(
                    item = item,
                    horizontal = spanTarjetaResiduo(indice, itemsBasura.size) == COLUMNAS_GRILLA_RESIDUOS,
                    habilitado = isStarted,
                    onModificar = { delta ->
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onModificarConteo(item.idTipoBasura, delta)
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        BotonLimpieza(
            iniciada = isStarted,
            onClick = {
                if (!isStarted) {
                    val faltanPermisos = permisosLimpieza.any {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (faltanPermisos) {
                        permissionLauncher.launch(permisosLimpieza)
                    } else {
                        onComenzarLimpieza()
                        uiState.idRegistroActivo?.let { LimpiezaLocationService.iniciar(context, it) }
                    }
                } else {
                    showConfirmFinalizarDialog = true
                }
            }
        )
    }

    if (showConfirmFinalizarDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmFinalizarDialog = false },
            containerColor = Blanco,
            title = {
                Text("¿Finalizar limpieza?", fontWeight = FontWeight.ExtraBold, color = AzulMarino)
            },
            text = {
                Text(
                    "Se registrará la hora de término y pasarás a las fotos de evidencia final. " +
                        "Total contado: ${formatoMiles(totalUnidades)} ítems.",
                    color = AzulMarinoSuave
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmFinalizarDialog = false
                        onFinalizarLimpieza()
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = RojoCalido, contentColor = Blanco)
                ) {
                    Text("Sí, finalizar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmFinalizarDialog = false }) {
                    Text("Seguir limpiando", fontWeight = FontWeight.Bold, color = AzulPetroleo)
                }
            }
        )
    }
}

/** Partes por fila de la grilla de residuos: 3 tarjetas normales de 2 partes cada una. */
internal const val COLUMNAS_GRILLA_RESIDUOS = 6

/**
 * Partes que ocupa la tarjeta [indice] de [total] para que ninguna quede sola (ajuste del 07-11-2026):
 * múltiplo de 3 → todas 2 (filas de 3); sobran 2 → las 2 últimas 3 (media fila); sobra 1 → la última 6 (fila completa).
 */
internal fun spanTarjetaResiduo(indice: Int, total: Int): Int {
    val sobrantes = total % 3
    val inicioSobrantes = total - sobrantes
    return when {
        indice < inicioSobrantes -> 2
        sobrantes == 1 -> COLUMNAS_GRILLA_RESIDUOS
        else -> COLUMNAS_GRILLA_RESIDUOS / 2
    }
}

/** Tiempo transcurrido como "hh:mm:ss" ("00:42:15"); negativos cuentan como 0. */
internal fun formatoDuracion(ms: Long): String {
    val totalSegundos = (ms.coerceAtLeast(0L)) / 1_000L
    return String.format(
        Locale.ROOT, "%02d:%02d:%02d",
        totalSegundos / 3_600L, (totalSegundos % 3_600L) / 60L, totalSegundos % 60L
    )
}

private fun formatoMiles(n: Int): String = NumberFormat.getIntegerInstance(LocaleChile).format(n)

/** Total de ítems sobre la ilustración de playa y franja con la distancia (decisión 2). */
@Composable
private fun TarjetaResumenLimpieza(total: Int, km: Float, gpsActivo: Boolean, tiempo: String) {
    Surface(
        shape = FormaTarjetaFlujo,
        color = Blanco,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, FormaTarjetaFlujo)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                // Ilustración (res/drawable-nodpi/portada_p1.webp), la misma de la P1
                Image(
                    painter = painterResource(R.drawable.portada_p1),
                    contentDescription = null, // decorativa
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                )
                // Velo blanco suave a la izquierda para que el número se lea sobre el mar
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0f)),
                                center = Offset(0f, 0f),
                                radius = 900f
                            )
                        )
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ítems registrados",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AzulMarino
                        )
                        Text(
                            text = formatoMiles(total),
                            fontSize = 46.sp,
                            lineHeight = 50.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AzulMarino
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Blanco.copy(alpha = 0.9f))
                    ) {
                        Icon(Icons.Filled.Eco, contentDescription = null, tint = VerdeAccion, modifier = Modifier.size(30.dp))
                    }
                }
            }
            // Ubicación + distancia a la izquierda; tiempo de limpieza en vivo a la derecha (ajuste del 07-11-2026)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = if (gpsActivo) "GPS activo" else "GPS inactivo",
                    tint = if (gpsActivo) VerdeAccion else SyncGris,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = buildAnnotatedString {
                        append("Distancia: ")
                        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                            append(String.format(LocaleChile, "%.2f km", km))
                        }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = AzulMarino,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Filled.Timer,
                    contentDescription = "Tiempo de limpieza",
                    tint = AzulPetroleo,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = tiempo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = AzulMarino,
                    maxLines = 1
                )
            }
        }
    }
}

/** Recuadro de un tipo de residuo: nombre (hasta 2 líneas), conteo y [-1] [+1] (decisiones 3 y 4). */
@Composable
private fun TarjetaResiduo(
    item: BasuraRecolectadaEntity,
    habilitado: Boolean,
    onModificar: (Int) -> Unit,
    horizontal: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = VerdeMenta,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (horizontal) {
            // Tarjeta sobrante a todo el ancho: nombre | número | [-1] [+1] en una sola línea
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = item.nombreVisible,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AzulMarino,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatoMiles(item.cantidad),
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AzulMarino,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                BotonesConteo(
                    item = item,
                    habilitado = habilitado,
                    onModificar = onModificar,
                    modifier = Modifier.width(132.dp)
                )
            }
            return@Surface
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 6.dp)
        ) {
            // Nombres largos del catálogo: se achica la letra hasta 11sp para caber en 2 líneas (ISSUE-50: nombres cortos)
            var tamanoNombre by remember(item.nombreVisible) { mutableStateOf(13f) }
            // Alto fijo (2 líneas de 13sp) para que todos los recuadros de una fila queden parejos
            Box(contentAlignment = Alignment.Center, modifier = Modifier.height(36.dp)) {
            Text(
                text = item.nombreVisible,
                style = MaterialTheme.typography.bodySmall,
                fontSize = tamanoNombre.sp,
                lineHeight = (tamanoNombre + 2f).sp,
                fontWeight = FontWeight.SemiBold,
                color = AzulMarino,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { layout ->
                    if (layout.hasVisualOverflow && tamanoNombre > 11f) tamanoNombre -= 1f
                }
            )
            }
            Text(
                text = formatoMiles(item.cantidad),
                fontSize = 28.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = AzulMarino
            )
            Spacer(Modifier.height(4.dp))
            BotonesConteo(item = item, habilitado = habilitado, onModificar = onModificar, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** [-1] blanco con borde (deshabilitado en 0) y [+1] verde. */
@Composable
private fun BotonesConteo(
    item: BasuraRecolectadaEntity,
    habilitado: Boolean,
    onModificar: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        BotonConteo(
            texto = "-1",
            fondo = Blanco,
            contenido = AzulMarino,
            borde = BorderStroke(1.dp, BordeSuave),
            habilitado = habilitado && item.cantidad > 0,
            onClick = { onModificar(-1) },
            modifier = Modifier.weight(1f)
        )
        BotonConteo(
            texto = "+1",
            fondo = VerdeAccion,
            contenido = Blanco,
            habilitado = habilitado,
            onClick = { onModificar(1) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BotonConteo(
    texto: String,
    fondo: Color,
    contenido: Color,
    habilitado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    borde: BorderStroke? = null
) {
    Surface(
        onClick = onClick,
        enabled = habilitado,
        shape = RoundedCornerShape(10.dp),
        color = fondo,
        contentColor = contenido,
        border = borde,
        modifier = modifier
            .height(36.dp)
            .alpha(if (habilitado) 1f else 0.5f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = texto, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

/** "Comenzar limpieza" (verde, play) → "Finalizar limpieza" (rojo cálido de advertencia, sin ícono) (decisión 5). */
@Composable
private fun BotonLimpieza(iniciada: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (iniciada) RojoCalido else VerdeAccion,
            contentColor = Blanco
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // "Finalizar limpieza" va sin ícono (ajuste del 07-11-2026); "Comenzar" mantiene el play
            if (!iniciada) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = if (iniciada) "Finalizar limpieza" else "Comenzar limpieza",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}
