package cl.logisticaverde.app

import android.app.Application
import cl.logisticaverde.app.data.local.db.LogisticaVerdeDatabase
import cl.logisticaverde.app.data.repository.JornadaSyncRepository

class LogisticaVerdeApp : Application() {

    val database: LogisticaVerdeDatabase by lazy {
        LogisticaVerdeDatabase.getInstance(this)
    }

    val syncRepository: JornadaSyncRepository by lazy {
        JornadaSyncRepository(
            catalogoDao = database.catalogoDao(),
            jornadaDao = database.jornadaDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Inicializar la base de datos Room y los catálogos semilla al arrancar la app
        database
    }
}
