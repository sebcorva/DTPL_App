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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cl.logisticaverde.app.data.local.entity.HerramientaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.HerramientaJornadaEntity
import cl.logisticaverde.app.ui.theme.AzulMarino
import cl.logisticaverde.app.ui.theme.AzulMarinoSuave
import cl.logisticaverde.app.ui.theme.AzulPetroleoClaro
import cl.logisticaverde.app.ui.theme.Blanco
import cl.logisticaverde.app.ui.theme.BordeSuave
import cl.logisticaverde.app.ui.theme.RojoCalido
import cl.logisticaverde.app.ui.theme.SyncAmarillo
import cl.logisticaverde.app.ui.theme.VerdeAccion
import cl.logisticaverde.app.ui.theme.VerdeAccionOscuro

/**
 * Pantalla 7 "Devolución de herramientas" (diseño ISSUE-55). Solo aparece si la brigada llevó herramientas.
 * - Una tarjeta con la lista: cada herramienta con Operativo / Dañado / Extraviado.
 * - Botón Siguiente (Pantalla 8).
 */
@Composable
fun EstadoHerramientasScreen(
    herramientas: List<HerramientaJornadaEntity>,
    onCambiarEstado: (HerramientaJornadaEntity, String) -> Unit,
    onSiguienteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        TituloPantallaFlujo("Devolución de herramientas")
        Spacer(Modifier.height(12.dp))

        TarjetaFlujo(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SubtituloFlujo("Estado al devolver", modifier = Modifier.weight(1f))
                val cantidad = herramientas.size
                BadgedBox(
                    badge = {
                        Badge(containerColor = VerdeAccionOscuro, contentColor = Blanco) {
                            Text(text = cantidad.toString(), fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier.semantics {
                        contentDescription = if (cantidad == 1) "1 herramienta" else "$cantidad herramientas"
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Handyman,
                        contentDescription = null,
                        tint = AzulMarino,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Text(
                text = "Marca cómo vuelve cada herramienta",
                style = MaterialTheme.typography.bodyMedium,
                color = AzulMarinoSuave
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = BordeSuave)

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(herramientas, key = { _, item -> item.id }) { indice, item ->
                    FilaHerramienta(item = item, onCambiarEstado = { onCambiarEstado(item, it) })
                    if (indice < herramientas.lastIndex) HorizontalDivider(color = BordeSuave)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        BotonSiguienteFlujo(enabled = true, onClick = onSiguienteClick, icono = null)
    }
}

/** Color de cada estado al estar elegido (decisión 3). */
private fun colorEstado(estado: String): Color = when (estado) {
    HerramientaCatalogoEntity.ESTADO_DANADO -> SyncAmarillo
    HerramientaCatalogoEntity.ESTADO_EXTRAVIADO -> RojoCalido
    else -> VerdeAccion
}

/** Una herramienta: nombre y los tres estados en botones del mismo ancho (decisión 3). */
@Composable
private fun FilaHerramienta(item: HerramientaJornadaEntity, onCambiarEstado: (String) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = item.nombreHerramienta,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = AzulMarino
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            HerramientaCatalogoEntity.ESTADOS_DISPONIBLES.forEach { estado ->
                val elegido = item.estadoFinal == estado
                val color = colorEstado(estado)
                Surface(
                    onClick = { onCambiarEstado(estado) },
                    selected = elegido,
                    shape = FormaCampoFlujo,
                    color = if (elegido) color else AzulPetroleoClaro,
                    // Blanco sobre verde y rojo; azul marino sobre el amarillo para que se lea
                    contentColor = when {
                        !elegido -> AzulMarino
                        estado == HerramientaCatalogoEntity.ESTADO_DANADO -> AzulMarino
                        else -> Blanco
                    },
                    border = if (elegido) null else BorderStroke(1.dp, BordeSuave),
                    shadowElevation = if (elegido) 2.dp else 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = estado,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
