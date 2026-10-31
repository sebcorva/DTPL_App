package cl.logisticaverde.app.ui.screens.resumen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import cl.logisticaverde.app.R
import cl.logisticaverde.app.domain.resumen.FormatoResumen
import cl.logisticaverde.app.domain.resumen.ResumenImpacto
import cl.logisticaverde.app.ui.theme.AzulMarino
import cl.logisticaverde.app.ui.theme.VerdeMenta
import cl.logisticaverde.app.ui.theme.VerdeAccion
import cl.logisticaverde.app.ui.theme.AzulPetroleo
import java.io.File
import java.io.FileOutputStream

/**
 * ISSUE-26: dibuja la tarjeta 1080×1920 (9:16, Historias de Instagram) con Canvas.
 * Decisión de la clienta (31-10-2026): la tarjeta lleva solo cifras, sin fotos (Ley 21.719).
 */
object ResumenImpactoGenerator {

    const val ANCHO = 1080
    const val ALTO = 1920
    private const val MARGEN = 72f
    private const val CARPETA_COMPARTIR = "compartir"

    // Nunito variable: el peso se fija con el eje "wght" del Paint
    private var fuente: Typeface = Typeface.SANS_SERIF

    fun generar(context: Context, resumen: ResumenImpacto): Bitmap {
        fuente = runCatching { ResourcesCompat.getFont(context, R.font.nunito) }.getOrNull() ?: Typeface.SANS_SERIF
        val bitmap = Bitmap.createBitmap(ANCHO, ALTO, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val blanco = android.graphics.Color.WHITE
        val claro = VerdeMenta.toArgb()

        // Fondo con degradado de la marca
        val fondo = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, ALTO.toFloat(),
                VerdeAccion.toArgb(), AzulPetroleo.toArgb(), Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, ANCHO.toFloat(), ALTO.toFloat(), fondo)

        val anchoUtil = ANCHO - 2 * MARGEN
        var y = 170f

        // Encabezado
        y = texto(canvas, context.getString(R.string.resumen_titulo_tarjeta), y, 76f, blanco, negrita = true, ancho = anchoUtil)
        y += 30f
        y = texto(canvas, resumen.nombrePlaya.ifBlank { "—" }, y, 58f, blanco, ancho = anchoUtil)
        y += 16f
        val subtitulo = listOf(FormatoResumen.fecha(resumen.fechaJornada), resumen.brigada)
            .filter { it.isNotBlank() }.joinToString(" · ")
        y = texto(canvas, subtitulo, y, 42f, claro, ancho = anchoUtil)

        // Cifra principal: kg recolectados
        y += 150f
        y = texto(canvas, FormatoResumen.kg(resumen.kgTotales), y, 170f, blanco, negrita = true, ancho = anchoUtil)
        y -= 50f // la cifra grande no tiene descendentes: se acerca la etiqueta
        y = texto(canvas, context.getString(R.string.resumen_kg_recolectados), y, 44f, claro, ancho = anchoUtil)
        y += 14f
        y = texto(
            canvas,
            "${FormatoResumen.porcentaje(resumen.porcentajeReciclable)} ${context.getString(R.string.resumen_reciclable)}",
            y, 50f, blanco, negrita = true, ancho = anchoUtil
        )

        // Grilla 2×2 de cifras
        y += 80f
        val gap = 32f
        val celdaAncho = (anchoUtil - gap) / 2f
        val celdaAlto = 220f
        val celdas = listOf(
            FormatoResumen.entero(resumen.personas) to context.getString(R.string.resumen_personas),
            FormatoResumen.m2(resumen.m2Limpiados) to context.getString(R.string.resumen_m2),
            FormatoResumen.km(resumen.kmRecorridos) to context.getString(R.string.resumen_km),
            FormatoResumen.duracion(resumen.duracionMs) to context.getString(R.string.resumen_duracion)
        )
        val tarjeta = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blanco }
        celdas.forEachIndexed { i, (valor, etiqueta) ->
            val col = i % 2
            val fila = i / 2
            val left = MARGEN + col * (celdaAncho + gap)
            val top = y + fila * (celdaAlto + gap)
            canvas.drawRoundRect(RectF(left, top, left + celdaAncho, top + celdaAlto), 36f, 36f, tarjeta)
            val cx = left + celdaAncho / 2f
            texto(canvas, valor, top + 105f, 66f, AzulMarino.toArgb(), negrita = true, ancho = celdaAncho - 40f, centroX = cx)
            texto(canvas, etiqueta, top + 170f, 36f, AzulPetroleo.toArgb(), ancho = celdaAncho - 40f, centroX = cx)
        }
        y += 2 * celdaAlto + gap

        // Top 3 residuos
        if (resumen.topResiduos.isNotEmpty()) {
            y += 90f
            y = texto(canvas, context.getString(R.string.resumen_top_residuos), y, 46f, claro, negrita = true, ancho = anchoUtil)
            resumen.topResiduos.forEach { residuo ->
                y += 22f
                y = texto(canvas, FormatoResumen.residuo(residuo), y, 52f, blanco, ancho = anchoUtil)
            }
        }

        // Pie: hashtag y @cuenta (textos de prueba hasta que la clienta los entregue)
        val pie = "${context.getString(R.string.resumen_hashtag)}   ${context.getString(R.string.resumen_cuenta)}"
        texto(canvas, pie, ALTO - 110f, 44f, blanco, negrita = true, ancho = anchoUtil)

        return bitmap
    }

    /** Guarda en cacheDir/compartir/resumen_<id>.png (la única carpeta expuesta por el FileProvider para compartir). */
    fun guardarParaCompartir(context: Context, bitmap: Bitmap, idRegistro: String): File {
        val carpeta = File(context.cacheDir, CARPETA_COMPARTIR).apply { mkdirs() }
        val seguro = idRegistro.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val archivo = File(carpeta, "resumen_$seguro.png")
        FileOutputStream(archivo).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return archivo
    }

    /** Dibuja una línea centrada (recortada con "…" si no cabe) y devuelve la y de la línea base siguiente. */
    private fun texto(
        canvas: Canvas,
        contenido: String,
        baseY: Float,
        tamano: Float,
        color: Int,
        negrita: Boolean = false,
        ancho: Float,
        centroX: Float = ANCHO / 2f
    ): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = tamano
            textAlign = Paint.Align.CENTER
            typeface = fuente
            fontVariationSettings = if (negrita) "'wght' 800" else "'wght' 500"
        }
        val linea = TextUtils.ellipsize(contenido, paint, ancho, TextUtils.TruncateAt.END).toString()
        canvas.drawText(linea, centroX, baseY, paint)
        return baseY + tamano
    }
}
