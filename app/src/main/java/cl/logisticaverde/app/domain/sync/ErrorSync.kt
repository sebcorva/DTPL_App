package cl.logisticaverde.app.domain.sync

import android.util.Log

/**
 * ISSUE-42: errores de sincronización tipados.
 *
 * - [detenerLote]: no tiene sentido seguir con las demás jornadas (sin internet, servidor no listo…).
 * - [rechazaJornada]: el problema es de esa jornada y no se arregla reintentando sola → `ERROR_SYNC`.
 * - Los demás son transitorios: la jornada sigue `PENDIENTE_SYNC` y se reintenta después.
 */
sealed interface ErrorSync {
    val detenerLote: Boolean get() = false
    val rechazaJornada: Boolean get() = false

    data object SinConfiguracion : ErrorSync {
        override val detenerLote = true
    }

    data object SinConexion : ErrorSync {
        override val detenerLote = true
    }

    /** PostgREST PGRST202/PGRST205 o 404: faltan tablas/funciones (SQL sin ejecutar o caché sin recargar). */
    data object ServidorNoPreparado : ErrorSync {
        override val detenerLote = true
    }

    data object NoAutorizado : ErrorSync {
        override val detenerLote = true
    }

    data object ErrorServidor : ErrorSync

    /** La RPC respondió "RECHAZADA: <motivo>" (o 400 de validación en un servidor antiguo). */
    data class Rechazada(val motivo: String) : ErrorSync {
        override val rechazaJornada = true
    }

    /** El archivo de una foto ya no está en el teléfono. */
    data object FotoFaltante : ErrorSync {
        override val rechazaJornada = true
    }

    data object Desconocido : ErrorSync
}

/** Excepción interna del repositorio: lleva el tipo y el detalle técnico (solo para el log). */
class SyncException(val error: ErrorSync, detalle: String) : Exception(detalle)

/** Textos para el líder (decisión 31-10-2026): lenguaje simple y mencionando al administrador. */
object MensajesSync {
    const val MOTIVO_FOTO_FALTANTE = "Falta una foto en el teléfono"

    fun paraLider(error: ErrorSync): String = when (error) {
        ErrorSync.SinConfiguracion -> "Esta versión de la app no tiene servidor configurado. Avisa al administrador."
        ErrorSync.SinConexion -> "Sin conexión estable. Tus jornadas siguen guardadas."
        ErrorSync.ServidorNoPreparado -> "El servidor está en mantención. Avisa al administrador."
        ErrorSync.NoAutorizado -> "La app no tiene permiso en el servidor. Avisa al administrador."
        ErrorSync.ErrorServidor -> "El servidor tuvo un problema. Intenta más tarde."
        is ErrorSync.Rechazada -> "Jornada rechazada. Revisa Reportes."
        ErrorSync.FotoFaltante -> "$MOTIVO_FOTO_FALTANTE. Revisa Reportes."
        ErrorSync.Desconocido -> "No se pudo sincronizar. Intenta más tarde."
    }

    /** Motivo que se guarda en `motivo_error_sync` y se muestra en Reportes. */
    fun motivoRechazo(error: ErrorSync): String = when (error) {
        is ErrorSync.Rechazada -> error.motivo
        ErrorSync.FotoFaltante -> MOTIVO_FOTO_FALTANTE
        else -> paraLider(error)
    }
}

/**
 * Registro técnico (decisión 8): solo el `Log` de Android, sin nombres ni coordenadas.
 * `runCatching` porque en los tests unitarios de la JVM `Log` no está disponible.
 */
object RegistroErrores {
    fun advertencia(tag: String, mensaje: String, error: Throwable? = null) {
        runCatching { Log.w(tag, mensaje, error) }
    }
}
