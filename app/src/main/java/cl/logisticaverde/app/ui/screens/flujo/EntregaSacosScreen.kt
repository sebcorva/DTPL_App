package cl.logisticaverde.app.ui.screens.flujo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Recycling
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.logisticaverde.app.data.local.entity.JornadaEntity
import cl.logisticaverde.app.ui.theme.AzulMarinoSuave
import cl.logisticaverde.app.ui.theme.AzulPetroleo
import cl.logisticaverde.app.ui.theme.AzulPetroleoClaro
import cl.logisticaverde.app.ui.theme.CieloClaro
import cl.logisticaverde.app.ui.theme.VerdeAccion
import cl.logisticaverde.app.ui.theme.VerdeAccionOscuro
import cl.logisticaverde.app.ui.theme.VerdeMenta

/**
 * Pantalla 9 "Entrega y destino de sacos" (diseño ISSUE-53):
 * - Tarjeta "Basura normal": destino (camión DIMAO, pendiente de retiro) y N° guía de despacho opcional
 *   si se entregó al camión.
 * - Tarjeta "Basura reciclable": destino (Punto Limpio, reciclador de base, pendiente de entrega).
 * - Con 0 kg, cada tarjeta solo avisa que no aplica ("No aplicable" queda guardado por la regla del ViewModel).
 * - Botón "Guardar jornada": guarda en Room y pasa al resumen de la jornada.
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
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            TituloPantallaFlujo("Entrega y destino de sacos")
            Spacer(Modifier.height(12.dp))

            // TARJETA 1: basura normal + N° guía de despacho opcional
            TarjetaDestino(
                titulo = "Basura normal",
                icono = Icons.Outlined.Delete,
                colorIcono = AzulPetroleo,
                fondoIcono = AzulPetroleoClaro,
                kg = uiState.kgBasuraNormalVal,
                opciones = JornadaEntity.OPCIONES_DESTINO_NORMAL,
                permitidas = uiState.opcionesPermitidasBasuraNormal,
                noAplica = JornadaEntity.DESTINO_NORMAL_NO_APLICA,
                seleccionada = uiState.destinoBasuraNormal,
                onSeleccionar = onSelectDestinoNormal,
                textoSinKilos = "Sin kilos de basura normal: no aplica"
            ) {
                if (uiState.mostrarCampoGuiaDespacho) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.nroGuiaDespachoInput,
                        onValueChange = onNroGuiaDespachoChange,
                        label = { Text("N° guía de despacho (opcional)") },
                        placeholder = { Text("Ej.: GD-2026-0041") },
                        singleLine = true,
                        shape = FormaCampoFlujo,
                        colors = coloresCampoFlujo(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // TARJETA 2: basura reciclable
            TarjetaDestino(
                titulo = "Basura reciclable",
                icono = Icons.Outlined.Recycling,
                colorIcono = VerdeAccion,
                fondoIcono = VerdeMenta,
                kg = uiState.kgBasuraReciclableVal,
                opciones = JornadaEntity.OPCIONES_DESTINO_RECICLABLE,
                permitidas = uiState.opcionesPermitidasBasuraReciclable,
                noAplica = JornadaEntity.DESTINO_RECICLABLE_NO_APLICA,
                seleccionada = uiState.destinoBasuraReciclable,
                onSeleccionar = onSelectDestinoReciclable,
                textoSinKilos = "Sin kilos de basura reciclable: no aplica"
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(12.dp))
        BotonSiguienteFlujo(
            enabled = true,
            onClick = onGuardarJornadaClick,
            texto = "Guardar jornada",
            icono = null
        )
    }
}

/**
 * Tarjeta de destino de un tipo de basura (decisiones 2–4): encabezado con ícono, subtítulo y kilos;
 * con kilos, las opciones elegibles (sin "No aplicable"); con 0 kg, solo la nota de que no aplica.
 */
@Composable
private fun TarjetaDestino(
    titulo: String,
    icono: ImageVector,
    colorIcono: Color,
    fondoIcono: Color,
    kg: Float,
    opciones: List<String>,
    permitidas: List<String>,
    noAplica: String,
    seleccionada: String,
    onSeleccionar: (String) -> Unit,
    textoSinKilos: String,
    extra: @Composable ColumnScope.() -> Unit = {}
) {
    TarjetaFlujo {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(fondoIcono)
            ) {
                Icon(icono, contentDescription = null, tint = colorIcono, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            SubtituloFlujo(titulo, modifier = Modifier.weight(1f))
            Surface(shape = CircleShape, color = VerdeMenta, contentColor = VerdeAccionOscuro) {
                Text(
                    text = "${formatoKg(kg)} kg",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        if (kg <= 0f) {
            // Sin kilos: "No aplicable" ya queda elegido por la regla del ViewModel
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CieloClaro)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = AzulMarinoSuave,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = textoSinKilos,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AzulMarinoSuave
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                opciones
                    .filter { it != noAplica && it in permitidas }
                    .forEach { opcion ->
                        BotonSeleccionFlujo(
                            texto = opcion,
                            activo = seleccionada == opcion,
                            onClick = { onSeleccionar(opcion) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
            }
        }
        extra()
    }
}
