package cl.logisticaverde.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import cl.logisticaverde.app.data.local.entity.BasuraRecolectadaEntity
import cl.logisticaverde.app.data.local.entity.HerramientaJornadaEntity
import cl.logisticaverde.app.data.local.entity.JornadaEntity
import cl.logisticaverde.app.data.local.entity.ParticipanteJornadaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JornadaDao {

    // --- JORNADA ACTIVA Y SINCRONIZACIÓN (Pantalla 1) ---
    @Query("SELECT * FROM jornada WHERE estado_sync = 'EN_CURSO' LIMIT 1")
    fun observeJornadaEnCurso(): Flow<JornadaEntity?>

    @Query("SELECT * FROM jornada WHERE id_registro = :idRegistro LIMIT 1")
    fun observeJornadaById(idRegistro: String): Flow<JornadaEntity?>

    @Query("SELECT * FROM jornada WHERE id_registro = :idRegistro LIMIT 1")
    suspend fun getJornadaById(idRegistro: String): JornadaEntity?

    /** P1 tarjeta "Última jornada": la última cerrada (pendiente, sincronizada o rechazada). */
    @Query(
        """
        SELECT * FROM jornada WHERE estado_sync IN ('PENDIENTE_SYNC', 'SINCRONIZADO', 'ERROR_SYNC')
        ORDER BY hora_inicio_ms DESC LIMIT 1
        """
    )
    fun observeUltimaJornadaCerrada(): Flow<JornadaEntity?>

    /** P1 "Ver últimos resúmenes": hasta 5 jornadas cerradas, sincronizadas o no (ISSUE-25). */
    @Query(
        """
        SELECT * FROM jornada WHERE estado_sync IN ('PENDIENTE_SYNC', 'SINCRONIZADO')
        ORDER BY hora_inicio_ms DESC LIMIT :limite
        """
    )
    fun observeUltimasJornadasCerradas(limite: Int = 5): Flow<List<JornadaEntity>>

    @Query("SELECT COUNT(*) FROM jornada WHERE estado_sync = 'PENDIENTE_SYNC'")
    fun observePendingSyncCount(): Flow<Int>

    @Query("SELECT * FROM jornada WHERE estado_sync = 'PENDIENTE_SYNC' ORDER BY hora_inicio_ms ASC")
    suspend fun getJornadasPendientesSync(): List<JornadaEntity>

    /**
     * ISSUE-59: ABORT y no REPLACE. En SQLite, REPLACE borra la fila existente antes de insertar,
     * y ese borrado arrastra en cascada participantes, herramientas y conteos. Con ABORT, un ID
     * repetido lanza un error en vez de borrar una jornada.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertJornada(jornada: JornadaEntity)

    @Update
    suspend fun updateJornada(jornada: JornadaEntity)

    @Query("UPDATE jornada SET pantalla_actual = :pantalla WHERE id_registro = :idRegistro")
    suspend fun updatePantallaActual(idRegistro: String, pantalla: Int)

    // Escrito por LimpiezaLocationService: no cambia los km de una limpieza ya finalizada.
    // Tope de 30 km (TopesJornada.MAX_KM_RECORRIDOS), el mismo que valida la RPC.
    @Query("UPDATE jornada SET km_recorridos = MIN(:kmRecorridos, 30.0) WHERE id_registro = :idRegistro AND hora_fin_ms = 0")
    suspend fun updateKmRecorridos(idRegistro: String, kmRecorridos: Float)

    @Query(
        """
        UPDATE jornada SET latitud_inicial = :lat, longitud_inicial = :lng
        WHERE id_registro = :idRegistro AND latitud_inicial IS NULL
        """
    )
    suspend fun updateCoordenadaInicialSiVacia(idRegistro: String, lat: Double, lng: Double)

    /** Cambia el estado y borra el motivo de rechazo anterior (si lo había). */
    @Query("UPDATE jornada SET estado_sync = :nuevoEstado, motivo_error_sync = NULL WHERE id_registro = :idRegistro")
    suspend fun updateEstadoSync(idRegistro: String, nuevoEstado: String)

    // --- JORNADAS RECHAZADAS (ISSUE-42) ---
    @Query("UPDATE jornada SET estado_sync = 'ERROR_SYNC', motivo_error_sync = :motivo WHERE id_registro = :idRegistro")
    suspend fun marcarErrorSync(idRegistro: String, motivo: String)

    @Query("SELECT * FROM jornada WHERE estado_sync = 'ERROR_SYNC' ORDER BY hora_inicio_ms DESC")
    fun observeJornadasRechazadas(): Flow<List<JornadaEntity>>

    @Query("SELECT COUNT(*) FROM jornada WHERE estado_sync = 'ERROR_SYNC'")
    fun observeRechazadasCount(): Flow<Int>

    // --- PARTICIPANTES DE LA JORNADA (Pantallas 2 y 8) ---
    @Query("SELECT * FROM participante_jornada WHERE id_registro = :idRegistro ORDER BY id ASC")
    fun observeParticipantes(idRegistro: String): Flow<List<ParticipanteJornadaEntity>>

    @Query("SELECT * FROM participante_jornada WHERE id_registro = :idRegistro ORDER BY id ASC")
    suspend fun getParticipantesList(idRegistro: String): List<ParticipanteJornadaEntity>

    /** Devuelve -1 si el nombre ya existe en la jornada (índice único por nombre normalizado). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertParticipante(participante: ParticipanteJornadaEntity): Long

    @Query("DELETE FROM participante_jornada WHERE id = :id")
    suspend fun deleteParticipante(id: Int)

    // --- HERRAMIENTAS DE LA JORNADA (Pantallas 4 y 7) ---
    @Query("SELECT * FROM herramienta_jornada WHERE id_registro = :idRegistro ORDER BY id ASC")
    fun observeHerramientasJornada(idRegistro: String): Flow<List<HerramientaJornadaEntity>>

    @Query("SELECT * FROM herramienta_jornada WHERE id_registro = :idRegistro ORDER BY id ASC")
    suspend fun getHerramientasJornadaList(idRegistro: String): List<HerramientaJornadaEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHerramientaJornada(herramienta: HerramientaJornadaEntity): Long

    /** BR-10: herramientas cuyo estado local aún no llega a Supabase (el catálogo remoto no debe pisarlo). */
    @Query(
        """
        SELECT DISTINCT hj.qr_codigo FROM herramienta_jornada hj
        JOIN jornada j ON j.id_registro = hj.id_registro
        WHERE j.estado_sync IN ('EN_CURSO', 'PENDIENTE_SYNC', 'ERROR_SYNC')
        """
    )
    suspend fun getQrHerramientasNoSincronizadas(): List<String>

    @Query("DELETE FROM herramienta_jornada WHERE id_registro = :idRegistro")
    suspend fun deleteHerramientasJornada(idRegistro: String)

    @Query("DELETE FROM herramienta_jornada WHERE id = :id")
    suspend fun deleteHerramientaJornadaById(id: Int)

    @Query("UPDATE herramienta_jornada SET estado_final = :estadoFinal WHERE id = :id")
    suspend fun updateEstadoHerramientaJornada(id: Int, estadoFinal: String)

    // --- BASURA RECOLECTADA (Pantalla 5: Botonera -1 / +1 / +10) ---
    @Query("SELECT * FROM basura_recolectada WHERE id_registro = :idRegistro ORDER BY id_tipo_basura ASC")
    fun observeBasuraRecolectada(idRegistro: String): Flow<List<BasuraRecolectadaEntity>>

    @Query("SELECT * FROM basura_recolectada WHERE id_registro = :idRegistro ORDER BY id_tipo_basura ASC")
    suspend fun getBasuraRecolectadaList(idRegistro: String): List<BasuraRecolectadaEntity>

    @Upsert
    suspend fun upsertBasuraRecolectada(items: List<BasuraRecolectadaEntity>)

    @Query(
        """
        UPDATE basura_recolectada 
        SET cantidad = MIN(5000, MAX(0, cantidad + :delta))
        WHERE id_registro = :idRegistro AND id_tipo_basura = :idTipoBasura
        """
    )
    suspend fun modificarCantidadBasura(idRegistro: String, idTipoBasura: String, delta: Int)
}
