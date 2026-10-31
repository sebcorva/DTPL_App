package cl.logisticaverde.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import cl.logisticaverde.app.LogisticaVerdeApp
import cl.logisticaverde.app.MainActivity
import cl.logisticaverde.app.R
import cl.logisticaverde.app.data.local.entity.JornadaEntity
import cl.logisticaverde.app.domain.util.GpsDistanceTracker
import cl.logisticaverde.app.domain.sync.RegistroErrores
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * GPS de la Pantalla 5 en un Foreground Service (ISSUE-23, RNF-02):
 * sigue midiendo km_recorridos con la pantalla apagada o la app en segundo plano.
 *
 * - Escribe km y coordenada inicial directo en Room; la UI solo observa la jornada.
 * - Se inicia con "Comenzar Limpieza" y se detiene con "Finalizar Limpieza".
 * - Si Android reinicia el servicio (START_STICKY, intent nulo), retoma la jornada EN_CURSO
 *   con la limpieza iniciada y continúa desde los km ya guardados.
 */
class LimpiezaLocationService : Service() {

    companion object {
        private const val CHANNEL_ID = "limpieza_gps"
        private const val NOTIFICATION_ID = 5005
        private const val EXTRA_ID_REGISTRO = "id_registro"

        fun tienePermisoUbicacion(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        /** Sin permiso de ubicación no se inicia: la limpieza sigue, pero sin km. */
        fun iniciar(context: Context, idRegistro: String) {
            if (!tienePermisoUbicacion(context)) return
            val intent = Intent(context, LimpiezaLocationService::class.java)
                .putExtra(EXTRA_ID_REGISTRO, idRegistro)
            ContextCompat.startForegroundService(context, intent)
        }

        fun detener(context: Context) {
            context.stopService(Intent(context, LimpiezaLocationService::class.java))
        }
    }

    // ISSUE-42: un error al escribir en Room se registra y no detiene el servicio ni cierra la app
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, e -> RegistroErrores.advertencia("LimpiezaGPS", "Error del servicio GPS", e) }
    )
    private var tracker: GpsDistanceTracker? = null
    private var idRegistroActivo: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground debe llamarse de inmediato tras startForegroundService
        if (!entrarEnPrimerPlano()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val idSolicitado = intent?.getStringExtra(EXTRA_ID_REGISTRO)
        scope.launch {
            val jornadaDao = (application as LogisticaVerdeApp).database.jornadaDao()
            val jornada = if (idSolicitado != null) {
                jornadaDao.getJornadaById(idSolicitado)
            } else {
                // Reinicio del sistema: solo si la limpieza estaba en curso
                jornadaDao.observeJornadaEnCurso().firstOrNull()?.takeIf { it.horaInicioMs > 0L }
            }

            if (jornada == null || jornada.estadoSync != JornadaEntity.SYNC_EN_CURSO || jornada.horaFinMs > 0L) {
                stopSelf()
                return@launch
            }
            if (idRegistroActivo == jornada.idRegistro) return@launch // ya se está midiendo

            tracker?.stopTracking()
            val id = jornada.idRegistro
            idRegistroActivo = id
            actualizarNotificacion(jornada.kmRecorridos)

            tracker = GpsDistanceTracker(this@LimpiezaLocationService).also {
                it.startTracking(
                    initialMeters = jornada.kmRecorridos * 1000f,
                    onInitialCoordinate = { lat, lng ->
                        scope.launch { jornadaDao.updateCoordenadaInicialSiVacia(id, lat, lng) }
                    },
                    onDistanceUpdated = { km ->
                        scope.launch { jornadaDao.updateKmRecorridos(id, km) }
                        actualizarNotificacion(km)
                    }
                )
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        tracker?.stopTracking()
        tracker = null
        scope.cancel()
        super.onDestroy()
    }

    private fun entrarEnPrimerPlano(): Boolean {
        if (!tienePermisoUbicacion(this)) return false
        crearCanal()
        return try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                construirNotificacion(0f),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            )
            true
        } catch (e: Exception) {
            // Android 12+ puede negar iniciar un servicio en primer plano desde segundo plano
            RegistroErrores.advertencia("LimpiezaGPS", "No se pudo iniciar el servicio en primer plano", e)
            false
        }
    }

    private fun crearCanal() {
        val canal = NotificationChannel(CHANNEL_ID, "Limpieza en curso (GPS)", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "Muestra los km recorridos mientras dura la limpieza" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    private fun construirNotificacion(km: Float): Notification {
        val abrirApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_limpieza)
            .setContentTitle("Deja tu Playa Limpia")
            .setContentText(String.format(Locale("es", "CL"), "Limpieza en curso · %.2f km", km))
            .setContentIntent(abrirApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun actualizarNotificacion(km: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, construirNotificacion(km))
    }
}
