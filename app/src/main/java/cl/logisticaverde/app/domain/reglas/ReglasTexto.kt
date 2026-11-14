package cl.logisticaverde.app.domain.reglas

/**
 * Caracteres permitidos en los campos de texto libre (ISSUE-58 e ISSUE-60).
 *
 * Lista blanca por campo, igual en la app, en [ValidadorJornada] y en la RPC `sincronizar_jornada`
 * (docs/supabase_schema.sql): si cambia aquí, cambiarla también en el SQL.
 *
 * Por qué: estos textos se copiarán a Google Sheets; uno que empiece con `=`, `+`, `-` o `@` se
 * ejecutaría como fórmula (p. ej. `=IMPORTXML(...)` enviaría la planilla a un tercero). Además, un
 * campo que solo acepta lo que necesita no sirve para inflar la base de datos.
 */
object ReglasTexto {

    const val MAX_CARACTERES_GUIA = 12

    /** N° de guía de despacho: letras, números y guion; empieza con letra o número; 1 a 12. */
    private val GUIA = Regex("^[A-Za-z0-9][A-Za-z0-9-]{0,${MAX_CARACTERES_GUIA - 1}}$")

    const val MENSAJE_GUIA = "Solo letras, números y guion (máx. $MAX_CARACTERES_GUIA), empezando con letra o número."

    fun esGuiaValida(texto: String): Boolean = GUIA.matches(texto)

    /** Mensaje bajo el campo de la guía (null si está vacía, porque es opcional, o si es válida). */
    fun errorGuia(texto: String): String? {
        val limpio = texto.trim()
        return if (limpio.isEmpty() || esGuiaValida(limpio)) null else MENSAJE_GUIA
    }
}
