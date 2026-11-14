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

    /** Letras del español (y vocales con otros acentos, para nombres extranjeros). Igual en el SQL. */
    const val LETRAS = "A-Za-zÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÄËÏÖÜÑÇáéíóúàèìòùâêîôûäëïöüñç"

    const val MAX_CARACTERES_NOMBRE = 80
    const val MAX_CARACTERES_GUIA = 12

    /** Nombres (líder, voluntarios, encargado de pesaje): letras, espacios y apóstrofo; empieza con letra. */
    private val NOMBRE = Regex("^[$LETRAS][$LETRAS '’]*$")

    /** Motivo de omisión de foto: letras, números y espacios; empieza con letra o número. */
    private val MOTIVO = Regex("^[${LETRAS}0-9][${LETRAS}0-9 ]*$")

    const val MENSAJE_NOMBRE = "Solo letras, espacios y apóstrofo (máx. $MAX_CARACTERES_NOMBRE)."
    const val MENSAJE_MOTIVO = "Solo letras, números y espacios."

    /** Recorta y colapsa espacios, igual que la RPC antes de validar. */
    fun normalizar(texto: String): String = texto.trim().replace(Regex("\\s+"), " ")

    fun esNombreValido(texto: String): Boolean {
        val limpio = normalizar(texto)
        return limpio.length <= MAX_CARACTERES_NOMBRE && NOMBRE.matches(limpio)
    }

    /** Mensaje bajo un campo de nombre (null si está vacío o es válido). */
    fun errorNombre(texto: String): String? =
        if (texto.isBlank() || esNombreValido(texto)) null else MENSAJE_NOMBRE

    fun esMotivoValido(texto: String): Boolean {
        val limpio = normalizar(texto)
        return limpio.length <= TopesJornada.MAX_CARACTERES_MOTIVO_OMISION && MOTIVO.matches(limpio)
    }

    /** Mensaje bajo el campo del motivo (null si está vacío o es válido). */
    fun errorMotivo(texto: String): String? =
        if (texto.isBlank() || esMotivoValido(texto)) null else MENSAJE_MOTIVO

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
