package cl.logisticaverde.app.ui.screens.flujo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Recycling
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.logisticaverde.app.R
import cl.logisticaverde.app.data.local.entity.ParticipanteJornadaEntity
import cl.logisticaverde.app.ui.theme.AzulMarino
import cl.logisticaverde.app.ui.theme.AzulMarinoSuave
import cl.logisticaverde.app.ui.theme.AzulPetroleo
import cl.logisticaverde.app.ui.theme.Blanco
import cl.logisticaverde.app.ui.theme.BordeSuave
import cl.logisticaverde.app.ui.theme.RojoCalido
import cl.logisticaverde.app.ui.theme.VerdeAccion
import cl.logisticaverde.app.ui.theme.VerdeAccionOscuro
import cl.logisticaverde.app.ui.theme.VerdeMenta
import cl.logisticaverde.app.ui.theme.AzulPetroleoClaro
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import cl.logisticaverde.app.domain.reglas.TopesJornada
import cl.logisticaverde.app.ui.theme.SyncGrisFondo
import cl.logisticaverde.app.ui.theme.SyncGrisIcono
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Pantalla 8 "Resumen y pesaje" (diseño ISSUE-52):
 * - Tarjeta "Resumen del recorrido": líder, horario, tiempo transcurrido y distancia.
 * - Tarjeta "Pesaje en acopio": encargado de pesaje (participante u "Otra persona"), sacos y kilos
 *   con su ícono, y el bloque de total extraído / promedio por saco frente a la Ley N° 20.949 (≤ 25 kg).
 * - Botón Siguiente (Pantalla 9, entrega de sacos).
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
    val formatoHora = remember { SimpleDateFormat("HH:mm", LocaleChile) }
    val horaInicio = if (uiState.horaInicioMs > 0L) formatoHora.format(Date(uiState.horaInicioMs)) else "--:--"
    val horaFin = if (uiState.horaFinMs > 0L) formatoHora.format(Date(uiState.horaFinMs)) else "--:--"
    val tiempoTranscurrido = if (uiState.horaInicioMs > 0L && uiState.horaFinMs > 0L) {
        formatoDuracion(uiState.horaFinMs - uiState.horaInicioMs)
    } else {
        "--:--:--"
    }

    var encargadoDesplegado by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            TituloPantallaFlujo("Resumen y pesaje")
            Spacer(Modifier.height(12.dp))

            // TARJETA 1: resumen del recorrido
            TarjetaFlujo {
                SubtituloFlujo("Resumen del recorrido")
                Spacer(Modifier.height(12.dp))
                // Grilla 2×2 compacta (ajuste del 08-11-2026: antes una fila por dato)
                Row(modifier = Modifier.fillMaxWidth()) {
                    DatoResumen(
                        icono = Icons.Outlined.Person,
                        etiqueta = "Líder · ${uiState.selectedBrigada}",
                        valor = uiState.nombreLider.ifBlank { "Sin asignar" },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    DatoResumen(
                        icono = Icons.Outlined.Schedule,
                        etiqueta = "Horario",
                        valor = "$horaInicio – $horaFin",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    DatoResumen(
                        icono = Icons.Outlined.Timer,
                        etiqueta = "Tiempo transcurrido",
                        valor = tiempoTranscurrido,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    DatoResumen(
                        icono = Icons.Outlined.LocationOn,
                        etiqueta = "Distancia",
                        valor = String.format(LocaleChile, "%.2f km", uiState.kmRecorridos),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // TARJETA 2: pesaje en acopio
            TarjetaFlujo {
                SubtituloFlujo("Pesaje en acopio")
                Spacer(Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = encargadoDesplegado,
                    onExpandedChange = { encargadoDesplegado = !encargadoDesplegado },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = if (uiState.encargadoEsExterno) "Otra persona" else uiState.encargadoPesaje,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        placeholder = { Text("Elige al encargado de pesaje") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = encargadoDesplegado) },
                        shape = FormaCampoFlujo,
                        colors = coloresCampoFlujo(),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = encargadoDesplegado,
                        onDismissRequest = { encargadoDesplegado = false }
                    ) {
                        participantes.forEach { participante ->
                            val rol = if (participante.rol == ParticipanteJornadaEntity.ROL_LIDER) "Líder" else "Voluntario"
                            DropdownMenuItem(
                                text = { Text("${participante.nombre} ($rol)") },
                                onClick = {
                                    onSelectEncargadoPesaje(participante.nombre)
                                    encargadoDesplegado = false
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Otra persona (escribir nombre)") },
                            onClick = {
                                onSelectEncargadoExterno()
                                encargadoDesplegado = false
                            }
                        )
                    }
                }

                // Encargado externo a la brigada (ej. alguien del punto de acopio)
                if (uiState.encargadoEsExterno) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.encargadoPesaje,
                        onValueChange = onEncargadoExternoChange,
                        label = { Text("Nombre de quien pesa") },
                        isError = uiState.errorEncargadoExterno != null,
                        supportingText = uiState.errorEncargadoExterno?.let { mensaje -> { Text(mensaje) } },
                        singleLine = true,
                        shape = FormaCampoFlujo,
                        colors = coloresCampoFlujo(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(12.dp))
                // Sacos con contador [–] [+], sin teclado (ajuste del 08-11-2026)
                CampoContadorSacos(
                    cantidad = uiState.cantidadSacosVal,
                    onCambio = { onCantidadSacosChange(it.toString()) }
                )
                Spacer(Modifier.height(12.dp))
                CampoPesaje(
                    valor = uiState.kgBasuraNormalInput,
                    onCambio = onKgNormalChange,
                    etiqueta = "Kg basura normal",
                    sufijo = "kg",
                    tipoTeclado = KeyboardType.Decimal,
                    icono = rememberVectorPainter(Icons.Outlined.Delete),
                    colorIcono = AzulPetroleo,
                    fondoIcono = AzulPetroleoClaro
                )
                Spacer(Modifier.height(12.dp))
                CampoPesaje(
                    valor = uiState.kgBasuraReciclableInput,
                    onCambio = onKgReciclableChange,
                    etiqueta = "Kg basura reciclable",
                    sufijo = "kg",
                    tipoTeclado = KeyboardType.Decimal,
                    icono = rememberVectorPainter(Icons.Outlined.Recycling),
                    colorIcono = VerdeAccion
                )

                Spacer(Modifier.height(16.dp))
                BloqueTotales(
                    kgTotales = uiState.kgTotalesVal,
                    promedio = uiState.kgPromedioPorSaco,
                    excede = uiState.excedeLimiteLeySaco
                )

                uiState.pesajeErrorMessage?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(12.dp))
                    MensajeFlujo(texto = it, esError = true)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(12.dp))
        BotonSiguienteFlujo(enabled = uiState.esPesajeValido, onClick = onSiguienteClick)
    }
}

/** Dato del resumen en la grilla 2×2: ícono en círculo menta, etiqueta chica y valor en negrita (decisión 2). */
@Composable
private fun DatoResumen(icono: ImageVector, etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(VerdeMenta)
        ) {
            Icon(icono, contentDescription = null, tint = VerdeAccionOscuro, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                text = etiqueta,
                style = MaterialTheme.typography.labelMedium,
                color = AzulMarinoSuave,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = valor,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.ExtraBold,
                color = AzulMarino,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Campo numérico del pesaje con su ícono a la derecha, fuera del campo (decisión 4). */
@Composable
private fun CampoPesaje(
    valor: String,
    onCambio: (String) -> Unit,
    etiqueta: String,
    sufijo: String,
    tipoTeclado: KeyboardType,
    icono: Painter,
    colorIcono: Color,
    fondoIcono: Color = VerdeMenta
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = valor,
            onValueChange = onCambio,
            label = { Text(etiqueta) },
            suffix = { Text(sufijo, color = AzulMarinoSuave) },
            singleLine = true,
            shape = FormaCampoFlujo,
            colors = coloresCampoFlujo(),
            keyboardOptions = KeyboardOptions(keyboardType = tipoTeclado),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        IconoCampo(icono, colorIcono, fondoIcono)
    }
}

/** Ícono en círculo a la derecha de un campo, fuera de él (decisión 4). */
@Composable
private fun IconoCampo(icono: Painter, color: Color, fondo: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(top = 8.dp) // alinea con la caja del campo (la etiqueta flota arriba)
            .size(48.dp)
            .clip(CircleShape)
            .background(fondo)
    ) {
        Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
    }
}

/**
 * Cantidad de sacos con [–] y [+] (0 a [TopesJornada.MAX_SACOS]), sin abrir el teclado.
 * Mismo recuadro y etiqueta que los demás campos; mantener presionado repite el paso.
 */
@Composable
private fun CampoContadorSacos(cantidad: Int, onCambio: (Int) -> Unit) {
    val cantidadActual by rememberUpdatedState(cantidad)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = cantidad.toString(),
            onValueChange = {},
            readOnly = true,
            // Deshabilitado (con los colores normales): el número no se selecciona ni abre el teclado
            enabled = false,
            singleLine = true,
            label = { Text("Cantidad de sacos") },
            textStyle = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            ),
            leadingIcon = {
                BotonPaso(
                    icono = Icons.Filled.Remove,
                    descripcion = "Quitar un saco",
                    principal = false,
                    habilitado = cantidad > 0,
                    onPaso = { if (cantidadActual > 0) onCambio(cantidadActual - 1) }
                )
            },
            trailingIcon = {
                BotonPaso(
                    icono = Icons.Filled.Add,
                    descripcion = "Agregar un saco",
                    principal = true,
                    habilitado = cantidad < TopesJornada.MAX_SACOS,
                    onPaso = { if (cantidadActual < TopesJornada.MAX_SACOS) onCambio(cantidadActual + 1) }
                )
            },
            shape = FormaCampoFlujo,
            colors = coloresCampoFlujo().copy(
                disabledTextColor = AzulMarino,
                disabledLabelColor = AzulMarinoSuave,
                disabledIndicatorColor = BordeSuave
            ),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        IconoCampo(painterResource(R.drawable.ic_saco), VerdeAccion, VerdeMenta)
    }
}

/**
 * Botón redondo de paso: [–] blanco con borde o [+] verde, como en la P5.
 * Un toque da un paso; mantenerlo presionado repite (tras 400 ms, cada 90 ms).
 */
@Composable
private fun BotonPaso(
    icono: ImageVector,
    descripcion: String,
    principal: Boolean,
    habilitado: Boolean,
    onPaso: () -> Unit
) {
    val pasoActual by rememberUpdatedState(onPaso)
    val habilitadoActual by rememberUpdatedState(habilitado)
    val alcance = rememberCoroutineScope()
    val fondo = when {
        !habilitado -> SyncGrisFondo
        principal -> VerdeAccion
        else -> Blanco
    }
    val contenido = when {
        !habilitado -> SyncGrisIcono
        principal -> Blanco
        else -> AzulMarino
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(38.dp)
            .clip(CircleShape)
            .background(fondo)
            .then(if (!principal && habilitado) Modifier.border(1.dp, BordeSuave, CircleShape) else Modifier)
            .semantics { contentDescription = descripcion; role = Role.Button }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    if (!habilitadoActual) return@detectTapGestures
                    pasoActual()
                    val repetir = alcance.launch {
                        delay(400)
                        while (habilitadoActual) {
                            pasoActual()
                            delay(90)
                        }
                    }
                    tryAwaitRelease()
                    repetir.cancel()
                })
            }
    ) {
        Icon(icono, contentDescription = null, tint = contenido, modifier = Modifier.size(22.dp))
    }
}

