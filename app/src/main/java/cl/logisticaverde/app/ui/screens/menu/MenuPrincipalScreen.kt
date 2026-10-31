package cl.logisticaverde.app.ui.screens.menu

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import cl.logisticaverde.app.ui.theme.ErrorRojo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.logisticaverde.app.R
import cl.logisticaverde.app.ui.navigation.BarraInferior
import cl.logisticaverde.app.ui.navigation.PestanaInferior
import cl.logisticaverde.app.ui.theme.AzulMarino
import cl.logisticaverde.app.ui.theme.AzulMarinoSuave
import cl.logisticaverde.app.ui.theme.AzulPetroleo
import cl.logisticaverde.app.ui.theme.Blanco
import cl.logisticaverde.app.ui.theme.BordeSuave
import cl.logisticaverde.app.ui.theme.SyncAmarillo
import cl.logisticaverde.app.ui.theme.SyncGris
import cl.logisticaverde.app.ui.theme.SyncVerde
import cl.logisticaverde.app.ui.theme.VerdeAccion
import cl.logisticaverde.app.ui.theme.VerdeMenta
import kotlinx.coroutines.delay

/**
 * Pantalla 1 — Inicio (diseño del 31-10-2026): encabezado con logo, tarjeta "Playas limpias",
 * botón Iniciar/Reanudar jornada, tarjeta de estado de sincronización y barra inferior.
 */
@Composable
fun MenuPrincipalScreen(
    uiState: MenuPrincipalUiState,
    onIniciarJornadaClick: () -> Unit,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier,
    onReportesClick: () -> Unit = {},
    onFeedbackMostrado: () -> Unit = {}
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Encabezado()
            TarjetaPortada()
            BotonJornada(uiState, onIniciarJornadaClick)
        }
        TarjetaSincronizacion(
            uiState = uiState,
            onSyncClick = onSyncClick,
            onFeedbackMostrado = onFeedbackMostrado,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
        BarraInferior(
            seleccionada = PestanaInferior.INICIO,
            onInicio = {},
            onReportes = onReportesClick
        )
    }
}

@Composable
private fun Encabezado() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Espacio del logo: queda vacío hasta que llegue la imagen (PNG 512×512 con fondo transparente)
        Box(
            modifier = Modifier
                .size(88.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(Blanco)
                .border(2.dp, BordeSuave, CircleShape)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = stringResource(R.string.main_header_title_dos_lineas),
            style = MaterialTheme.typography.headlineMedium.copy(lineHeight = 32.sp),
            fontSize = 30.sp,
            color = AzulPetroleo
        )
    }
}

/** Tarjeta "Playas limpias": degradado provisorio hasta que llegue la ilustración (sin texto, ~1200×800). */
@Composable
private fun TarjetaPortada() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .shadow(8.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFFE6F6FC),
                    0.55f to Color(0xFF9FDCEB),
                    0.78f to Color(0xFF4FC1D6),
                    1f to Color(0xFFF2DDB0)
                )
            )
            .padding(20.dp)
    ) {
        Column {
            Text(
                text = stringResource(R.string.portada_titulo),
                style = MaterialTheme.typography.titleLarge,
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = AzulMarino
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.portada_subtitulo),
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                color = AzulPetroleo
            )
        }
    }
}

@Composable
private fun BotonJornada(uiState: MenuPrincipalUiState, onClick: () -> Unit) {
    val jornada = uiState.jornadaEnCurso
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(24.dp),
            color = VerdeAccion,
            contentColor = Blanco,
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Blanco),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = VerdeAccion, modifier = Modifier.size(30.dp))
                }
                Text(
                    text = stringResource(if (jornada != null) R.string.btn_resume_shift else R.string.btn_start_shift),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(30.dp))
            }
        }
        if (jornada != null) {
            // Decisión 31-10-2026: solo líder de brigada - brigada
            val detalle = listOf(jornada.nombreLider, jornada.brigada).filter { it.isNotBlank() }.joinToString(" - ")
            if (detalle.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(text = detalle, style = MaterialTheme.typography.bodyMedium, color = AzulMarinoSuave)
            }
        }
    }
}

