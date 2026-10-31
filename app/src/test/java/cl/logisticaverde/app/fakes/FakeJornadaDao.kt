package cl.logisticaverde.app.fakes

import cl.logisticaverde.app.data.local.db.JornadaDao
import cl.logisticaverde.app.data.local.entity.BasuraRecolectadaEntity
import cl.logisticaverde.app.data.local.entity.HerramientaJornadaEntity
import cl.logisticaverde.app.data.local.entity.JornadaEntity
import cl.logisticaverde.app.data.local.entity.ParticipanteJornadaEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.math.max

class FakeJornadaDao : JornadaDao {

    private val jornadasFlow = MutableStateFlow<List<JornadaEntity>>(emptyList())
    private val herramientasJornadaFlow = MutableStateFlow<List<HerramientaJornadaEntity>>(emptyList())
    private val basuraFlow = MutableStateFlow<List<BasuraRecolectadaEntity>>(emptyList())
    private val participantesFlow = MutableStateFlow<List<ParticipanteJornadaEntity>>(emptyList())
    private var nextHerramientaId = 1
    private var nextParticipanteId = 1

    override fun observeJornadaEnCurso(): Flow<JornadaEntity?> =
        jornadasFlow.map { list -> list.find { it.estadoSync == JornadaEntity.SYNC_EN_CURSO } }

    override fun observeJornadaById(idRegistro: String): Flow<JornadaEntity?> =
        jornadasFlow.map { list -> list.find { it.idRegistro == idRegistro } }

    override suspend fun getJornadaById(idRegistro: String): JornadaEntity? =
        jornadasFlow.value.find { it.idRegistro == idRegistro }

    override suspend fun marcarErrorSync(idRegistro: String, motivo: String) {
        jornadasFlow.update { current ->
            current.map {
                if (it.idRegistro == idRegistro) it.copy(estadoSync = JornadaEntity.SYNC_ERROR, motivoErrorSync = motivo) else it
            }
        }
    }

    override fun observeUltimaJornadaCerrada(): Flow<JornadaEntity?> =
        jornadasFlow.map { list ->
            list.filter { it.estadoSync != JornadaEntity.SYNC_EN_CURSO }.maxByOrNull { it.horaInicioMs }
        }

    override fun observeJornadasRechazadas(): Flow<List<JornadaEntity>> =
        jornadasFlow.map { list -> list.filter { it.estadoSync == JornadaEntity.SYNC_ERROR }.sortedByDescending { it.horaInicioMs } }

    override fun observeRechazadasCount(): Flow<Int> =
        jornadasFlow.map { list -> list.count { it.estadoSync == JornadaEntity.SYNC_ERROR } }

    override fun observeUltimasJornadasCerradas(limite: Int): Flow<List<JornadaEntity>> =
        jornadasFlow.map { list ->
            list.filter { it.estadoSync == JornadaEntity.SYNC_PENDIENTE || it.estadoSync == JornadaEntity.SYNC_SINCRONIZADO }
                .sortedByDescending { it.horaInicioMs }
                .take(limite)
        }

    override fun observePendingSyncCount(): Flow<Int> =
        jornadasFlow.map { list -> list.count { it.estadoSync == JornadaEntity.SYNC_PENDIENTE } }

    override suspend fun getJornadasPendientesSync(): List<JornadaEntity> =
        jornadasFlow.value.filter { it.estadoSync == JornadaEntity.SYNC_PENDIENTE }

    override suspend fun insertJornada(jornada: JornadaEntity) {
        jornadasFlow.update { current ->
            current.filterNot { it.idRegistro == jornada.idRegistro } + jornada
        }
    }

    override suspend fun updateJornada(jornada: JornadaEntity) {
        insertJornada(jornada)
    }

    override suspend fun updatePantallaActual(idRegistro: String, pantalla: Int) {
        jornadasFlow.update { current ->
            current.map { if (it.idRegistro == idRegistro) it.copy(pantallaActual = pantalla) else it }
        }
    }

    override suspend fun updateKmRecorridos(idRegistro: String, kmRecorridos: Float) {
        jornadasFlow.update { current ->
            current.map {
                if (it.idRegistro == idRegistro && it.horaFinMs == 0L) it.copy(kmRecorridos = minOf(kmRecorridos, 30f)) else it
            }
        }
    }

