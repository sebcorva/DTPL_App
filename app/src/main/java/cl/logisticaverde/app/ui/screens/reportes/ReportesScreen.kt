package cl.logisticaverde.app.ui.screens.reportes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cl.logisticaverde.app.data.local.db.JornadaDao
import cl.logisticaverde.app.data.local.entity.JornadaEntity
import cl.logisticaverde.app.data.repository.JornadaSyncRepository
import cl.logisticaverde.app.domain.resumen.FormatoResumen
import cl.logisticaverde.app.domain.sync.MensajesSync
import cl.logisticaverde.app.ui.navigation.BarraInferior
import cl.logisticaverde.app.ui.navigation.PestanaInferior
import cl.logisticaverde.app.ui.screens.menu.reproducirSonidoExito
import cl.logisticaverde.app.ui.theme.AzulMarino
import cl.logisticaverde.app.ui.theme.AzulMarinoSuave
import cl.logisticaverde.app.ui.theme.AzulPetroleo
import cl.logisticaverde.app.ui.theme.Blanco
import cl.logisticaverde.app.ui.theme.ErrorRojo
import cl.logisticaverde.app.ui.theme.SyncAmarillo
import cl.logisticaverde.app.ui.theme.SyncVerde
import cl.logisticaverde.app.ui.theme.VerdeAccion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado del botón "Reintentar" de una jornada rechazada (ISSUE-42). */
sealed interface EstadoReintento {
    data object Enviando : EstadoReintento
    data object Exito : EstadoReintento
    data class Error(val mensaje: String) : EstadoReintento
}

data class ReportesUiState(
    val rechazadas: List<JornadaEntity> = emptyList(),
    val ultimas: List<JornadaEntity> = emptyList(),
    val reintentos: Map<String, EstadoReintento> = emptyMap()
)

/**
 * Reportes: jornadas rechazadas arriba (siempre visibles, con "Reintentar") y las últimas 5
 * jornadas cerradas (pendientes o sincronizadas) → resumen P10.
 */
