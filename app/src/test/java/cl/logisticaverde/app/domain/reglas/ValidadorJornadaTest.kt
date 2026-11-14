package cl.logisticaverde.app.domain.reglas

import cl.logisticaverde.app.data.local.db.CatalogoSeedData
import cl.logisticaverde.app.data.local.entity.BasuraRecolectadaEntity
import cl.logisticaverde.app.data.local.entity.HerramientaJornadaEntity
import cl.logisticaverde.app.data.local.entity.JornadaEntity
import cl.logisticaverde.app.data.local.entity.ParticipanteJornadaEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** ISSUE-42: las mismas reglas que la RPC, antes de cerrar la jornada en P9. */
class ValidadorJornadaTest {

    // 16-11-2026 12:00 en Chile (UTC-3)
    private val ahora = 1_794_841_200_000L
    private val id = "REG-20261116-ABC123"

    private val jornadaValida = JornadaEntity(
        idRegistro = id,
        idBrigada = "BRIGADA-01",
        brigada = "Brigada A",
        fechaJornada = "2026-11-16",
        idPlaya = CatalogoSeedData.ID_PLAYA_GRANDE,
        sectoresIdsCsv = "SECTOR-01,SECTOR-02",
        horaInicioMs = ahora - 3 * 3_600_000L,
        horaFinMs = ahora - 3_600_000L,
        kmRecorridos = 1.5f,
        motivoOmisionInicio = "Sin batería",
        motivoOmisionLimpio = "Sin batería",
        motivoOmisionSacos = "Sin batería",
        cantidadSacos = 2,
        kgBasuraNormal = 20f,
        kgBasuraReciclable = 10f,
        encargadoPesaje = "Rosa",
        destinoBasuraNormal = JornadaEntity.DESTINO_NORMAL_DIMAO,
        destinoBasuraReciclable = JornadaEntity.DESTINO_RECICLABLE_PUNTO_LIMPIO
    )
    private val participantes = listOf(
        ParticipanteJornadaEntity(idRegistro = id, nombre = "Ana", rol = ParticipanteJornadaEntity.ROL_LIDER),
        ParticipanteJornadaEntity(idRegistro = id, nombre = "Luis", rol = ParticipanteJornadaEntity.ROL_VOLUNTARIO)
    )
    private val herramientas = listOf(
        HerramientaJornadaEntity(idRegistro = id, qrCodigo = "INV-02-PINZA-01", nombreHerramienta = "Pinza 1", estadoFinal = "Operativo")
    )
    private val basura = listOf(BasuraRecolectadaEntity(id, "COLILLAS", "Colillas", "Recreativa", 412))

    private fun validar(
        jornada: JornadaEntity = jornadaValida,
        participantes: List<ParticipanteJornadaEntity> = this.participantes,
        herramientas: List<HerramientaJornadaEntity> = this.herramientas,
        basura: List<BasuraRecolectadaEntity> = this.basura
    ) = ValidadorJornada.validar(
        jornada, participantes, herramientas, basura,
        CatalogoSeedData.sectoresPlayaGrande, CatalogoSeedData.herramientasQr, CatalogoSeedData.brigadas, ahora
    )

    @Test
    fun jornada_completa_es_valida() {
        assertEquals(emptyList<String>(), validar())
    }

    @Test
    fun issue58_textos_libres_con_formulas_se_rechazan() {
        val conFormula = participantes + ParticipanteJornadaEntity(
            idRegistro = id, nombre = "=IMPORTXML(A1)", rol = ParticipanteJornadaEntity.ROL_VOLUNTARIO
        )
        assertTrue("Hay un participante inválido" in validar(participantes = conFormula))
        assertTrue(
            "Nombre del encargado de pesaje inválido" in validar(jornadaValida.copy(encargadoPesaje = "+Rosa"))
        )
        assertTrue(
            "El motivo de la foto de inicio tiene caracteres no permitidos" in
                validar(jornadaValida.copy(motivoOmisionInicio = "@sin luz"))
        )
    }

    @Test
    fun issue60_guia_de_despacho_valida_o_vacia() {
        assertEquals(emptyList<String>(), validar(jornadaValida.copy(nroGuiaDespacho = "GD-2026-0041")))
        assertEquals(emptyList<String>(), validar(jornadaValida.copy(nroGuiaDespacho = null)))
    }

