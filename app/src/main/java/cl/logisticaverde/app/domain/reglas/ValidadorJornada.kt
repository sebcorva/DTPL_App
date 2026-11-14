package cl.logisticaverde.app.domain.reglas

import cl.logisticaverde.app.data.local.entity.BasuraRecolectadaEntity
import cl.logisticaverde.app.data.local.entity.HerramientaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.HerramientaJornadaEntity
import cl.logisticaverde.app.data.local.entity.JornadaEntity
import cl.logisticaverde.app.data.local.entity.ParticipanteJornadaEntity
import cl.logisticaverde.app.data.local.entity.SectorCatalogoEntity
import java.time.LocalDate

/**
 * ISSUE-42 (decisión 31-10-2026): antes de cerrar la jornada en P9 se aplican las MISMAS reglas
 * que la RPC `sincronizar_jornada` (docs/supabase_schema.sql §4), para que el servidor no tenga
 * motivo para rechazarla. Si una regla cambia en el SQL, cambiarla también aquí.
 *
 * Devuelve la lista de problemas (vacía = la jornada se puede cerrar).
 */
object ValidadorJornada {

    val BRIGADAS = listOf("Brigada A", "Brigada B", "Brigada C", "Brigada D")
    private const val DIAS_ATRAS_PERMITIDOS = 60L
    private const val UN_DIA_MS = 24L * 60 * 60 * 1000

