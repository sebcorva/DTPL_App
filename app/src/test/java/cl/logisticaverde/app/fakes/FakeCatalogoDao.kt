package cl.logisticaverde.app.fakes

import cl.logisticaverde.app.data.local.db.CatalogoDao
import cl.logisticaverde.app.data.local.db.CatalogoSeedData
import cl.logisticaverde.app.data.local.entity.BrigadaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.HerramientaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.PlayaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.SectorCatalogoEntity
import cl.logisticaverde.app.data.local.entity.TipoBasuraCatalogoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeCatalogoDao : CatalogoDao {

    private val playasFlow = MutableStateFlow<List<PlayaCatalogoEntity>>(CatalogoSeedData.playas)
    private val brigadasFlow = MutableStateFlow<List<BrigadaCatalogoEntity>>(CatalogoSeedData.brigadas)
    private val sectoresFlow = MutableStateFlow<List<SectorCatalogoEntity>>(CatalogoSeedData.sectoresPlayaGrande)
    private val herramientasFlow = MutableStateFlow<List<HerramientaCatalogoEntity>>(CatalogoSeedData.herramientasQr)
    private val tiposBasuraFlow = MutableStateFlow<List<TipoBasuraCatalogoEntity>>(CatalogoSeedData.tiposBasura)

    override fun observePlayas(): Flow<List<PlayaCatalogoEntity>> = playasFlow

    // Igual que @Upsert de Room: agrega o actualiza, no borra
    override suspend fun upsertPlayas(playas: List<PlayaCatalogoEntity>) {
        val nuevas = playas.associateBy { it.idPlaya }
        playasFlow.update { lista -> lista.filterNot { it.idPlaya in nuevas } + playas }
    }

    override suspend fun desactivarPlayasExcepto(idsVigentes: List<String>) {
        playasFlow.update { lista -> lista.map { if (it.idPlaya in idsVigentes) it else it.copy(activo = false) } }
    }

    override fun observeBrigadas(): Flow<List<BrigadaCatalogoEntity>> = brigadasFlow

    override suspend fun getBrigadasList(): List<BrigadaCatalogoEntity> = brigadasFlow.value

    override suspend fun upsertBrigadas(brigadas: List<BrigadaCatalogoEntity>) {
        val nuevas = brigadas.associateBy { it.idBrigada }
        brigadasFlow.update { lista -> lista.filterNot { it.idBrigada in nuevas } + brigadas }
    }

    override fun observeSectores(): Flow<List<SectorCatalogoEntity>> = sectoresFlow

    override suspend fun getSectoresList(): List<SectorCatalogoEntity> = sectoresFlow.value

    override suspend fun upsertSectores(sectores: List<SectorCatalogoEntity>) {
        val nuevos = sectores.associateBy { it.idSector }
        sectoresFlow.update { lista -> (lista.filterNot { it.idSector in nuevos } + sectores).sortedBy { it.idSector } }
    }

    override suspend fun desactivarSectoresExcepto(idsVigentes: List<String>) {
        sectoresFlow.update { lista -> lista.map { if (it.idSector in idsVigentes) it else it.copy(activo = false) } }
    }

    override suspend fun desactivarBrigadasExcepto(idsVigentes: List<String>) {
        brigadasFlow.update { lista -> lista.map { if (it.idBrigada in idsVigentes) it else it.copy(activo = false) } }
    }

    override fun observeHerramientasCatalogo(): Flow<List<HerramientaCatalogoEntity>> = herramientasFlow

    override suspend fun getHerramientaByQr(qrCodigo: String): HerramientaCatalogoEntity? =
        herramientasFlow.value.find { it.qrCodigo.equals(qrCodigo, ignoreCase = true) }

    override suspend fun updateEstadoHerramientaCatalogo(qrCodigo: String, nuevoEstado: String) {
        herramientasFlow.update { current ->
            current.map { item ->
                if (item.qrCodigo == qrCodigo) item.copy(estado = nuevoEstado) else item
            }
        }
    }

    // Igual que @Upsert de Room: agrega o actualiza, no borra
    override suspend fun upsertHerramientas(herramientas: List<HerramientaCatalogoEntity>) {
        val nuevas = herramientas.associateBy { it.qrCodigo }
        herramientasFlow.update { lista -> lista.filterNot { it.qrCodigo in nuevas } + herramientas }
    }

    override suspend fun desactivarHerramientasExcepto(qrVigentes: List<String>) {
        herramientasFlow.update { lista -> lista.map { if (it.qrCodigo in qrVigentes) it else it.copy(activo = false) } }
    }

    override fun observeTiposBasura(): Flow<List<TipoBasuraCatalogoEntity>> = tiposBasuraFlow

    override suspend fun getTiposBasuraList(): List<TipoBasuraCatalogoEntity> = tiposBasuraFlow.value

    override suspend fun upsertTiposBasura(tipos: List<TipoBasuraCatalogoEntity>) {
        val nuevos = tipos.associateBy { it.idTipoBasura }
        tiposBasuraFlow.update { lista -> lista.filterNot { it.idTipoBasura in nuevos } + tipos }
    }

    override suspend fun desactivarTiposBasuraExcepto(idsVigentes: List<String>) {
        tiposBasuraFlow.update { lista -> lista.map { if (it.idTipoBasura in idsVigentes) it else it.copy(activo = false) } }
    }
}
