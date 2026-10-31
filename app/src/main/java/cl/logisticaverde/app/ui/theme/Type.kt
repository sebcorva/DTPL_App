package cl.logisticaverde.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import cl.logisticaverde.app.R

/**
 * Nunito (SIL OFL 1.1, docs/licencias/Nunito-OFL.txt), incluida en el APK porque la app funciona sin internet.
 * Es una fuente variable: cada peso se obtiene del mismo archivo con el eje "wght".
 */
@OptIn(ExperimentalTextApi::class)
private fun nunito(peso: FontWeight) = Font(
    resId = R.font.nunito,
    weight = peso,
    variationSettings = FontVariation.Settings(FontVariation.weight(peso.weight))
)

val Nunito = FontFamily(
    nunito(FontWeight.Normal),
    nunito(FontWeight.Medium),
    nunito(FontWeight.SemiBold),
    nunito(FontWeight.Bold),
    nunito(FontWeight.ExtraBold)
)

private val base = Typography()

private fun TextStyle.nunito(peso: FontWeight? = null) =
    copy(fontFamily = Nunito, fontWeight = peso ?: fontWeight)

val Typography = Typography(
    displayLarge = base.displayLarge.nunito(FontWeight.ExtraBold),
    displayMedium = base.displayMedium.nunito(FontWeight.ExtraBold),
    displaySmall = base.displaySmall.nunito(FontWeight.ExtraBold),
    headlineLarge = base.headlineLarge.nunito(FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.nunito(FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.nunito(FontWeight.Bold),
    titleLarge = base.titleLarge.nunito(FontWeight.Bold),
    titleMedium = base.titleMedium.nunito(FontWeight.Bold),
    titleSmall = base.titleSmall.nunito(FontWeight.Bold),
    bodyLarge = base.bodyLarge.nunito(FontWeight.Medium),
    bodyMedium = base.bodyMedium.nunito(FontWeight.Medium),
    bodySmall = base.bodySmall.nunito(FontWeight.Medium),
    labelLarge = base.labelLarge.nunito(FontWeight.Bold),
    labelMedium = base.labelMedium.nunito(FontWeight.Bold),
    labelSmall = base.labelSmall.nunito(FontWeight.Bold)
)
