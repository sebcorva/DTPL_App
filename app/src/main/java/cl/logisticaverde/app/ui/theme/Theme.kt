package cl.logisticaverde.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Solo modo claro (decisión 31-10-2026: se lee mejor a pleno sol)
private val EsquemaClaro = lightColorScheme(
    primary = VerdeAccion,
    onPrimary = Blanco,
    primaryContainer = VerdeMenta,
    onPrimaryContainer = VerdeAccionOscuro,
    secondary = AzulPetroleo,
    onSecondary = Blanco,
    secondaryContainer = AzulPetroleoClaro,
    onSecondaryContainer = AzulMarino,
    tertiary = AzulMarino,
    background = CieloBlanco,
    onBackground = AzulMarino,
    surface = Blanco,
    onSurface = AzulMarino,
    surfaceVariant = CieloBlanco,
    onSurfaceVariant = AzulMarinoSuave,
    surfaceContainer = Blanco,
    surfaceContainerLow = Blanco,
    surfaceContainerHigh = Blanco,
    surfaceContainerHighest = CieloBlanco,
    outline = BordeSuave,
    outlineVariant = BordeSuave,
    error = ErrorRojo
)

@Composable
fun LogisticaVerdeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = EsquemaClaro,
        typography = Typography,
        content = content
    )
}
