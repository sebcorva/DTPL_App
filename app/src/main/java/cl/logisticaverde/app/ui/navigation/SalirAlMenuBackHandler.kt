package cl.logisticaverde.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * RF-NAV-3: desde que se presiona "Comenzar Limpieza" y hasta P9, el botón Atrás no reabre
 * pantallas ya cerradas; solo ofrece salir al menú (la jornada queda guardada para reanudarla).
 */
@Composable
fun SalirAlMenuBackHandler(
    enabled: Boolean = true,
    onSalirAlMenu: () -> Unit
) {
    var mostrarDialogo by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = enabled) { mostrarDialogo = true }

    if (mostrarDialogo) {
        AlertDialog(
            onDismissRequest = { mostrarDialogo = false },
            title = { Text("¿Salir al menú?") },
            text = { Text("La jornada queda guardada y podrás reanudarla.") },
            confirmButton = {
                TextButton(onClick = {
                    mostrarDialogo = false
                    onSalirAlMenu()
                }) {
                    Text("Salir al menú")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogo = false }) {
                    Text("Seguir aquí")
                }
            }
        )
    }
}