    override suspend fun updateCoordenadaInicialSiVacia(idRegistro: String, lat: Double, lng: Double) {
        jornadasFlow.update { current ->
            current.map {
                if (it.idRegistro == idRegistro && it.latitudInicial == 0.0 && it.longitudInicial == 0.0) {
                    it.copy(latitudInicial = lat, longitudInicial = lng)
                } else {
                    it
                }
            }
        }
    }

    override suspend fun updateEstadoSync(idRegistro: String, nuevoEstado: String) {
        jornadasFlow.update { current ->
            current.map { if (it.idRegistro == idRegistro) it.copy(estadoSync = nuevoEstado, motivoErrorSync = null) else it }
        }
    }

    override fun observeParticipantes(idRegistro: String): Flow<List<ParticipanteJornadaEntity>> =
        participantesFlow.map { list -> list.filter { it.idRegistro == idRegistro } }

    override suspend fun getParticipantesList(idRegistro: String): List<ParticipanteJornadaEntity> =
        participantesFlow.value.filter { it.idRegistro == idRegistro }

    override suspend fun insertParticipante(participante: ParticipanteJornadaEntity): Long {
        // Simula el índice único (id_registro, nombre_normalizado)
        val duplicate = participantesFlow.value.any {
            it.idRegistro == participante.idRegistro && it.nombreNormalizado == participante.nombreNormalizado
        }
        if (duplicate) return -1L

        val assignedId = nextParticipanteId++
        participantesFlow.update { it + participante.copy(id = assignedId) }
        return assignedId.toLong()
    }

    override suspend fun deleteParticipante(id: Int) {
        participantesFlow.update { list -> list.filterNot { it.id == id } }
    }

    override fun observeHerramientasJornada(idRegistro: String): Flow<List<HerramientaJornadaEntity>> =
        herramientasJornadaFlow.map { list -> list.filter { it.idRegistro == idRegistro } }

    override suspend fun getHerramientasJornadaList(idRegistro: String): List<HerramientaJornadaEntity> =
        herramientasJornadaFlow.value.filter { it.idRegistro == idRegistro }

    override suspend fun insertHerramientaJornada(herramienta: HerramientaJornadaEntity): Long {
        val duplicate = herramientasJornadaFlow.value.any {
            it.idRegistro == herramienta.idRegistro && it.qrCodigo == herramienta.qrCodigo
        }
        if (duplicate) return -1L

        val assignedId = nextHerramientaId++
        val withId = herramienta.copy(id = assignedId)
        herramientasJornadaFlow.update { it + withId }
        return assignedId.toLong()
    }

    override suspend fun getQrHerramientasNoSincronizadas(): List<String> {
        val noSincronizadas = jornadasFlow.value
            .filter { it.estadoSync != JornadaEntity.SYNC_SINCRONIZADO }
            .map { it.idRegistro }
            .toSet()
        return herramientasJornadaFlow.value.filter { it.idRegistro in noSincronizadas }.map { it.qrCodigo }.distinct()
    }

    override suspend fun deleteHerramientasJornada(idRegistro: String) {
        herramientasJornadaFlow.update { list -> list.filterNot { it.idRegistro == idRegistro } }
    }

    override suspend fun deleteHerramientaJornadaById(id: Int) {
        herramientasJornadaFlow.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun updateEstadoHerramientaJornada(id: Int, estadoFinal: String) {
        herramientasJornadaFlow.update { list ->
            list.map { if (it.id == id) it.copy(estadoFinal = estadoFinal) else it }
        }
    }

    override fun observeBasuraRecolectada(idRegistro: String): Flow<List<BasuraRecolectadaEntity>> =
        basuraFlow.map { list -> list.filter { it.idRegistro == idRegistro } }

    override suspend fun getBasuraRecolectadaList(idRegistro: String): List<BasuraRecolectadaEntity> =
        basuraFlow.value.filter { it.idRegistro == idRegistro }

    override suspend fun upsertBasuraRecolectada(items: List<BasuraRecolectadaEntity>) {
        basuraFlow.update { current ->
            val keysToReplace = items.map { it.idRegistro to it.idTipoBasura }.toSet()
            current.filterNot { (it.idRegistro to it.idTipoBasura) in keysToReplace } + items
        }
    }

    override suspend fun modificarCantidadBasura(idRegistro: String, idTipoBasura: String, delta: Int) {
        basuraFlow.update { current ->
            current.map { item ->
                if (item.idRegistro == idRegistro && item.idTipoBasura == idTipoBasura) {
                    item.copy(cantidad = minOf(5000, max(0, item.cantidad + delta)))
                } else {
                    item
                }
            }
        }
    }
}