    @Test
    fun issue60_guia_de_despacho_con_formula_o_muy_larga_se_rechaza() {
        listOf("=IMPORTXML(1)", "-123", "GD 2026", "GD-2026-00411", "123/45").forEach { guia ->
            assertTrue(guia, "N° de guía de despacho inválido" in validar(jornadaValida.copy(nroGuiaDespacho = guia)))
        }
    }

    @Test
    fun foto_omitida_sin_motivo() {
        val problemas = validar(jornadaValida.copy(motivoOmisionSacos = "   "))
        assertEquals(listOf("Falta el motivo para omitir la foto de sacos"), problemas)
    }

    @Test
    fun foto_tomada_no_exige_motivo() {
        assertEquals(emptyList<String>(), validar(jornadaValida.copy(pathFotoInicio = "/x/inicio.jpg", motivoOmisionInicio = null)))
    }

    @Test
    fun sector_de_otra_playa_o_sin_sectores() {
        assertTrue(validar(jornadaValida.copy(sectoresIdsCsv = "SECTOR-99")).contains("Hay un sector que no pertenece a la playa elegida"))
        assertTrue(validar(jornadaValida.copy(sectoresIdsCsv = "")).contains("Falta al menos 1 sector"))
    }

    @Test
    fun fecha_fuera_de_rango_y_hora_que_no_calza() {
        assertTrue(validar(jornadaValida.copy(fechaJornada = "2026-08-01")).any { it.startsWith("La fecha de la jornada está fuera de rango") })
        assertTrue(validar(jornadaValida.copy(fechaJornada = "2026-11-14")).contains("La hora de inicio no corresponde a la fecha de la jornada"))
    }

    @Test
    fun duracion_mayor_a_12_horas() {
        val j = jornadaValida.copy(horaInicioMs = ahora - 13 * 3_600_000L, horaFinMs = ahora)
        assertTrue(validar(j).contains("Horas de inicio/fin inválidas (máximo 12 h de limpieza)"))
    }

    @Test
    fun pesaje_y_ley_20949() {
        assertTrue(validar(jornadaValida.copy(cantidadSacos = 1)).contains("El promedio por saco supera 25 kg (Ley 20.949)"))
        assertTrue(validar(jornadaValida.copy(cantidadSacos = 0)).contains("La cantidad de sacos debe estar entre 1 y 100"))
        assertTrue(validar(jornadaValida.copy(encargadoPesaje = " ")).contains("Falta el encargado de pesaje"))
    }

    @Test
    fun no_aplicable_solo_con_cero_kg() {
        val j = jornadaValida.copy(destinoBasuraReciclable = JornadaEntity.DESTINO_RECICLABLE_NO_APLICA)
        assertTrue(validar(j).contains("\"No aplicable\" solo corresponde cuando hay 0 kg"))
    }

    @Test
    fun participantes() {
        assertTrue(validar(participantes = participantes.take(1)).contains("Se requiere 1 líder y al menos 1 voluntario"))
        val repetidos = participantes + ParticipanteJornadaEntity(idRegistro = id, nombre = " luis ", rol = ParticipanteJornadaEntity.ROL_VOLUNTARIO)
        assertTrue(validar(participantes = repetidos).contains("Hay nombres de participantes repetidos"))
    }

    @Test
    fun herramientas_y_sin_herramientas() {
        assertTrue(validar(herramientas = emptyList()).any { it.startsWith("Falta al menos 1 herramienta") })
        assertTrue(validar(jornadaValida.copy(sinHerramientas = true)).contains("Una jornada sin herramientas no puede tener herramientas"))
        assertEquals(emptyList<String>(), validar(jornadaValida.copy(sinHerramientas = true), herramientas = emptyList()))
        val pesa = herramientas.map { it.copy(qrCodigo = "INV-01-PESA-01") }
        assertTrue(validar(herramientas = pesa).contains("Hay una herramienta que no está en el catálogo"))
    }

    @Test
    fun basura_fuera_de_tope() {
        assertTrue(validar(basura = basura.map { it.copy(cantidad = 5_001) }).contains("Las cantidades de basura deben estar entre 0 y 5.000"))
    }
}
