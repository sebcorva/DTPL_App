package cl.logisticaverde.app.ui.screens.flujo

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import cl.logisticaverde.app.domain.reglas.TopesJornada

/**
 * ISSUE-35: campo obligatorio que aparece en la misma pantalla al marcar "Omitir fotografía".
 * Texto libre, sin opciones predefinidas.
 */
@Composable
fun MotivoOmisionField(
    motivo: String,
    onMotivoChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val vacio = motivo.isBlank()
    OutlinedTextField(
        value = motivo,
        onValueChange = onMotivoChange,
        label = { Text("Motivo para omitir la foto") },
        placeholder = { Text("Ej.: sin batería, había personas en la toma") },
        isError = vacio,
        supportingText = {
            Text(
                if (vacio) "Obligatorio para continuar."
                else "${motivo.length}/${TopesJornada.MAX_CARACTERES_MOTIVO_OMISION}"
            )
        },
        maxLines = 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier.fillMaxWidth()
    )
}
