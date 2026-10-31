package cl.logisticaverde.app.ui.navigation

import android.widget.Toast
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cl.logisticaverde.app.ui.theme.AzulMarinoSuave
import cl.logisticaverde.app.ui.theme.AzulPetroleo
import cl.logisticaverde.app.ui.theme.Blanco
import cl.logisticaverde.app.ui.theme.SyncGris
import cl.logisticaverde.app.ui.theme.VerdeMenta

enum class PestanaInferior { INICIO, REPORTES }

/**
 * Barra inferior de Inicio y Reportes. "Usuario" se muestra atenuado hasta que exista
 * la cuenta por teléfono (ISSUE-38); al tocarlo avisa "Próximamente".
 */
@Composable
fun BarraInferior(
    seleccionada: PestanaInferior,
    onInicio: () -> Unit,
    onReportes: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colores = NavigationBarItemDefaults.colors(
        selectedIconColor = AzulPetroleo,
        selectedTextColor = AzulPetroleo,
        indicatorColor = VerdeMenta,
        unselectedIconColor = AzulMarinoSuave,
        unselectedTextColor = AzulMarinoSuave
    )
    val forma = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    NavigationBar(
        containerColor = Blanco,
        tonalElevation = 0.dp,
        // MainActivity ya aplica el margen de la barra del sistema
        windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, forma)
            .clip(forma)
    ) {
        NavigationBarItem(
            selected = seleccionada == PestanaInferior.INICIO,
            onClick = { if (seleccionada != PestanaInferior.INICIO) onInicio() },
            icon = {
                Icon(
                    if (seleccionada == PestanaInferior.INICIO) Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = null
                )
            },
            label = { Text("Inicio") },
            colors = colores
        )
        NavigationBarItem(
            selected = seleccionada == PestanaInferior.REPORTES,
            onClick = { if (seleccionada != PestanaInferior.REPORTES) onReportes() },
            icon = {
                Icon(
                    if (seleccionada == PestanaInferior.REPORTES) Icons.Filled.BarChart else Icons.Outlined.BarChart,
                    contentDescription = null
                )
            },
            label = { Text("Reportes") },
            colors = colores
        )
        NavigationBarItem(
            selected = false,
            onClick = { Toast.makeText(context, "Usuario: próximamente", Toast.LENGTH_SHORT).show() },
            icon = { Icon(Icons.Outlined.AccountCircle, contentDescription = null) },
            label = { Text("Usuario") },
            colors = NavigationBarItemDefaults.colors(
                unselectedIconColor = SyncGris,
                unselectedTextColor = SyncGris
            )
        )
    }
}
