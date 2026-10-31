package cl.logisticaverde.app.domain.reglas

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Topes razonables de una jornada (ISSUE-31). Se validan en la app y en la RPC
 * `sincronizar_jornada` con los mismos valores: si cambian aquí, cambiar también el SQL.
 */
object TopesJornada {
    const val MAX_PARTICIPANTES = 60
    const val MAX_VOLUNTARIOS = MAX_PARTICIPANTES - 1
    const val MAX_CANTIDAD_POR_RESIDUO = 5_000
    const val MAX_SACOS = 100
    const val MAX_KG_TOTALES = 2_500f
    const val MAX_KM_RECORRIDOS = 30f
    const val MAX_DURACION_MS = 12L * 60 * 60 * 1000
    const val MAX_CARACTERES_MOTIVO_OMISION = 120
}

/** La fecha de la jornada se registra en hora de Chile, sin depender de la zona del teléfono. */
object FechaJornada {
    val ZONA_CHILE: ZoneId = ZoneId.of("America/Santiago")
    private val FORMATO_ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val FORMATO_ID: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE

    fun hoy(ahoraMs: Long = System.currentTimeMillis()): LocalDate =
        Instant.ofEpochMilli(ahoraMs).atZone(ZONA_CHILE).toLocalDate()

    /** "2026-11-16" (columna `fecha_jornada`). */
    fun iso(fecha: LocalDate): String = fecha.format(FORMATO_ISO)

    /** "20261116" (parte del `id_registro`). */
    fun compacta(fecha: LocalDate): String = fecha.format(FORMATO_ID)
}