class ReportesViewModel(
    private val jornadaDao: JornadaDao,
    private val syncRepository: JornadaSyncRepository,
    private val isOnline: Flow<Boolean>
) : ViewModel() {

    private val reintentos = MutableStateFlow<Map<String, EstadoReintento>>(emptyMap())

    val uiState: StateFlow<ReportesUiState> = combine(
        jornadaDao.observeJornadasRechazadas(),
        jornadaDao.observeUltimasJornadasCerradas(MAX_JORNADAS),
        reintentos
    ) { rechazadas, ultimas, estados -> ReportesUiState(rechazadas, ultimas, estados) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportesUiState())

    /**
     * Reenvía exactamente los mismos datos (la jornada no se edita). Con internet se envía ahora;
     * sin internet vuelve a "pendiente" y se va con la próxima sincronización.
     */
    fun reintentar(idRegistro: String) {
        if (reintentos.value[idRegistro] == EstadoReintento.Enviando) return
        viewModelScope.launch {
            val online = runCatching { isOnline.first() }.getOrDefault(false)
            if (!online) {
                jornadaDao.updateEstadoSync(idRegistro, JornadaEntity.SYNC_PENDIENTE)
                return@launch
            }
            reintentos.update { it + (idRegistro to EstadoReintento.Enviando) }
            val resultado = try {
                syncRepository.sincronizarJornada(idRegistro)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
            val estado = when {
                resultado != null && resultado.error == null && resultado.successCount > 0 -> EstadoReintento.Exito
                resultado?.error != null -> EstadoReintento.Error(MensajesSync.paraLider(resultado.error))
                else -> EstadoReintento.Error("No se pudo sincronizar. Intenta más tarde.")
            }
            reintentos.update { it + (idRegistro to estado) }
        }
    }

    /** La pantalla lo llama cuando terminó de mostrar el resultado. */
    fun limpiarReintento(idRegistro: String) {
        reintentos.update { it - idRegistro }
    }

    companion object {
        const val MAX_JORNADAS = 5
    }

    class Factory(
        private val jornadaDao: JornadaDao,
        private val syncRepository: JornadaSyncRepository,
        private val isOnline: Flow<Boolean>
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ReportesViewModel(jornadaDao, syncRepository, isOnline) as T
    }
}

@Composable
fun ReportesScreen(
    uiState: ReportesUiState,
    onVerResumen: (String) -> Unit,
    onReintentar: (String) -> Unit,
    onReintentoMostrado: (String) -> Unit,
    onInicio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Mismo efecto que la P1: sonido + vibración al reintentar con éxito; el resultado se ve unos segundos
    uiState.reintentos.forEach { (id, estado) ->
        LaunchedEffect(id, estado) {
            when (estado) {
                EstadoReintento.Exito -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    reproducirSonidoExito(context)
                    delay(2_500)
                    onReintentoMostrado(id)
                }
                is EstadoReintento.Error -> {
                    delay(4_000)
                    onReintentoMostrado(id)
                }
                EstadoReintento.Enviando -> Unit
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp)
        ) {
            Text(
                "Últimas jornadas",
                style = MaterialTheme.typography.headlineMedium,
                color = AzulPetroleo,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
            Text(
                "Toca una jornada para ver su resumen.",
                style = MaterialTheme.typography.bodyMedium,
                color = AzulMarinoSuave,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            if (uiState.rechazadas.isEmpty() && uiState.ultimas.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Aún no hay jornadas cerradas.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AzulMarinoSuave,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(uiState.rechazadas, key = { "r-" + it.idRegistro }) { j ->
                        TarjetaJornada(
                            j = j,
                            reintento = uiState.reintentos[j.idRegistro],
                            onClick = { onVerResumen(j.idRegistro) },
                            onReintentar = { onReintentar(j.idRegistro) }
                        )
                    }
                    // Una rechazada que se reintenta con éxito pasa a esta lista y muestra su ✓ unos segundos
                    items(uiState.ultimas, key = { it.idRegistro }) { j ->
                        TarjetaJornada(
                            j = j,
                            reintento = uiState.reintentos[j.idRegistro],
                            onClick = { onVerResumen(j.idRegistro) },
                            onReintentar = {}
                        )
                    }
                }
            }
        }
        BarraInferior(
            seleccionada = PestanaInferior.REPORTES,
            onInicio = onInicio,
            onReportes = {},
            rechazadas = uiState.rechazadas.size
        )
    }
}

@Composable
private fun TarjetaJornada(
    j: JornadaEntity,
    reintento: EstadoReintento?,
    onClick: () -> Unit,
    onReintentar: () -> Unit
) {
    val rechazada = j.estadoSync == JornadaEntity.SYNC_ERROR
    val (textoEstado, colorEstado) = when {
        reintento is EstadoReintento.Error -> reintento.mensaje to ErrorRojo
        reintento == EstadoReintento.Exito -> "Sincronizada" to SyncVerde
        rechazada -> "Rechazada: ${j.motivoErrorSync ?: "sin detalle"}" to ErrorRojo
        j.estadoSync == JornadaEntity.SYNC_SINCRONIZADO -> "Sincronizada" to SyncVerde
        else -> "Pendiente de sincronizar" to SyncAmarillo
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Blanco,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        listOf(FormatoResumen.fecha(j.fechaJornada), j.nombrePlaya).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.titleMedium,
                        color = AzulMarino
                    )
                    Text(
                        "${j.nombreLider} - ${j.brigada} · ${FormatoResumen.kg(j.kgBasuraNormal + j.kgBasuraReciclable)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AzulMarinoSuave
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AzulMarino)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colorEstado)
                )
                Text(
                    textoEstado,
                    style = MaterialTheme.typography.labelMedium,
                    color = colorEstado,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp, end = 8.dp)
                )
                if (rechazada || reintento != null) {
                    BotonReintentar(reintento, onReintentar)
                }
            }
        }
    }
}

@Composable
private fun BotonReintentar(reintento: EstadoReintento?, onReintentar: () -> Unit) {
    AnimatedContent(
        targetState = reintento,
        transitionSpec = {
            (scaleIn(initialScale = 0.4f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) +
                fadeIn(tween(150))) togetherWith fadeOut(tween(120))
        },
        label = "reintento"
    ) { estado ->
        when (estado) {
            EstadoReintento.Enviando -> CircularProgressIndicator(
                modifier = Modifier.size(28.dp), strokeWidth = 3.dp, color = VerdeAccion
            )
            EstadoReintento.Exito -> Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(VerdeAccion),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, contentDescription = "Sincronizada", tint = Blanco, modifier = Modifier.size(22.dp))
            }
            else -> Button(
                onClick = onReintentar,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VerdeAccion),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("Reintentar", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
