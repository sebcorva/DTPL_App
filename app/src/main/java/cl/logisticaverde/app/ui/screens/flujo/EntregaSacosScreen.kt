package cl.logisticaverde.app.ui.screens.flujo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cl.logisticaverde.app.R
import cl.logisticaverde.app.data.local.entity.JornadaEntity

/**
 * Pantalla 9 (Nueva - Entrega de Sacos antes de terminar la jornada):
 * - Card 1: Opciones para Basura Normal:
 *     1. Entregado a camión DIMAO
 *     2. Pendiente de retiro
 *     3. No aplicable
 *   + Campo opcional de ingresar N° Guía de Despacho.
 * - Card 2: Opciones para Basura Reciclable:
 *     1. Entregado a Punto Limpio Municipal
 *     2. Entregado a Reciclador de Base
 *     3. Pendiente de entrega
 *     4. No aplicable
 * - Botón Guardar: Guarda localmente en Room Database y vuelve a Inicio (Pantalla 1).
 */
@Composable
fun EntregaSacosScreen(
    uiState: EquipoSeleccionUiState,
    onSelectDestinoNormal: (String) -> Unit,
    onNroGuiaDespachoChange: (String) -> Unit,
    onSelectDestinoReciclable: (String) -> Unit,
    onGuardarJornadaClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                text = "Entrega y Destino de Sacos",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            // CARD 1: Entrega de Basura Normal + N° Guía de Despacho Opcional
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Text(
                        text = "1. Destino de Basura Normal (${uiState.kgBasuraNormalInput.ifBlank { "0" }} kg)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider()

                    JornadaEntity.OPCIONES_DESTINO_NORMAL.forEach { opcion ->
                        val isAllowed = opcion in uiState.opcionesPermitidasBasuraNormal
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = isAllowed) { onSelectDestinoNormal(opcion) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = uiState.destinoBasuraNormal == opcion,
                                enabled = isAllowed,
                                onClick = { onSelectDestinoNormal(opcion) }
                            )
                            Text(
                                text = opcion,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isAllowed) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                },
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }

                    if (uiState.mostrarCampoGuiaDespacho) {
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = uiState.nroGuiaDespachoInput,
                            onValueChange = onNroGuiaDespachoChange,
                            label = { Text("N° Guía de Despacho (Opcional)") },
                            placeholder = { Text("Ej: GD-2026-0041") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // CARD 2: Entrega de Basura Reciclable
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Text(
                        text = "2. Destino de Basura Reciclable (${uiState.kgBasuraReciclableInput.ifBlank { "0" }} kg)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider()

                    JornadaEntity.OPCIONES_DESTINO_RECICLABLE.forEach { opcion ->
                        val isAllowed = opcion in uiState.opcionesPermitidasBasuraReciclable
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = isAllowed) { onSelectDestinoReciclable(opcion) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = uiState.destinoBasuraReciclable == opcion,
                                enabled = isAllowed,
                                onClick = { onSelectDestinoReciclable(opcion) }
                            )
                            Text(
                                text = opcion,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isAllowed) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                },
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Botón Guardar y volver a Inicio (Pantalla 1)
        Button(
            onClick = onGuardarJornadaClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = stringResource(id = R.string.btn_save),
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
