package cl.logisticaverde.app.ui.screens.flujo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.logisticaverde.app.R
import cl.logisticaverde.app.ui.theme.AzulMarino
import cl.logisticaverde.app.ui.theme.AzulMarinoSuave
import cl.logisticaverde.app.ui.theme.AzulPetroleo
import cl.logisticaverde.app.ui.theme.AzulPetroleoClaro
import cl.logisticaverde.app.ui.theme.Blanco
import cl.logisticaverde.app.ui.theme.BordeSuave
import cl.logisticaverde.app.ui.theme.VerdeAccion
import cl.logisticaverde.app.ui.theme.VerdeAccionOscuro

/*
 * Piezas comunes del rediseño de las pantallas del flujo (P2 ISSUE-44, P3 ISSUE-46),
 * para que todas compartan tarjetas, subtítulos, botones de selección y el botón Siguiente.
 */

val FormaTarjetaFlujo = RoundedCornerShape(24.dp)
val FormaCampoFlujo = RoundedCornerShape(12.dp)

@Composable
fun TituloPantallaFlujo(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.ExtraBold,
        color = AzulMarino,
        modifier = modifier
    )
}

@Composable
fun TarjetaFlujo(modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = FormaTarjetaFlujo,
        color = Blanco,
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, FormaTarjetaFlujo)
    ) {
        Column(modifier = Modifier.padding(20.dp)) { contenido() }
    }
}

@Composable
fun SubtituloFlujo(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto,
        style = MaterialTheme.typography.titleLarge,
        fontSize = 24.sp,
        fontWeight = FontWeight.ExtraBold,
        color = VerdeAccionOscuro,
        modifier = modifier
    )
}

/** Botón de selección (brigada, sector): verde si está activo, celeste si no. */
@Composable
fun BotonSeleccionFlujo(
    texto: String,
    activo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        selected = activo,
        shape = FormaCampoFlujo,
        color = if (activo) VerdeAccion else AzulPetroleoClaro,
        contentColor = if (activo) Blanco else AzulMarino,
        border = if (activo) null else BorderStroke(1.dp, BordeSuave),
        shadowElevation = if (activo) 2.dp else 0.dp,
        modifier = modifier.height(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = texto,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

/** Botón Siguiente: píldora verde con flecha, como "Iniciar jornada" (decisión 07-11-2026). */
@Composable
fun BotonSiguienteFlujo(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = VerdeAccion, contentColor = Blanco),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
        modifier = modifier
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

@Composable
fun coloresCampoFlujo(): TextFieldColors = OutlinedTextFieldDefaults.colors(
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

@Composable
fun coloresCheckboxFlujo() = CheckboxDefaults.colors(
    checkedColor = VerdeAccion,
    uncheckedColor = AzulMarinoSuave,
    checkmarkColor = Blanco
)

/**
 * Nombre del sector sin la superficie entre paréntesis: "Sector 1 - Norte (2.000 m²)" → "Sector 1 - Norte".
 * El catálogo trae la superficie pegada al nombre; mejora futura en ISSUE-47 (limpiar el catálogo).
 */
fun nombreSectorVisible(nombre: String): String =
    nombre.replace(Regex("""\s*\([^()]*m(²|2)\s*\)\s*$""", RegexOption.IGNORE_CASE), "").trim()
