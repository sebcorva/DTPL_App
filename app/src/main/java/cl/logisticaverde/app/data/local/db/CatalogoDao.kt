package cl.logisticaverde.app.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import cl.logisticaverde.app.data.local.entity.HerramientaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.PlayaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.SectorCatalogoEntity
import cl.logisticaverde.app.data.local.entity.TipoBasuraCatalogoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogoDao {

    // --- PLAYAS (ISSUE-32) ---
    @Query("SELECT * FROM playa_catalogo ORDER BY nombre_playa ASC")
    fun observePlayas(): Flow<List<PlayaCatalogoEntity>>

    @Upsert
    suspend fun upsertPlayas(playas: List<PlayaCatalogoEntity>)

    // --- SECTORES (cada uno pertenece a una playa) ---
    @Query("SELECT * FROM sector_catalogo ORDER BY id_sector ASC")
    fun observeSectores(): Flow<List<SectorCatalogoEntity>>

    @Query("SELECT * FROM sector_catalogo ORDER BY id_sector ASC")
    suspend fun getSectoresList(): List<SectorCatalogoEntity>

    @Upsert
    suspend fun upsertSectores(sectores: List<SectorCatalogoEntity>)

    // --- HERRAMIENTAS QR ---
    @Query("SELECT * FROM herramienta_catalogo ORDER BY qr_codigo ASC")
    fun observeHerramientasCatalogo(): Flow<List<HerramientaCatalogoEntity>>

    @Query("SELECT * FROM herramienta_catalogo WHERE qr_codigo = :qrCodigo LIMIT 1")
    suspend fun getHerramientaByQr(qrCodigo: String): HerramientaCatalogoEntity?

    @Query("UPDATE herramienta_catalogo SET estado = :nuevoEstado WHERE qr_codigo = :qrCodigo")
    suspend fun updateEstadoHerramientaCatalogo(qrCodigo: String, nuevoEstado: String)

    @Upsert
    suspend fun upsertHerramientas(herramientas: List<HerramientaCatalogoEntity>)

    // --- TIPOS DE BASURA (Con categoria_origen por defecto) ---
    @Query("SELECT * FROM tipo_basura_catalogo ORDER BY id_tipo_basura ASC")
    fun observeTiposBasura(): Flow<List<TipoBasuraCatalogoEntity>>

    @Query("SELECT * FROM tipo_basura_catalogo ORDER BY id_tipo_basura ASC")
    suspend fun getTiposBasuraList(): List<TipoBasuraCatalogoEntity>

    @Upsert
    suspend fun upsertTiposBasura(tipos: List<TipoBasuraCatalogoEntity>)
}
