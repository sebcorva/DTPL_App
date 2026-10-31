package cl.logisticaverde.app.ui.screens.reportes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cl.logisticaverde.app.data.local.db.JornadaDao
import cl.logisticaverde.app.data.local.entity.JornadaEntity
import cl.logisticaverde.app.domain.resumen.FormatoResumen
import cl.logisticaverde.app.ui.navigation.BarraInferior
import cl.logisticaverde.app.ui.navigation.PestanaInferior
import cl.logisticaverde.app.ui.theme.AzulMarino
import cl.logisticaverde.app.ui.theme.AzulMarinoSuave
import cl.logisticaverde.app.ui.theme.AzulPetroleo
import cl.logisticaverde.app.ui.theme.Blanco
import cl.logisticaverde.app.ui.theme.SyncAmarillo
import cl.logisticaverde.app.ui.theme.SyncVerde
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Reportes: últimas 5 jornadas cerradas del teléfono (pendientes o sincronizadas) → resumen P10. */
class ReportesViewModel(jornadaDao: JornadaDao) : ViewModel() {
    val jornadas: StateFlow<List<JornadaEntity>> = jornadaDao
        .observeUltimasJornadasCerradas(MAX_JORNADAS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        const val MAX_JORNADAS = 5
    }

    class Factory(private val jornadaDao: JornadaDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ReportesViewModel(jornadaDao) as T
    }
}

@Composable
fun ReportesScreen(
    jornadas: List<JornadaEntity>,
    onVerResumen: (String) -> Unit,
    onInicio: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                "Las últimas 5 jornadas cerradas en este teléfono. Toca una para ver su resumen.",
                style = MaterialTheme.typography.bodyMedium,
                color = AzulMarinoSuave,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            if (jornadas.isEmpty()) {
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
                    items(jornadas, key = { it.idRegistro }) { j -> TarjetaJornada(j) { onVerResumen(j.idRegistro) } }
                }
            }
        }
        BarraInferior(
            seleccionada = PestanaInferior.REPORTES,
            onInicio = onInicio,
            onReportes = {}
        )
    }
}

@Composable
private fun TarjetaJornada(j: JornadaEntity, onClick: () -> Unit) {
    val sincronizada = j.estadoSync == JornadaEntity.SYNC_SINCRONIZADO
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Blanco,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
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
                Text(
                    if (sincronizada) "Sincronizada" else "Pendiente de sincronizar",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (sincronizada) SyncVerde else SyncAmarillo
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AzulMarino)
        }
    }
}