/** Total extraído y promedio por saco, con la barra frente al máximo de la Ley 20.949 (decisión 5). */
@Composable
private fun BloqueTotales(kgTotales: Float, promedio: Float, excede: Boolean) {
    val colorPromedio = if (excede) RojoCalido else AzulMarino
    Surface(shape = RoundedCornerShape(18.dp), color = VerdeMenta, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Total extraído", style = MaterialTheme.typography.labelLarge, color = AzulMarinoSuave)
                    Text(
                        text = "${formatoKg(kgTotales)} kg",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AzulMarino
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .width(1.dp)
                        .height(48.dp)
                        .background(VerdeAccion.copy(alpha = 0.3f))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Promedio por saco", style = MaterialTheme.typography.labelLarge, color = AzulMarinoSuave)
                    Text(
                        text = "${formatoKg(promedio)} kg",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colorPromedio
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // Barra del promedio frente al máximo legal
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Blanco)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((promedio / EquipoSeleccionUiState.MAX_KG_POR_SACO_LEY_20949).coerceIn(0f, 1f))
                        .clip(CircleShape)
                        .background(if (excede) RojoCalido else VerdeAccion)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Máx. 25 kg por saco (Ley 20.949)",
                style = MaterialTheme.typography.bodySmall,
                color = if (excede) RojoCalido else AzulMarinoSuave
            )
        }
    }
}
