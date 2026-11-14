package cl.logisticaverde.app.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import cl.logisticaverde.app.data.local.entity.BrigadaCatalogoEntity
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

    @Query("DELETE FROM playa_catalogo WHERE id_playa NOT IN (:idsVigentes)")
    suspend fun deletePlayasExcepto(idsVigentes: List<String>)

    /** ISSUE-43: deja las playas locales idénticas a las del servidor (borra las que ya no vienen). */
    @Transaction
    suspend fun reemplazarPlayas(playas: List<PlayaCatalogoEntity>) {
        deletePlayasExcepto(playas.map { it.idPlaya })
        upsertPlayas(playas)
    }

    // --- BRIGADAS (ISSUE-62: cada una pertenece a una playa) ---
    @Query("SELECT * FROM brigada_catalogo ORDER BY nombre_brigada ASC")
    fun observeBrigadas(): Flow<List<BrigadaCatalogoEntity>>

    @Query("SELECT * FROM brigada_catalogo ORDER BY nombre_brigada ASC")
    suspend fun getBrigadasList(): List<BrigadaCatalogoEntity>

    @Upsert
    suspend fun upsertBrigadas(brigadas: List<BrigadaCatalogoEntity>)

    // --- SECTORES (cada uno pertenece a una playa) ---
    @Query("SELECT * FROM sector_catalogo ORDER BY id_sector ASC")
    fun observeSectores(): Flow<List<SectorCatalogoEntity>>

    @Query("SELECT * FROM sector_catalogo ORDER BY id_sector ASC")
    suspend fun getSectoresList(): List<SectorCatalogoEntity>

    @Upsert
    suspend fun upsertSectores(sectores: List<SectorCatalogoEntity>)

    @Query("DELETE FROM sector_catalogo WHERE id_sector NOT IN (:idsVigentes)")
    suspend fun deleteSectoresExcepto(idsVigentes: List<String>)

    /** ISSUE-43: deja los sectores locales idénticos a los del servidor (borra los que ya no vienen). */
    @Transaction
    suspend fun reemplazarSectores(sectores: List<SectorCatalogoEntity>) {
        deleteSectoresExcepto(sectores.map { it.idSector })
        upsertSectores(sectores)
    }

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
