package cl.logisticaverde.app.ui.screens.flujo

import org.junit.Assert.assertEquals
import org.junit.Test

/** ISSUE-49: grilla de residuos sin tarjetas solas y formato del tiempo de limpieza. */
class SpanTarjetaResiduoTest {

    private fun spans(total: Int) = (0 until total).map { spanTarjetaResiduo(it, total) }

    @Test
    fun `multiplo de 3 - todas las tarjetas en filas de 3`() {
        assertEquals(List(9) { 2 }, spans(9))
        assertEquals(List(3) { 2 }, spans(3))
    }

    @Test
    fun `sobra 1 - la ultima ocupa la fila completa`() {
        assertEquals(List(9) { 2 } + 6, spans(10))
        assertEquals(listOf(6), spans(1))
    }

    @Test
    fun `sobran 2 - las dos ultimas ocupan media fila cada una`() {
        assertEquals(List(9) { 2 } + listOf(3, 3), spans(11))
        assertEquals(listOf(3, 3), spans(2))
    }

    @Test
    fun `cada fila suma 6 partes`() {
        for (total in 1..20) assertEquals(0, spans(total).sum() % COLUMNAS_GRILLA_RESIDUOS)
    }

    @Test
    fun `tiempo de limpieza en formato hh mm ss`() {
        assertEquals("00:00:00", formatoDuracion(0L))
        assertEquals("00:00:59", formatoDuracion(59_999L))
        assertEquals("00:42:15", formatoDuracion((42 * 60 + 15) * 1_000L))
        assertEquals("02:05:09", formatoDuracion((2 * 3_600 + 5 * 60 + 9) * 1_000L))
        assertEquals("00:00:00", formatoDuracion(-5_000L))
    }
}
