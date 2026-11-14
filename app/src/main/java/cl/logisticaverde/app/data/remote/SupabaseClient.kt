package cl.logisticaverde.app.data.remote

import cl.logisticaverde.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Proveedor Singleton de Retrofit 2 + OkHttp 4 configurado para Supabase REST (PostgREST) y Storage.
 *
 * Las credenciales NO se hardcodean en el código fuente: se leen en tiempo de compilación
 * desde el archivo `.env` (o `local.properties`) en la raíz del proyecto mediante [BuildConfig].
 */
object SupabaseClient {

    val SUPABASE_URL: String = BuildConfig.SUPABASE_URL.trim()
    val SUPABASE_KEY: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()
    const val STORAGE_BUCKET_EVIDENCIAS = "evidencias-fotos"

    /**
     * Verifica si el archivo `.env` / `local.properties` contiene credenciales reales de Supabase.
     */
    fun isConfigured(): Boolean {
        return SUPABASE_URL.startsWith("https://") &&
            !SUPABASE_URL.contains("placeholder.supabase.co") &&
            !SUPABASE_URL.contains("TU_PROYECTO") &&
            SUPABASE_KEY.isNotBlank() &&
            !SUPABASE_KEY.contains("PEGA_AQUI")
    }

    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()
            .header("apikey", SUPABASE_KEY)

        // Si la clave es un JWT clásico (`eyJ...`), enviamos también Authorization Bearer.
        // Si es una nueva clave `sb_publishable_...`, el gateway de Supabase usa únicamente `apikey`.
        if (SUPABASE_KEY.startsWith("eyJ")) {
            requestBuilder.header("Authorization", "Bearer $SUPABASE_KEY")
        }

        chain.proceed(requestBuilder.build())
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .apply {
                // RNF-05: en release no se registra tráfico HTTP en Logcat
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                }
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val api: SupabaseApi by lazy {
        val rawUrl = SUPABASE_URL.ifBlank { "https://placeholder.supabase.co/" }
        val normalizedBaseUrl = if (rawUrl.endsWith("/")) rawUrl else "$rawUrl/"
        Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SupabaseApi::class.java)
    }
}