@Composable
private fun TarjetaSincronizacion(
    uiState: MenuPrincipalUiState,
    onSyncClick: () -> Unit,
    onFeedbackMostrado: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val resultado = uiState.resultadoSync
    val sincronizando = uiState.syncButtonState == SyncButtonState.SINCRONIZANDO

    // Resultado: sin texto. Éxito = check con rebote + onda + sonido + vibración; error = ícono rojo.
    val onda = remember { Animatable(0f) }
    LaunchedEffect(resultado) {
        if (resultado == null) return@LaunchedEffect
        if (resultado == ResultadoSync.EXITO) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            reproducirSonidoExito(context)
            onda.snapTo(0f)
            onda.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
        }
        delay(DURACION_RESULTADO_MS)
        onFeedbackMostrado()
    }

    val n = uiState.pendingSyncCount
    val (colorPunto, estado) = when {
        resultado == ResultadoSync.ERROR -> ErrorRojo to "No se pudo sincronizar"
        else -> when (uiState.syncButtonState) {
            SyncButtonState.GRIS_SIN_DATOS -> SyncGris to "Sin jornadas pendientes"
            SyncButtonState.AMARILLO_SIN_INTERNET -> SyncAmarillo to "$n ${if (n == 1) "pendiente" else "pendientes"} · sin internet"
            SyncButtonState.VERDE_LISTO_PARA_SYNC -> SyncVerde to "$n ${if (n == 1) "lista" else "listas"} para sincronizar"
            SyncButtonState.SINCRONIZANDO -> SyncVerde to "Sincronizando…"
        }
    }
    val colorTextoEstado = when (colorPunto) {
        SyncGris -> AzulMarinoSuave
        SyncAmarillo -> Color(0xFF9A6B00)
        ErrorRojo -> ErrorRojo
        else -> SyncVerde
    }
    val tocable = uiState.syncButtonState == SyncButtonState.VERDE_LISTO_PARA_SYNC && resultado == null

    val icono = when {
        sincronizando -> IconoSync.CARGANDO
        resultado == ResultadoSync.EXITO -> IconoSync.EXITO
        resultado == ResultadoSync.ERROR -> IconoSync.ERROR
        else -> IconoSync.NUBE
    }
    val fondoIcono by animateColorAsState(
        targetValue = when (icono) {
            IconoSync.EXITO -> VerdeAccion
            IconoSync.ERROR -> Color(0xFFFDE2E2)
            else -> VerdeMenta
        },
        animationSpec = tween(300),
        label = "fondoIcono"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Blanco,
        shadowElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(enabled = tocable, role = Role.Button, onClick = onSyncClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                // Onda verde que se expande y desvanece al sincronizar con éxito
                if (onda.value in 0.01f..0.99f) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .graphicsLayer {
                                val escala = 1f + onda.value * 0.9f
                                scaleX = escala
                                scaleY = escala
                                alpha = 1f - onda.value
                            }
                            .border(3.dp, VerdeAccion, CircleShape)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(fondoIcono),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = icono,
                        transitionSpec = {
                            (scaleIn(
                                initialScale = 0.3f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                            ) + fadeIn(tween(150))) togetherWith fadeOut(tween(120))
                        },
                        label = "iconoSync"
                    ) { estadoIcono ->
                        when (estadoIcono) {
                            IconoSync.CARGANDO -> CircularProgressIndicator(
                                modifier = Modifier.size(28.dp), strokeWidth = 3.dp, color = AzulMarino
                            )
                            IconoSync.EXITO -> Icon(
                                Icons.Filled.Check, contentDescription = "Sincronizado", tint = Blanco, modifier = Modifier.size(34.dp)
                            )
                            IconoSync.ERROR -> Icon(
                                Icons.Outlined.CloudOff, contentDescription = "No se pudo sincronizar", tint = ErrorRojo, modifier = Modifier.size(30.dp)
                            )
                            IconoSync.NUBE -> Icon(
                                Icons.Outlined.CloudUpload, contentDescription = null, tint = AzulMarino, modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text("Estado de sincronización", style = MaterialTheme.typography.titleMedium, color = AzulMarino)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colorPunto)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = estado,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorTextoEstado
                    )
                }
            }
            if (tocable) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Sincronizar", tint = AzulMarino)
            }
        }
    }
}

private enum class IconoSync { NUBE, CARGANDO, EXITO, ERROR }

private const val DURACION_RESULTADO_MS = 2_500L

/** Sonido corto de éxito (res/raw/sync_exito.wav, generado para la app). Respeta el volumen de sistema. */
private fun reproducirSonidoExito(context: Context) {
    runCatching {
        val atributos = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        MediaPlayer.create(context, R.raw.sync_exito, atributos, 0)?.apply {
            setOnCompletionListener { it.release() }
            start()
        }
    }
}
