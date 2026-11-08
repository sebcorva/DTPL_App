package cl.logisticaverde.app.ui.screens.inicio

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import cl.logisticaverde.app.ui.screens.menu.EncabezadoApp
import cl.logisticaverde.app.ui.theme.CieloBlanco
import cl.logisticaverde.app.ui.theme.CieloClaro
import cl.logisticaverde.app.ui.theme.VerdeAccion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Animación de entrada (ISSUE-54), dibujada encima del inicio:
 * verde de la app → degradado celeste, aparecen logo y título (iguales al encabezado del inicio),
 * pausa y fundido hacia el inicio. Unos 2 s. Con las animaciones del sistema desactivadas, termina al tiro.
 */
@Composable
fun IntroAnimada(onTerminar: () -> Unit) {
    val context = LocalContext.current
    val animacionesActivas = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val verde = remember { Animatable(1f) }      // opacidad del verde sobre el celeste
    val marca = remember { Animatable(0f) }      // aparición del logo y el título
    val pantalla = remember { Animatable(1f) }   // opacidad de toda la animación

    LaunchedEffect(Unit) {
        if (!animacionesActivas) {
            onTerminar()
            return@LaunchedEffect
        }
        delay(DEMORA_INICIAL_MS)
        launch { verde.animateTo(0f, tween(DURACION_VERDE_A_CELESTE_MS, easing = FastOutSlowInEasing)) }
        delay(DURACION_VERDE_A_CELESTE_MS * 6L / 10L) // con el fondo ya casi celeste
        marca.animateTo(1f, tween(DURACION_MARCA_MS, easing = LinearOutSlowInEasing))
        delay(PAUSA_MS)
        pantalla.animateTo(0f, tween(DURACION_SALIDA_MS))
        onTerminar()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = pantalla.value }
            // Mismo fondo que la app, con el verde encima desvaneciéndose
            .background(Brush.verticalGradient(listOf(CieloClaro, CieloBlanco)))
            .background(VerdeAccion.copy(alpha = verde.value))
            // Mientras dura, los toques no llegan al inicio que está debajo
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = marca.value
                val escala = 0.9f + 0.1f * marca.value
                scaleX = escala
                scaleY = escala
            }
        ) {
            EncabezadoApp()
        }
    }
}

private const val DEMORA_INICIAL_MS = 150L
private const val DURACION_VERDE_A_CELESTE_MS = 1_000
private const val DURACION_MARCA_MS = 500
private const val PAUSA_MS = 600L
private const val DURACION_SALIDA_MS = 400