    fun validar(
        jornada: JornadaEntity,
        participantes: List<ParticipanteJornadaEntity>,
        herramientas: List<HerramientaJornadaEntity>,
        basura: List<BasuraRecolectadaEntity>,
        sectoresCatalogo: List<SectorCatalogoEntity>,
        herramientasCatalogo: List<HerramientaCatalogoEntity>,
        ahoraMs: Long
    ): List<String> {
        val problemas = mutableListOf<String>()
        fun regla(cumple: Boolean, problema: String) {
            if (!cumple) problemas += problema
        }

        // ---------- Cabecera ----------
        regla(jornada.idRegistro.matches(Regex("^REG-[0-9]{8}-[A-Z0-9]{4,12}$")), "Identificador de jornada inválido")
        regla(jornada.brigada in BRIGADAS, "Brigada inválida")

        val hoy = FechaJornada.hoy(ahoraMs)
        val fecha = runCatching { LocalDate.parse(jornada.fechaJornada) }.getOrNull()
        regla(
            fecha != null && !fecha.isAfter(hoy.plusDays(1)) && !fecha.isBefore(hoy.minusDays(DIAS_ATRAS_PERMITIDOS)),
            "La fecha de la jornada está fuera de rango (revisa la hora del teléfono)"
        )

        regla(jornada.idPlaya.isNotBlank(), "Falta la playa")
        val sectores = jornada.sectoresIdsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        regla(sectores.isNotEmpty(), "Falta al menos 1 sector")
        val sectoresDeLaPlaya = sectoresCatalogo.filter { it.idPlaya == jornada.idPlaya }.map { it.idSector }.toSet()
        regla(sectores.all { it in sectoresDeLaPlaya }, "Hay un sector que no pertenece a la playa elegida")

        // ---------- Horas, GPS y topes ----------
        val inicio = jornada.horaInicioMs
        val fin = jornada.horaFinMs
        regla(
            inicio > 0 && fin >= inicio && fin - inicio <= TopesJornada.MAX_DURACION_MS,
            "Horas de inicio/fin inválidas (máximo 12 h de limpieza)"
        )
        regla(inicio <= ahoraMs + UN_DIA_MS, "La hora de inicio está en el futuro (revisa la hora del teléfono)")
        if (inicio > 0 && fecha != null) {
            val fechaInicio = FechaJornada.hoy(inicio)
            regla(
                !fechaInicio.isBefore(fecha) && !fechaInicio.isAfter(fecha.plusDays(1)),
                "La hora de inicio no corresponde a la fecha de la jornada"
            )
        }
        regla(jornada.latitudInicial in -90.0..90.0 && jornada.longitudInicial in -180.0..180.0, "Coordenadas inválidas")
        regla(jornada.kmRecorridos in 0f..TopesJornada.MAX_KM_RECORRIDOS, "Kilómetros fuera de rango (0 a 30)")

        // ---------- Pesaje ----------
        val kgNormal = jornada.kgBasuraNormal
        val kgReciclable = jornada.kgBasuraReciclable
        val kgTotal = kgNormal + kgReciclable
        regla(jornada.cantidadSacos in 1..TopesJornada.MAX_SACOS, "La cantidad de sacos debe estar entre 1 y 100")
        regla(kgNormal >= 0f && kgReciclable >= 0f && kgTotal > 0f, "Los kilos deben sumar más de 0")
        regla(kgTotal <= TopesJornada.MAX_KG_TOTALES, "Máximo 2.500 kg por jornada")
        if (jornada.cantidadSacos > 0) {
            regla(kgTotal / jornada.cantidadSacos <= 25f, "El promedio por saco supera 25 kg (Ley 20.949)")
        }
        val encargado = normalizar(jornada.encargadoPesaje)
        regla(encargado.isNotEmpty(), "Falta el encargado de pesaje")
        if (encargado.isNotEmpty()) {
            regla(ReglasTexto.esNombreValido(encargado), "Nombre del encargado de pesaje inválido")
        }

        // ---------- Destinos ----------
        regla(jornada.destinoBasuraNormal in JornadaEntity.OPCIONES_DESTINO_NORMAL, "Destino de basura normal inválido")
        regla(
            jornada.destinoBasuraReciclable in JornadaEntity.OPCIONES_DESTINO_RECICLABLE,
            "Destino de basura reciclable inválido"
        )
        regla(
            (kgNormal == 0f) == (jornada.destinoBasuraNormal == JornadaEntity.DESTINO_NORMAL_NO_APLICA) &&
                (kgReciclable == 0f) == (jornada.destinoBasuraReciclable == JornadaEntity.DESTINO_RECICLABLE_NO_APLICA),
            "\"No aplicable\" solo corresponde cuando hay 0 kg"
        )
        // ISSUE-60: la guía solo viaja con destino DIMAO (la RPC la descarta en otro caso)
        val guia = jornada.nroGuiaDespacho?.trim().orEmpty()
        if (guia.isNotEmpty() && jornada.destinoBasuraNormal == JornadaEntity.DESTINO_NORMAL_DIMAO) {
            regla(ReglasTexto.esGuiaValida(guia), "N° de guía de despacho inválido")
        }

        // ---------- Fotos: tomada, o con motivo ----------
        listOf(
            Triple(jornada.pathFotoInicio, jornada.urlFotoInicio, jornada.motivoOmisionInicio) to "inicio",
            Triple(jornada.pathFotoLimpio, jornada.urlFotoLimpio, jornada.motivoOmisionLimpio) to "sector limpio",
            Triple(jornada.pathFotoSacos, jornada.urlFotoSacos, jornada.motivoOmisionSacos) to "sacos"
        ).forEach { (foto, nombre) ->
            val (path, url, motivo) = foto
            if (path.isBlank() && url.isNullOrBlank()) {
                val m = normalizar(motivo.orEmpty())
                regla(m.isNotEmpty(), "Falta el motivo para omitir la foto de $nombre")
                if (m.isNotEmpty()) {
                    regla(ReglasTexto.esMotivoValido(m), "El motivo de la foto de $nombre tiene caracteres no permitidos")
                }
            }
        }

        // ---------- Participantes ----------
        regla(
            participantes.all {
                ReglasTexto.esNombreValido(it.nombre) &&
                    it.rol in listOf(ParticipanteJornadaEntity.ROL_LIDER, ParticipanteJornadaEntity.ROL_VOLUNTARIO)
            },
            "Hay un participante inválido"
        )
        val lideres = participantes.count { it.rol == ParticipanteJornadaEntity.ROL_LIDER }
        val voluntarios = participantes.count { it.rol == ParticipanteJornadaEntity.ROL_VOLUNTARIO }
        regla(lideres == 1 && voluntarios >= 1, "Se requiere 1 líder y al menos 1 voluntario")
        regla(participantes.size <= TopesJornada.MAX_PARTICIPANTES, "Máximo 60 integrantes por brigada")
        regla(
            participantes.map { normalizar(it.nombre).lowercase() }.let { it.size == it.toSet().size },
            "Hay nombres de participantes repetidos"
        )

        // ---------- Herramientas ----------
        if (jornada.sinHerramientas) {
            regla(herramientas.isEmpty(), "Una jornada sin herramientas no puede tener herramientas")
        } else {
            regla(herramientas.isNotEmpty(), "Falta al menos 1 herramienta (o marcar \"sin herramientas\")")
        }
        val qrCatalogo = herramientasCatalogo.map { it.qrCodigo }.toSet()
        regla(herramientas.all { it.qrCodigo in qrCatalogo }, "Hay una herramienta que no está en el catálogo")
        regla(
            herramientas.all { it.estadoFinal in HerramientaCatalogoEntity.ESTADOS_DISPONIBLES },
            "Falta el estado final de una herramienta"
        )
        regla(herramientas.map { it.qrCodigo }.let { it.size == it.toSet().size }, "Hay una herramienta repetida")

        // ---------- Basura ----------
        regla(
            basura.all { it.cantidad in 0..TopesJornada.MAX_CANTIDAD_POR_RESIDUO },
            "Las cantidades de basura deben estar entre 0 y 5.000"
        )
        regla(basura.map { it.idTipoBasura }.let { it.size == it.toSet().size }, "Hay un tipo de basura repetido")

        return problemas
    }

    private fun normalizar(texto: String) = texto.trim().replace(Regex("\\s+"), " ")
}
