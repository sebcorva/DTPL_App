package cl.logisticaverde.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cl.logisticaverde.app.data.local.entity.BasuraRecolectadaEntity
import cl.logisticaverde.app.data.local.entity.HerramientaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.HerramientaJornadaEntity
import cl.logisticaverde.app.data.local.entity.JornadaEntity
import cl.logisticaverde.app.data.local.entity.ParticipanteJornadaEntity
import cl.logisticaverde.app.data.local.entity.PlayaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.SectorCatalogoEntity
import cl.logisticaverde.app.data.local.entity.TipoBasuraCatalogoEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Política de migraciones (ISSUE-17, RNF-06):
 * - v1/v2 → v3 fue destructiva (se eliminó la tabla `usuario`).
 * - v3 → v4 también es destructiva, por decisión del 31-10-2026 (playas, brigada, fecha de jornada;
 *   los datos locales eran de prueba).
 * - v4 → v5 también es destructiva, por decisión del 31-10-2026 (ISSUE-42: `motivo_error_sync`;
 *   aún no hay datos reales).
 * - v5 → v6 también es destructiva, por decisión del 08-11-2026 (ISSUE-43: columna `is_activo` en los
 *   4 catálogos; solo había datos de prueba, sin datos sensibles). Es la ÚLTIMA vez: el piloto parte con la v6.
 * - Desde la v6, cualquier cambio de esquema exige subir `version`, agregar una `Migration`
 *   explícita en [MIGRACIONES] y un test con `MigrationTestHelper` contra `app/schemas/`.
 *   Nunca volver a usar `fallbackToDestructiveMigration()`: borraría jornadas sin sincronizar.
 */
@Database(
    entities = [
        PlayaCatalogoEntity::class,
        SectorCatalogoEntity::class,
        HerramientaCatalogoEntity::class,
        TipoBasuraCatalogoEntity::class,
        JornadaEntity::class,
        ParticipanteJornadaEntity::class,
        HerramientaJornadaEntity::class,
        BasuraRecolectadaEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class LogisticaVerdeDatabase : RoomDatabase() {

    abstract fun catalogoDao(): CatalogoDao
    abstract fun jornadaDao(): JornadaDao

    companion object {
        @Volatile
        private var INSTANCE: LogisticaVerdeDatabase? = null

        // Migraciones explícitas a partir de la v6 (ej. MIGRATION_6_7)
        private val MIGRACIONES = emptyArray<Migration>()

        private val dbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun getInstance(context: Context): LogisticaVerdeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LogisticaVerdeDatabase::class.java,
                    "logistica_verde_offline.db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Poblar catálogos iniciales al crear la base de datos por primera vez
                            INSTANCE?.let { database ->
                                dbScope.launch {
                                    seedInitialCatalogs(database.catalogoDao())
                                }
                            }
                        }
                    })
                    .addMigrations(*MIGRACIONES)
                    .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5)
                    .build()

                INSTANCE = instance
                // Garantizar que los catálogos semilla existan en el primer arranque
                dbScope.launch {
                    val dao = instance.catalogoDao()
                    if (dao.getTiposBasuraList().isEmpty()) {
                        seedInitialCatalogs(dao)
                    }
                }
                instance
            }
        }

        private suspend fun seedInitialCatalogs(catalogoDao: CatalogoDao) {
            catalogoDao.upsertPlayas(CatalogoSeedData.playas)
            catalogoDao.upsertSectores(CatalogoSeedData.sectoresPlayaGrande)
            catalogoDao.upsertHerramientas(CatalogoSeedData.herramientasQr)
            catalogoDao.upsertTiposBasura(CatalogoSeedData.tiposBasura)
        }
    }
}
