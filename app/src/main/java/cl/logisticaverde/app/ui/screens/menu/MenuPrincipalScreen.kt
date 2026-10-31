package cl.logisticaverde.app.ui.screens.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import cl.logisticaverde.app.data.local.entity.JornadaEntity
import cl.logisticaverde.app.domain.resumen.FormatoResumen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cl.logisticaverde.app.R
import cl.logisticaverde.app.ui.theme.SyncGrayDisabled
import cl.logisticaverde.app.ui.theme.SyncGreenReadyOnline
import cl.logisticaverde.app.ui.theme.SyncYellowPendingOffline

@Composable
fun MenuPrincipalScreen(
    uiState: MenuPrincipalUiState,
    onIniciarJornadaClick: () -> Unit,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier,
    // ISSUE-25: hasta 5 jornadas cerradas (pendientes o sincronizadas) para volver a ver su resumen
    ultimasJornadas: List<JornadaEntity> = emptyList(),
    onVerResumen: (idRegistro: String) -> Unit = {},
    // ISSUE-36: la fecha y las horas de la jornada salen del reloj del teléfono
    horaAutomaticaDesactivada: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Arriba: Título principal "Deja tu Playa Limpia" (la playa se elige al iniciar la jornada)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.main_header_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            if (horaAutomaticaDesactivada) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "La hora automática del teléfono está desactivada. Actívala en Ajustes " +
                            "(Fecha y hora) para que la fecha y las horas de la jornada queden bien registradas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 2. Centro: Botón de Iniciar Jornada (o Reanudar si hay una jornada EN_CURSO en Room)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val jornadaActiva = uiState.jornadaEnCurso
            Button(
                onClick = onIniciarJornadaClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Text(
                    text = if (jornadaActiva != null) {
                        stringResource(id = R.string.btn_resume_shift)
                    } else {
                        stringResource(id = R.string.btn_start_shift)
                    },
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (jornadaActiva != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val detalle = listOf(
                    jornadaActiva.nombreLider.ifBlank { "Sin líder aún" },
                    jornadaActiva.brigada,
                    jornadaActiva.nombrePlaya
                ).filter { it.isNotBlank() }.joinToString(" · ")
                Text(
                    text = "Jornada en curso: $detalle",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Paso pendiente: ${nombrePaso(jornadaActiva.pantallaActual)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            if (ultimasJornadas.isNotEmpty()) {
                var mostrarLista by rememberSaveable { mutableStateOf(false) }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { mostrarLista = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Ver últimos resúmenes")
                }
                if (mostrarLista) {
                    UltimosResumenesDialog(
                        jornadas = ultimasJornadas,
                        onElegir = { id ->
                            mostrarLista = false
                            onVerResumen(id)
                        },
                        onCerrar = { mostrarLista = false }
                    )
                }
            }
        }

        // 3. Abajo: Botón Sincronizar Tricolor (Gris / Amarillo / Verde)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val (btnColor, textColor, isClickable, statusLabel) = when (uiState.syncButtonState) {
                SyncButtonState.GRIS_SIN_DATOS -> SyncVisualConfig(
                    containerColor = SyncGrayDisabled,
                    contentColor = Color.White,
                    enabled = false,
                    label = "Sincronizar (Sin datos pendientes)"
                )
                SyncButtonState.AMARILLO_SIN_INTERNET -> SyncVisualConfig(
                    containerColor = SyncYellowPendingOffline,
                    contentColor = Color.Black,
                    enabled = false,
                    label = "Sincronizar (${uiState.pendingSyncCount} pendiente/s - Sin internet)"
                )
                SyncButtonState.VERDE_LISTO_PARA_SYNC -> SyncVisualConfig(
                    containerColor = SyncGreenReadyOnline,
                    contentColor = Color.White,
                    enabled = true,
                    label = "Sincronizar (${uiState.pendingSyncCount} lista/s para enviar)"
                )
                SyncButtonState.SINCRONIZANDO -> SyncVisualConfig(
                    containerColor = SyncGreenReadyOnline,
                    contentColor = Color.White,
                    enabled = false,
                    label = "Sincronizando..."
                )
            }

            if (!uiState.syncFeedbackMessage.isNullOrBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = uiState.syncFeedbackMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Button(
                onClick = onSyncClick,
                enabled = isClickable,
                colors = ButtonDefaults.buttonColors(
                    containerColor = btnColor,
                    contentColor = textColor,
                    disabledContainerColor = btnColor,
                    disabledContentColor = textColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun UltimosResumenesDialog(
    jornadas: List<JornadaEntity>,
    onElegir: (String) -> Unit,
    onCerrar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Últimas jornadas") },
        text = {
            Column {
                jornadas.forEachIndexed { i, j ->
                    if (i > 0) HorizontalDivider()
                    ListItem(
                        headlineContent = {
                            Text(listOf(FormatoResumen.fecha(j.fechaJornada), j.nombrePlaya)
                                .filter { it.isNotBlank() }.joinToString(" · "))
                        },
                        supportingContent = {
                            val estado = if (j.estadoSync == JornadaEntity.SYNC_SINCRONIZADO) {
                                "Sincronizada"
                            } else {
                                "Pendiente de sincronizar"
                            }
                            Text("${j.brigada} · ${j.nombreLider} · $estado")
                        },
                        modifier = Modifier.clickable { onElegir(j.idRegistro) }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } }
    )
}

/** Nombre del paso para "Reanudar Jornada" (ya no se muestra "Pantalla N de 10"). */
private fun nombrePaso(pantalla: Int): String = when (pantalla) {
    2 -> "Conformar brigada"
    3 -> "Sector y foto inicial"
    4 -> "Herramientas"
    5 -> "Limpieza y conteo"
    6 -> "Fotos de evidencia final"
    7 -> "Estado de herramientas"
    8 -> "Pesaje"
    9 -> "Entrega de sacos"
    else -> "Conformar brigada"
}

private data class SyncVisualConfig(
    val containerColor: Color,
    val contentColor: Color,
    val enabled: Boolean,
    val label: String
)
