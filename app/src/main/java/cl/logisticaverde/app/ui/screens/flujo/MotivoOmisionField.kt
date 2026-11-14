package cl.logisticaverde.app.ui.screens.flujo

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import cl.logisticaverde.app.domain.reglas.ReglasTexto
import cl.logisticaverde.app.domain.reglas.TopesJornada
import cl.logisticaverde.app.ui.theme.AzulMarinoSuave
import cl.logisticaverde.app.ui.theme.VerdeAccionOscuro

/**
 * ISSUE-35: campo obligatorio que aparece en la misma pantalla al marcar "Omitir fotografía".
 * Texto libre, sin opciones predefinidas.
 */
@Composable
fun MotivoOmisionField(
    motivo: String,
    onMotivoChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    // P6 (ISSUE-51): 1–2 líneas para caber en el visor más bajo
    minLines: Int = 2,
    maxLines: Int = 3
) {
    val vacio = motivo.isBlank()
    // ISSUE-58: solo letras, números y espacios (el botón Siguiente se deshabilita mientras no cumpla)
    val error = ReglasTexto.errorMotivo(motivo)
    OutlinedTextField(
        value = motivo,
        onValueChange = onMotivoChange,
        label = { Text("Motivo para omitir la foto") },
        placeholder = { Text("Ej.: sin batería o había personas en la toma") },
        isError = error != null,
        supportingText = {
            // Aviso suave (no en rojo) mientras falte: el botón Siguiente ya queda deshabilitado
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = error ?: if (vacio) "Obligatorio para continuar" else "Listo",
                    color = when {
                        error != null -> MaterialTheme.colorScheme.error
                        vacio -> AzulMarinoSuave
                        else -> VerdeAccionOscuro
                    },
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${motivo.length}/${TopesJornada.MAX_CARACTERES_MOTIVO_OMISION}",
                    color = AzulMarinoSuave
                )
            }
        },
        minLines = minLines,
        maxLines = maxLines,
        shape = FormaCampoFlujo,
        colors = coloresCampoFlujo(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier.fillMaxWidth()
    )
}
