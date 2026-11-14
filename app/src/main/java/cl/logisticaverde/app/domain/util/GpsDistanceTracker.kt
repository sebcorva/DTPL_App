package cl.logisticaverde.app.domain.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Calcula de forma ligera los kilómetros recorridos (km_recorridos) del líder de brigada
 * durante la Pantalla 5 (entre Comenzar Limpieza y Finalizar Limpieza).
 * Aplica filtro anti-rebote GPS (> 5 metros y precisión <= 30 metros) para ahorrar batería.
 */
class GpsDistanceTracker(context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context.applicationContext)

    private var lastValidLocation: Location? = null
    private var accumulatedMeters: Float = 0f
    private var isTracking: Boolean = false
    private var locationCallback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    fun startTracking(
        initialMeters: Float = 0f,
        onInitialCoordinate: (lat: Double, lng: Double) -> Unit,
        onDistanceUpdated: (kmRecorridos: Float) -> Unit
    ) {
        if (isTracking) return
        accumulatedMeters = initialMeters
        lastValidLocation = null
        isTracking = true

        // ISSUE-64: alta precisión = chip GPS, que funciona sin internet (sin datos solo tarda más la
        // primera posición). BALANCED se ubicaba por Wi-Fi y antenas (~100 m) y casi no encendía el GPS:
        // en una playa sin Wi-Fi todas las lecturas quedaban sobre 35 m y se descartaban (0 km, sin coordenada).
        // ⚠️ Gasta más batería: medirlo en el piloto (ISSUE-29, meta < 5 % en 3 h).
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 15_000L)
            .setMinUpdateIntervalMillis(8_000L)
            .setMinUpdateDistanceMeters(6f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val newLoc = result.lastLocation ?: return
                if (newLoc.accuracy > 35f) return // Ignorar lecturas GPS imprecisas

                val previous = lastValidLocation
                if (previous == null) {
                    lastValidLocation = newLoc
                    onInitialCoordinate(newLoc.latitude, newLoc.longitude)
                } else {
                    val deltaMeters = previous.distanceTo(newLoc)
                    // Filtro anti-rebote cuando el líder está detenido contando residuos
                    if (deltaMeters >= 5f && deltaMeters <= 500f) {
                        accumulatedMeters += deltaMeters
                        lastValidLocation = newLoc
                        val km = accumulatedMeters / 1000f
                        onDistanceUpdated(km)
                    }
                }
            }
        }

        locationCallback = callback
        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    fun stopTracking() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
        isTracking = false
    }
}
