package cl.logisticaverde.app.ui.screens.flujo

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.logisticaverde.app.R
import cl.logisticaverde.app.data.local.entity.PlayaCatalogoEntity
import cl.logisticaverde.app.ui.theme.AzulMarino
import cl.logisticaverde.app.ui.theme.AzulMarinoSuave
import cl.logisticaverde.app.ui.theme.AzulPetroleo
import cl.logisticaverde.app.ui.theme.AzulPetroleoClaro
import cl.logisticaverde.app.ui.theme.Blanco
import cl.logisticaverde.app.ui.theme.BordeSuave
import cl.logisticaverde.app.ui.theme.SyncGrisFondo
import cl.logisticaverde.app.ui.theme.SyncGrisIcono
import cl.logisticaverde.app.ui.theme.VerdeAccion
import cl.logisticaverde.app.ui.theme.VerdeAccionOscuro

private val FormaTarjeta = RoundedCornerShape(24.dp)
private val FormaCampo = RoundedCornerShape(12.dp)

/**
 * Pantalla 2 "Conformar Brigada" (diseño ISSUE-44):
 * - Tarjeta 1: nombre del líder (mínimo 3 caracteres), brigadas en grilla 2×2 y playa (desplegable, ISSUE-32).
 * - Tarjeta 2: voluntarios escritos a mano con botón redondo [+], ícono de voluntarios con globo de cantidad y lista con opción de eliminar.
 * - Cada cambio se guarda en Room al instante (RNF-01); los errores se muestran bajo cada campo.
 * - Botón Siguiente en la parte inferior.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
        if (nuevoVoluntario.isNotBlank() && onAgregarVoluntario(nuevoVoluntario)) nuevoVoluntario = ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Conformar Brigada",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = AzulMarino
        )
        Spacer(Modifier.height(12.dp))

        // TARJETA 1: líder, brigada y playa
        Tarjeta {
            Subtitulo("Líder de brigada")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.nombreLider,
                onValueChange = onNombreLiderChange,
                label = { Text("Nombre del líder de brigada") },
                singleLine = true,
                isError = uiState.errorNombreLider != null,
                supportingText = uiState.errorNombreLider?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                shape = FormaCampo,
                colors = coloresCampo(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            GrillaBrigadas(
                brigadas = brigadas,
                seleccionada = uiState.selectedBrigada,
                onSelect = onSelectBrigada
            )
            Spacer(Modifier.height(16.dp))
            // Playa de la jornada (obligatoria): define los sectores de la Pantalla 3
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExposedDropdownMenuBox(
                    expanded = playasExpandido,
                    onExpandedChange = { if (playas.isNotEmpty()) playasExpandido = !playasExpandido },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = playaSeleccionada?.nombrePlaya.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        placeholder = { Text("Elige una playa") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = playasExpandido) },
                        shape = FormaCampo,
                        colors = coloresCampo(),
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
                Spacer(Modifier.width(12.dp))
                // Decorativo (decisión 07-11-2026): no hace nada al tocarlo
                Icon(
                    imageVector = Icons.Outlined.Map,
                    contentDescription = null,
                    tint = VerdeAccionOscuro,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // TARJETA 2: voluntarios escritos a mano
        Tarjeta(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Subtitulo("Voluntarios", modifier = Modifier.weight(1f))
                val cantidad = uiState.voluntarios.size
                BadgedBox(
                    badge = {
                        Badge(containerColor = VerdeAccionOscuro, contentColor = Blanco) {
                            Text(text = cantidad.toString(), fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier.semantics {
                        contentDescription = if (cantidad == 1) "1 voluntario" else "$cantidad voluntarios"
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Groups,
                        contentDescription = null,
                        tint = AzulMarino,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top) {
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
                    shape = FormaCampo,
                    colors = coloresCampo(),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                // padding superior = espacio que OutlinedTextField reserva para la etiqueta
                FilledIconButton(
                    onClick = agregar,
                    enabled = nuevoVoluntario.isNotBlank(),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = VerdeAccion,
                        contentColor = Blanco,
                        disabledContainerColor = SyncGrisFondo,
                        disabledContentColor = SyncGrisIcono
                    ),
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(58.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Agregar voluntario", modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = BordeSuave)
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (uiState.voluntarios.isEmpty()) {
                    item {
                        Text(
                            text = "Aún no hay voluntarios",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AzulMarinoSuave,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        )
                    }
                }
                items(uiState.voluntarios, key = { it }) { voluntario ->
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp)
                        ) {
                            Text(
                                text = voluntario,
                                style = MaterialTheme.typography.bodyLarge,
                                color = AzulMarino,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onEliminarVoluntario(voluntario) }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Eliminar $voluntario",
                                    tint = AzulMarinoSuave
                                )
                            }
                        }
                        HorizontalDivider(color = BordeSuave)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Botón Siguiente: píldora verde, como "Iniciar jornada" (decisión 07-11-2026)
        Button(
            onClick = onSiguienteClick,
            enabled = uiState.esEquipoValido,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = VerdeAccion, contentColor = Blanco),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(id = R.string.btn_next),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.align(Alignment.Center)
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(30.dp)
                )
            }
        }
    }
}

@Composable
private fun Tarjeta(modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    Surface(
        shape = FormaTarjeta,
        color = Blanco,
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, FormaTarjeta)
    ) {
        Column(modifier = Modifier.padding(20.dp)) { contenido() }
    }
}

@Composable
private fun Subtitulo(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto,
        style = MaterialTheme.typography.titleLarge,
        fontSize = 24.sp,
        fontWeight = FontWeight.ExtraBold,
        color = VerdeAccionOscuro,
        modifier = modifier
    )
}

/** Brigadas en grilla de 2 columnas; si la cantidad es impar, la última fila queda con un hueco. */
@Composable
private fun GrillaBrigadas(brigadas: List<String>, seleccionada: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        brigadas.chunked(2).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                fila.forEach { brigada ->
                    val activa = brigada == seleccionada
                    Surface(
                        onClick = { onSelect(brigada) },
                        selected = activa,
                        shape = FormaCampo,
                        color = if (activa) VerdeAccion else AzulPetroleoClaro,
                        contentColor = if (activa) Blanco else AzulMarino,
                        border = if (activa) null else BorderStroke(1.dp, BordeSuave),
                        shadowElevation = if (activa) 2.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = brigada,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (fila.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun coloresCampo(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Blanco,
    unfocusedContainerColor = Blanco,
    focusedBorderColor = AzulPetroleo,
    unfocusedBorderColor = BordeSuave,
    focusedLabelColor = AzulPetroleo,
    unfocusedLabelColor = AzulMarinoSuave,
    focusedTextColor = AzulMarino,
    unfocusedTextColor = AzulMarino,
    focusedPlaceholderColor = AzulMarinoSuave,
    unfocusedPlaceholderColor = AzulMarinoSuave
)
