package cl.logisticaverde.app.ui.screens.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.logisticaverde.app.domain.resumen.FormatoResumen
import cl.logisticaverde.app.ui.theme.AzulMarino
import cl.logisticaverde.app.ui.theme.AzulMarinoSuave
import cl.logisticaverde.app.ui.theme.AzulPetroleo
import cl.logisticaverde.app.ui.theme.Blanco
import cl.logisticaverde.app.ui.theme.BordeSuave
import cl.logisticaverde.app.ui.theme.CieloClaro
import cl.logisticaverde.app.ui.theme.VerdeAccion
import cl.logisticaverde.app.ui.theme.VerdeMenta

/**
 * P1 — tarjeta "Última jornada" (diseño 31-10-2026): la última jornada cerrada del teléfono,
 * sincronizada o no. Al tocarla abre su resumen (P10).
 */
@Composable
fun TarjetaUltimaJornada(
    ultima: UltimaJornadaUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = Blanco,
        shadowElevation = 3.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconoCirculo(Icons.Outlined.CalendarMonth, fondo = VerdeMenta, tinte = AzulPetroleo, tamano = 48)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text("Última jornada", style = MaterialTheme.typography.titleMedium, fontSize = 18.sp, color = AzulMarino)
                    Text(
                        ultima.nombrePlaya,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AzulMarinoSuave,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(ultima.fechaTexto, style = MaterialTheme.typography.bodySmall, color = AzulMarinoSuave)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Ver resumen", tint = AzulMarino)
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Cifra(Icons.Outlined.Scale, FormatoResumen.kg(ultima.kgRecolectados), "recolectados", Modifier.weight(1f))
                Separador()
                Cifra(Icons.Outlined.Inventory2, FormatoResumen.entero(ultima.items), "ítems", Modifier.weight(1f))
                Separador()
                Cifra(Icons.Outlined.Eco, FormatoResumen.porcentaje(ultima.porcentajeReciclaje), "reciclaje", Modifier.weight(1f), VerdeAccion)
                Separador()
                Cifra(Icons.Outlined.DirectionsWalk, FormatoResumen.km(ultima.kmRecorridos), "recorridos", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Cifra(
    icono: ImageVector,
    valor: String,
    etiqueta: String,
    modifier: Modifier,
    tinte: androidx.compose.ui.graphics.Color = AzulPetroleo
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        IconoCirculo(icono, fondo = CieloClaro, tinte = tinte, tamano = 40)
        Spacer(Modifier.height(6.dp))
        Text(
            valor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = AzulMarino,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        Text(etiqueta, style = MaterialTheme.typography.bodySmall, color = AzulMarinoSuave, maxLines = 1)
    }
}

@Composable
private fun Separador() {
    Box(
        Modifier
            .width(1.dp)
            .fillMaxHeight()
            .padding(vertical = 8.dp)
            .background(BordeSuave)
    )
}

@Composable
private fun IconoCirculo(icono: ImageVector, fondo: androidx.compose.ui.graphics.Color, tinte: androidx.compose.ui.graphics.Color, tamano: Int) {
    Box(
        Modifier
            .size(tamano.dp)
            .clip(CircleShape)
            .background(fondo),
        contentAlignment = Alignment.Center
    ) {
        Icon(icono, contentDescription = null, tint = tinte, modifier = Modifier.size((tamano * 0.55f).dp))
    }
}
