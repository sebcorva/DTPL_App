package cl.logisticaverde.app.domain.resumen

import cl.logisticaverde.app.data.local.entity.BasuraRecolectadaEntity
import cl.logisticaverde.app.data.local.entity.JornadaEntity
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** Un residuo del Top 3 de la tarjeta (BR-12). */
data class ResiduoDestacado(val nombre: String, val cantidad: Int)

/**
 * Cifras de la Pantalla 10 (RF-P10). Se calculan solo con datos locales de Room,
 * así que funcionan sin internet y antes de sincronizar.
 */
data class ResumenImpacto(
    val idRegistro: String,
    val nombrePlaya: String,
    val fechaJornada: String,
    val brigada: String,
    val kgTotales: Float,
    val porcentajeReciclable: Int,
    // Decisión ISSUE-24: líder + voluntarios, mostrados como "personas" (sin nombres, RNF-07)
    val personas: Int,
    val m2Limpiados: Int,
    val kmRecorridos: Float,
    val duracionMs: Long,
    val topResiduos: List<ResiduoDestacado>
)

object ResumenImpactoCalculator {

    const val MAX_TOP_RESIDUOS = 3

    fun calcular(jornada: JornadaEntity, basura: List<BasuraRecolectadaEntity>): ResumenImpacto {
        val kgNormal = jornada.kgBasuraNormal.coerceAtLeast(0f)
        val kgReciclable = jornada.kgBasuraReciclable.coerceAtLeast(0f)
        val kgTotales = kgNormal + kgReciclable

        // BR-12: 0 kg → 0 % (sin división por cero)
        val porcentaje = if (kgTotales > 0f) ((kgReciclable / kgTotales) * 100f).roundToInt() else 0

        val duracion = if (jornada.horaInicioMs > 0L && jornada.horaFinMs > jornada.horaInicioMs) {
            jornada.horaFinMs - jornada.horaInicioMs
        } else {
            0L
        }

        return ResumenImpacto(
            idRegistro = jornada.idRegistro,
            nombrePlaya = jornada.nombrePlaya,
            fechaJornada = jornada.fechaJornada,
            brigada = jornada.brigada,
            kgTotales = kgTotales,
            porcentajeReciclable = porcentaje.coerceIn(0, 100),
            personas = jornada.totalParticipantes.coerceAtLeast(0),
            m2Limpiados = jornada.areaTotalM2.coerceAtLeast(0),
            kmRecorridos = jornada.kmRecorridos.coerceAtLeast(0f),
            duracionMs = duracion,
            topResiduos = topResiduos(basura)
        )
    }

    /** BR-12: excluye cantidades en 0; mayor cantidad primero y, si empatan, por nombre. */
    fun topResiduos(basura: List<BasuraRecolectadaEntity>): List<ResiduoDestacado> =
        basura.asSequence()
            .filter { it.cantidad > 0 }
            .sortedWith(compareByDescending<BasuraRecolectadaEntity> { it.cantidad }.thenBy { it.nombreVisible })
            .take(MAX_TOP_RESIDUOS)
            .map { ResiduoDestacado(it.nombreVisible, it.cantidad) }
            .toList()
}

/** Formatos acordados para la tarjeta (es-CL): "31 oct 2026", "42,5 kg", "2 h 15 min", "1,8 km". */
object FormatoResumen {

    private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    private val LOCALE_CHILE: Locale = Locale.forLanguageTag("es-CL")

    fun fecha(iso: String): String = runCatching {
        val d = LocalDate.parse(iso)
        "${d.dayOfMonth} ${MESES[d.monthValue - 1]} ${d.year}"
    }.getOrDefault(iso)

    /** Un decimal con coma; sin ",0" si es entero. */
    fun decimal(valor: Float): String {
        val redondeado = (valor * 10f).roundToInt() / 10f
        return if (redondeado % 1f == 0f) {
            entero(redondeado.roundToInt())
        } else {
            String.format(LOCALE_CHILE, "%,.1f", redondeado)
        }
    }

    /** Miles con punto: 1.200 */
    fun entero(valor: Int): String = String.format(LOCALE_CHILE, "%,d", valor)

    fun kg(valor: Float): String = "${decimal(valor)} kg"

    fun km(valor: Float): String = "${decimal(valor)} km"

    fun m2(valor: Int): String = "${entero(valor)} m²"

    fun porcentaje(valor: Int): String = "$valor %"

    fun duracion(ms: Long): String {
        val totalMin = (ms / 60_000L).toInt()
        val h = totalMin / 60
        val min = totalMin % 60
        return when {
            h == 0 -> "$min min"
            min == 0 -> "$h h"
            else -> "$h h $min min"
        }
    }

    private val DIAS = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

    /** Tarjeta "Última jornada" de la P1: "Sáb, 31 oct 2026 · 9:00" (hora de inicio, en Chile, 24 h). */
    fun fechaConHora(iso: String, horaInicioMs: Long): String {
        val fechaTexto = runCatching {
            val d = LocalDate.parse(iso)
            "${DIAS[d.dayOfWeek.value - 1]}, ${fecha(iso)}"
        }.getOrDefault(iso)
        if (horaInicioMs <= 0L) return fechaTexto
        val hora = java.time.Instant.ofEpochMilli(horaInicioMs).atZone(java.time.ZoneId.of("America/Santiago"))
        return "$fechaTexto · ${hora.hour}:${hora.minute.toString().padStart(2, '0')}"
    }

    fun residuo(r: ResiduoDestacado): String = "${entero(r.cantidad)} · ${r.nombre}"
}
