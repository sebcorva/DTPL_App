package cl.logisticaverde.app.domain.resumen

import cl.logisticaverde.app.data.local.entity.BasuraRecolectadaEntity
import cl.logisticaverde.app.data.local.entity.JornadaEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumenImpactoCalculatorTest {

    private fun jornada(
        kgNormal: Float = 30.5f,
        kgReciclable: Float = 12f,
        inicio: Long = 1_000_000L,
        fin: Long = 1_000_000L + (2 * 60 + 15) * 60_000L
    ) = JornadaEntity(
        idRegistro = "REG-20261030-ABC123",
        fechaJornada = "2026-10-30",
        brigada = "Brigada A",
        nombrePlaya = "Playa Grande",
        totalParticipantes = 8,
        areaTotalM2 = 1200,
        kmRecorridos = 1.8f,
        horaInicioMs = inicio,
        horaFinMs = fin,
        kgBasuraNormal = kgNormal,
        kgBasuraReciclable = kgReciclable,
        estadoSync = JornadaEntity.SYNC_PENDIENTE
    )

    private fun residuo(id: String, nombre: String, cantidad: Int) =
        BasuraRecolectadaEntity("REG-20261030-ABC123", id, nombre, "Recreativa", cantidad)

    @Test
    fun calcula_cifras_principales() {
        val r = ResumenImpactoCalculator.calcular(jornada(), emptyList())

        assertEquals(42.5f, r.kgTotales, 0.001f)
        assertEquals(28, r.porcentajeReciclable) // 12 / 42,5 = 28,2 %
        assertEquals(8, r.personas)
        assertEquals(1200, r.m2Limpiados)
        assertEquals(1.8f, r.kmRecorridos, 0.001f)
        assertEquals(135 * 60_000L, r.duracionMs)
    }

    @Test
    fun cero_kg_da_cero_por_ciento_sin_dividir_por_cero() {
        val r = ResumenImpactoCalculator.calcular(jornada(kgNormal = 0f, kgReciclable = 0f), emptyList())
        assertEquals(0f, r.kgTotales, 0f)
        assertEquals(0, r.porcentajeReciclable)
    }

    @Test
    fun duracion_sin_fin_valido_es_cero() {
        assertEquals(0L, ResumenImpactoCalculator.calcular(jornada(fin = 0L), emptyList()).duracionMs)
        assertEquals(0L, ResumenImpactoCalculator.calcular(jornada(inicio = 0L), emptyList()).duracionMs)
    }

    @Test
    fun top3_excluye_ceros_ordena_por_cantidad_y_desempata_por_nombre() {
        val top = ResumenImpactoCalculator.topResiduos(
            listOf(
                residuo("A", "Tapas", 37),
                residuo("B", "Colillas de cigarro", 412),
                residuo("C", "Botellas PET", 37),
                residuo("D", "Bolsas", 0),
                residuo("E", "Envoltorios", 5)
            )
        )
        assertEquals(
            listOf(
                ResiduoDestacado("Colillas de cigarro", 412),
                ResiduoDestacado("Botellas PET", 37),
                ResiduoDestacado("Tapas", 37)
            ),
            top
        )
    }

    @Test
    fun top3_con_menos_de_tres_residuos_o_todo_en_cero() {
        assertEquals(1, ResumenImpactoCalculator.topResiduos(listOf(residuo("A", "Tapas", 2), residuo("B", "Bolsas", 0))).size)
        assertEquals(emptyList<ResiduoDestacado>(), ResumenImpactoCalculator.topResiduos(listOf(residuo("A", "Tapas", 0))))
    }

    @Test
    fun formatos_acordados() {
        assertEquals("31 oct 2026", FormatoResumen.fecha("2026-10-31"))
        assertEquals("5 ene 2027", FormatoResumen.fecha("2027-01-05"))
        assertEquals("42,5 kg", FormatoResumen.kg(42.5f))
        assertEquals("30 kg", FormatoResumen.kg(30f))
        assertEquals("1.250,5 kg", FormatoResumen.kg(1250.5f))
        assertEquals("1,8 km", FormatoResumen.km(1.8f))
        assertEquals("1.200 m²", FormatoResumen.m2(1200))
        assertEquals("28 %", FormatoResumen.porcentaje(28))
        assertEquals("2 h 15 min", FormatoResumen.duracion(135 * 60_000L))
        assertEquals("45 min", FormatoResumen.duracion(45 * 60_000L))
        assertEquals("3 h", FormatoResumen.duracion(180 * 60_000L))
        assertEquals("412 · Colillas de cigarro", FormatoResumen.residuo(ResiduoDestacado("Colillas de cigarro", 412)))
        // Tarjeta "Última jornada" (P1): 31-10-2026 09:00 en Chile (UTC-3)
        assertEquals("Sáb, 31 oct 2026 · 9:00", FormatoResumen.fechaConHora("2026-10-31", 1_793_448_000_000L))
        assertEquals("Sáb, 31 oct 2026", FormatoResumen.fechaConHora("2026-10-31", 0L))
    }
}
