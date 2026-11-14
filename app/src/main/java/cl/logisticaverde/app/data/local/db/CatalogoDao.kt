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
/**
 * ISSUE-63: política única para los 5 catálogos. Al descargarlos de Supabase, lo que ya no viene
 * del servidor se marca inactivo en el teléfono (nunca se borra: las jornadas locales lo referencian);
 * si vuelve a venir, el upsert lo reactiva con el `is_activo` del servidor.
 */
interface CatalogoDao {

    // --- PLAYAS (ISSUE-32) ---
    @Query("SELECT * FROM playa_catalogo ORDER BY nombre_playa ASC")
    fun observePlayas(): Flow<List<PlayaCatalogoEntity>>

    @Upsert
    suspend fun upsertPlayas(playas: List<PlayaCatalogoEntity>)

    @Query("UPDATE playa_catalogo SET is_activo = 0 WHERE id_playa NOT IN (:idsVigentes)")
    suspend fun desactivarPlayasExcepto(idsVigentes: List<String>)

    @Transaction
    suspend fun sincronizarPlayas(remotas: List<PlayaCatalogoEntity>) {
        desactivarPlayasExcepto(remotas.map { it.idPlaya })
        upsertPlayas(remotas)
    }

    // --- BRIGADAS (ISSUE-62: cada una pertenece a una playa) ---
    @Query("SELECT * FROM brigada_catalogo ORDER BY nombre_brigada ASC")
    fun observeBrigadas(): Flow<List<BrigadaCatalogoEntity>>

    @Query("SELECT * FROM brigada_catalogo ORDER BY nombre_brigada ASC")
    suspend fun getBrigadasList(): List<BrigadaCatalogoEntity>

    @Upsert
    suspend fun upsertBrigadas(brigadas: List<BrigadaCatalogoEntity>)

    @Query("UPDATE brigada_catalogo SET is_activo = 0 WHERE id_brigada NOT IN (:idsVigentes)")
    suspend fun desactivarBrigadasExcepto(idsVigentes: List<String>)

    @Transaction
    suspend fun sincronizarBrigadas(remotas: List<BrigadaCatalogoEntity>) {
        desactivarBrigadasExcepto(remotas.map { it.idBrigada })
        upsertBrigadas(remotas)
    }

    // --- SECTORES (cada uno pertenece a una playa) ---
    @Query("SELECT * FROM sector_catalogo ORDER BY id_sector ASC")
    fun observeSectores(): Flow<List<SectorCatalogoEntity>>

    @Query("SELECT * FROM sector_catalogo ORDER BY id_sector ASC")
    suspend fun getSectoresList(): List<SectorCatalogoEntity>

    @Upsert
    suspend fun upsertSectores(sectores: List<SectorCatalogoEntity>)

    @Query("UPDATE sector_catalogo SET is_activo = 0 WHERE id_sector NOT IN (:idsVigentes)")
    suspend fun desactivarSectoresExcepto(idsVigentes: List<String>)

    @Transaction
    suspend fun sincronizarSectores(remotos: List<SectorCatalogoEntity>) {
        desactivarSectoresExcepto(remotos.map { it.idSector })
        upsertSectores(remotos)
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

    @Query("UPDATE herramienta_catalogo SET is_activo = 0 WHERE qr_codigo NOT IN (:qrVigentes)")
    suspend fun desactivarHerramientasExcepto(qrVigentes: List<String>)

    @Transaction
    suspend fun sincronizarHerramientas(remotas: List<HerramientaCatalogoEntity>) {
        desactivarHerramientasExcepto(remotas.map { it.qrCodigo })
        upsertHerramientas(remotas)
    }

    // --- TIPOS DE BASURA (Con categoria_origen por defecto) ---
    @Query("SELECT * FROM tipo_basura_catalogo ORDER BY id_tipo_basura ASC")
    fun observeTiposBasura(): Flow<List<TipoBasuraCatalogoEntity>>

    @Query("SELECT * FROM tipo_basura_catalogo ORDER BY id_tipo_basura ASC")
    suspend fun getTiposBasuraList(): List<TipoBasuraCatalogoEntity>

    @Upsert
    suspend fun upsertTiposBasura(tipos: List<TipoBasuraCatalogoEntity>)

    @Query("UPDATE tipo_basura_catalogo SET is_activo = 0 WHERE id_tipo_basura NOT IN (:idsVigentes)")
    suspend fun desactivarTiposBasuraExcepto(idsVigentes: List<String>)

    @Transaction
    suspend fun sincronizarTiposBasura(remotos: List<TipoBasuraCatalogoEntity>) {
        desactivarTiposBasuraExcepto(remotos.map { it.idTipoBasura })
        upsertTiposBasura(remotos)
    }
}
