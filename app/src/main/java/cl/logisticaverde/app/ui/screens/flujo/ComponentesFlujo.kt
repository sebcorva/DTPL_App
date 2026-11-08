package cl.logisticaverde.app.ui.screens.flujo

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material.icons.filled.CameraAlt
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import cl.logisticaverde.app.ui.theme.CieloClaro
import cl.logisticaverde.app.ui.theme.VerdeAccion
import cl.logisticaverde.app.ui.theme.VerdeAccionOscuro

/*
 * Piezas comunes del rediseño de las pantallas del flujo (P2 ISSUE-44, P3 ISSUE-46, P4 ISSUE-48, P6 ISSUE-51),
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
    unfocusedPlaceholderColor = AzulMarinoSuave,
    // Deshabilitado (p. ej. desplegable de la P4 sin opciones): blanco con texto atenuado
    disabledContainerColor = Blanco,
    disabledBorderColor = BordeSuave,
    disabledTextColor = AzulMarinoSuave,
    disabledPlaceholderColor = AzulMarinoSuave.copy(alpha = 0.6f),
    disabledTrailingIconColor = AzulMarinoSuave.copy(alpha = 0.6f)
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

/**
 * Fondo celeste para un área vacía del flujo (P3 "Sin fotografía", P4 "Sin herramientas"):
 * ícono + texto centrados y, debajo, [contenido] opcional.
 */
@Composable
fun FondoVacioFlujo(
    icono: ImageVector,
    texto: String,
    contenido: @Composable () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(CieloClaro)
            .border(1.dp, BordeSuave, RoundedCornerShape(16.dp))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icono,
                contentDescription = null,
                tint = AzulMarinoSuave,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = texto,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AzulMarinoSuave
            )
        }
        Spacer(Modifier.height(10.dp))
        contenido()
    }
}

/** Dibuja cuatro esquinas en "L" sobre el contenido, como el visor de una cámara (P3, P4 y P6). */
fun Modifier.esquinasVisor(color: Color): Modifier = drawWithContent {
    drawContent()
    val margen = 12.dp.toPx()
    val largo = 28.dp.toPx()
    val grosor = 4.dp.toPx()
    val izq = margen
    val der = size.width - margen
    val arr = margen
    val aba = size.height - margen
    fun linea(desde: Offset, hasta: Offset) =
        drawLine(color, desde, hasta, strokeWidth = grosor, cap = StrokeCap.Round)
    linea(Offset(izq, arr), Offset(izq + largo, arr)); linea(Offset(izq, arr), Offset(izq, arr + largo))
    linea(Offset(der, arr), Offset(der - largo, arr)); linea(Offset(der, arr), Offset(der, arr + largo))
    linea(Offset(izq, aba), Offset(izq + largo, aba)); linea(Offset(izq, aba), Offset(izq, aba - largo))
    linea(Offset(der, aba), Offset(der - largo, aba)); linea(Offset(der, aba), Offset(der, aba - largo))
}

/**
 * Visor de una foto del flujo (P3 y P6): sin foto, la [ilustracion] decorativa; con foto, la vista previa.
 * Encima van las esquinas tipo visor y [contenido] (botón de cámara).
 */
@Composable
fun VisorFotoFlujo(
    pathFoto: String?,
    @DrawableRes ilustracion: Int,
    descripcionFoto: String,
    contenido: @Composable BoxScope.() -> Unit
) {
    val bitmap = remember(pathFoto) { pathFoto?.let { BitmapFactory.decodeFile(it) } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(CieloClaro)
            .esquinasVisor(if (bitmap != null) Blanco else AzulMarino),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap == null) {
            Image(
                painter = painterResource(ilustracion),
                contentDescription = null, // decorativa
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = descripcionFoto,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        contenido()
    }
}

/** Botón de cámara en píldora verde, sobre el visor de foto (P3 y P6). */
@Composable
fun BotonCamaraFlujo(texto: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = VerdeAccion,
        contentColor = Blanco,
        shadowElevation = 6.dp,
        modifier = Modifier.padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.size(10.dp))
            Text(
                text = texto,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}
