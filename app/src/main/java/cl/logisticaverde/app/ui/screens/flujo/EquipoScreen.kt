package cl.logisticaverde.app.ui.screens.flujo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import cl.logisticaverde.app.R
import cl.logisticaverde.app.data.local.entity.PlayaCatalogoEntity

/**
 * Pantalla 2:
 * - Card 1: nombre del líder de brigada escrito a mano (mínimo 3 caracteres), selector de brigada
 *   y selector de playa (catálogo, ISSUE-32).
 * - Card 2: voluntarios escritos a mano con [ + Agregar ], contador de integrantes y lista con opción de eliminar.
 * - Cada cambio se guarda en Room al instante (RNF-01); los errores se muestran bajo cada campo.
 * - Botón Siguiente en la parte inferior.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EquipoScreen(
    uiState: EquipoSeleccionUiState,
    brigadas: List<String>,
    playas: List<PlayaCatalogoEntity>,
    onSelectBrigada: (String) -> Unit,
    onSelectPlaya: (String) -> Unit,
    onNombreLiderChange: (String) -> Unit,
    onAgregarVoluntario: (String) -> Boolean,
    onEliminarVoluntario: (String) -> Unit,
    onNuevoVoluntarioEditado: () -> Unit,
    onSiguienteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var nuevoVoluntario by rememberSaveable { mutableStateOf("") }
    var playasExpandido by remember { mutableStateOf(false) }
    val playaSeleccionada = playas.find { it.idPlaya == uiState.selectedPlayaId }
    val agregar = {
        if (onAgregarVoluntario(nuevoVoluntario)) nuevoVoluntario = ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Conformar Brigada",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // CARD 1: Líder de Brigada, brigada y playa
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "1. Líder de Brigada",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = uiState.nombreLider,
                        onValueChange = onNombreLiderChange,
                        label = { Text("Nombre del líder de brigada") },
                        singleLine = true,
                        isError = uiState.errorNombreLider != null,
                        supportingText = uiState.errorNombreLider?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Selector rápido de letra de brigada
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        brigadas.forEach { brigada ->
                            FilterChip(
                                selected = uiState.selectedBrigada == brigada,
                                onClick = { onSelectBrigada(brigada) },
                                label = { Text(text = brigada) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // Playa de la jornada (obligatoria): define los sectores de la Pantalla 3
                    ExposedDropdownMenuBox(
                        expanded = playasExpandido,
                        onExpandedChange = { if (playas.isNotEmpty()) playasExpandido = !playasExpandido },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = playaSeleccionada?.nombrePlaya.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("Playa") },
                            placeholder = { Text("Selecciona la playa") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = playasExpandido) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = playasExpandido,
                            onDismissRequest = { playasExpandido = false }
                        ) {
                            playas.forEach { playa ->
                                DropdownMenuItem(
                                    text = { Text(playa.nombrePlaya) },
                                    onClick = {
                                        onSelectPlaya(playa.idPlaya)
                                        playasExpandido = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // CARD 2: Voluntarios escritos a mano
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "2. Voluntarios",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = uiState.resumenIntegrantes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = nuevoVoluntario,
                            onValueChange = {
                                nuevoVoluntario = it
                                onNuevoVoluntarioEditado()
                            },
                            label = { Text("Nombre del voluntario") },
                            singleLine = true,
                            isError = uiState.errorVoluntario != null,
                            supportingText = uiState.errorVoluntario?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { agregar() }),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = agregar,
                            enabled = nuevoVoluntario.isNotBlank()
                        ) {
                            Text("+ Agregar")
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider()
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.voluntarios, key = { it }) { voluntario ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = voluntario,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onEliminarVoluntario(voluntario) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Eliminar $voluntario")
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Botón Siguiente
        Button(
            onClick = onSiguienteClick,
            enabled = uiState.esEquipoValido,
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
