package cl.logisticaverde.app.ui.screens.flujo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cl.logisticaverde.app.R
import cl.logisticaverde.app.data.local.entity.ParticipanteJornadaEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla 8:
 * - Muestra: Km recorridos, Hora Inicio - Hora Término y Nombre del Líder de Brigada.
 * - Selector desplegable obligatorio: Encargado de Pesaje (participantes de la jornada u "Otra persona" escrita a mano).
 * - Inputs: Cantidad de sacos, Kg basura normal y Kg basura reciclable (guardada en sacos independientes).
 * - Valida Ley N° 20.949 (> 0 kg y <= 25 kg por saco).
 * - Botón Siguiente (avanza a la Pantalla 9 de Entrega de Sacos).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PesajeResumenScreen(
    uiState: EquipoSeleccionUiState,
    participantes: List<ParticipanteJornadaEntity>,
    onSelectEncargadoPesaje: (String) -> Unit,
    onSelectEncargadoExterno: () -> Unit,
    onEncargadoExternoChange: (String) -> Unit,
    onCantidadSacosChange: (String) -> Unit,
    onKgNormalChange: (String) -> Unit,
    onKgReciclableChange: (String) -> Unit,
    onSiguienteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val horaInicioStr = if (uiState.horaInicioMs > 0L) timeFormat.format(Date(uiState.horaInicioMs)) else "--:--"
    val horaFinStr = if (uiState.horaFinMs > 0L) timeFormat.format(Date(uiState.horaFinMs)) else "--:--"

    val kgTotales = uiState.kgTotalesVal
    val canProceed = uiState.esPesajeValido

    var encargadoDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Resumen y Pesaje en Acopio",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            // CARD 1: Resumen de Recorrido (Km recorridos, Hora Inicio-Término, Líder)
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Resumen del Recorrido",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    HorizontalDivider()
                    SummaryRow(
                        label = "Líder de Brigada:",
                        value = "${uiState.nombreLider.ifBlank { "Sin asignar" }} (${uiState.selectedBrigada})"
                    )
                    SummaryRow(
                        label = "Hora Inicio - Hora Término:",
                        value = "$horaInicioStr - $horaFinStr"
                    )
                    SummaryRow(
                        label = "Kilómetros Recorridos:",
                        value = String.format(Locale.getDefault(), "%.2f km", uiState.kmRecorridos)
                    )
                }
            }

            // CARD 2: Formulario de Pesaje y Encargado de Pesaje
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Registro en Punto de Acopio y Pesaje",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Selector obligatorio de Encargado de Pesaje
                    ExposedDropdownMenuBox(
                        expanded = encargadoDropdownExpanded,
                        onExpandedChange = { encargadoDropdownExpanded = !encargadoDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = when {
                                uiState.encargadoEsExterno -> "Otra persona"
                                else -> uiState.encargadoPesaje.ifBlank { "Seleccionar encargado de pesaje..." }
                            },
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("Encargado de Pesaje (Obligatorio)") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = encargadoDropdownExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = encargadoDropdownExpanded,
                            onDismissRequest = { encargadoDropdownExpanded = false }
                        ) {
                            participantes.forEach { participante ->
                                val rolTag = if (participante.rol == ParticipanteJornadaEntity.ROL_LIDER) "Líder" else "Voluntario"
                                DropdownMenuItem(
                                    text = {
                                        Text("${participante.nombre} ($rolTag)")
                                    },
                                    onClick = {
                                        onSelectEncargadoPesaje(participante.nombre)
                                        encargadoDropdownExpanded = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Otra persona (escribir nombre)") },
                                onClick = {
                                    onSelectEncargadoExterno()
                                    encargadoDropdownExpanded = false
                                }
                            )
                        }
                    }

                    // Encargado externo a la brigada (ej. alguien del punto de acopio)
                    if (uiState.encargadoEsExterno) {
                        OutlinedTextField(
                            value = uiState.encargadoPesaje,
                            onValueChange = onEncargadoExternoChange,
                            label = { Text("Nombre de quien pesa") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = uiState.cantidadSacosInput,
                        onValueChange = onCantidadSacosChange,
                        label = { Text("Cantidad de sacos llenados/ocupados") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = uiState.kgBasuraNormalInput,
                        onValueChange = onKgNormalChange,
                        label = { Text("Kg Basura Normal (No reciclable)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = uiState.kgBasuraReciclableInput,
                        onValueChange = onKgReciclableChange,
                        label = { Text("Kg Basura Reciclable (Sacos independientes)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider()

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Total Extraído:",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format(Locale.getDefault(), "%.2f kg", kgTotales),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Promedio por Saco (Máx. 25 kg Ley 20.949):",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(Locale.getDefault(), "%.2f kg/saco", uiState.kgPromedioPorSaco),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (uiState.excedeLimiteLeySaco) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!uiState.pesajeErrorMessage.isNullOrBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = uiState.pesajeErrorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Botón Siguiente hacia Pantalla 9 (Entrega de Sacos)
        Button(
            onClick = onSiguienteClick,
            enabled = canProceed,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text(
                text = stringResource(id = R.string.btn_next),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
