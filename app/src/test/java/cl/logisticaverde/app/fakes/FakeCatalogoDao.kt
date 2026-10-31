package cl.logisticaverde.app.fakes

import cl.logisticaverde.app.data.local.db.CatalogoDao
import cl.logisticaverde.app.data.local.db.CatalogoSeedData
import cl.logisticaverde.app.data.local.entity.HerramientaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.PlayaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.SectorCatalogoEntity
import cl.logisticaverde.app.data.local.entity.TipoBasuraCatalogoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeCatalogoDao : CatalogoDao {

    private val playasFlow = MutableStateFlow<List<PlayaCatalogoEntity>>(CatalogoSeedData.playas)
    private val sectoresFlow = MutableStateFlow<List<SectorCatalogoEntity>>(CatalogoSeedData.sectoresPlayaGrande)
    private val herramientasFlow = MutableStateFlow<List<HerramientaCatalogoEntity>>(CatalogoSeedData.herramientasQr)
    private val tiposBasuraFlow = MutableStateFlow<List<TipoBasuraCatalogoEntity>>(CatalogoSeedData.tiposBasura)

    override fun observePlayas(): Flow<List<PlayaCatalogoEntity>> = playasFlow

    override suspend fun upsertPlayas(playas: List<PlayaCatalogoEntity>) {
        playasFlow.value = playas
    }

    override fun observeSectores(): Flow<List<SectorCatalogoEntity>> = sectoresFlow

    override suspend fun getSectoresList(): List<SectorCatalogoEntity> = sectoresFlow.value

    override suspend fun upsertSectores(sectores: List<SectorCatalogoEntity>) {
        sectoresFlow.value = sectores
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

    override suspend fun upsertHerramientas(herramientas: List<HerramientaCatalogoEntity>) {
        herramientasFlow.value = herramientas
    }

    override fun observeTiposBasura(): Flow<List<TipoBasuraCatalogoEntity>> = tiposBasuraFlow

    override suspend fun getTiposBasuraList(): List<TipoBasuraCatalogoEntity> = tiposBasuraFlow.value

    override suspend fun upsertTiposBasura(tipos: List<TipoBasuraCatalogoEntity>) {
        tiposBasuraFlow.value = tipos
    }
}
