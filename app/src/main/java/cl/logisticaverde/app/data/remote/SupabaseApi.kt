package cl.logisticaverde.app.data.remote

import cl.logisticaverde.app.data.local.entity.BrigadaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.HerramientaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.PlayaCatalogoEntity
import cl.logisticaverde.app.data.local.entity.SectorCatalogoEntity
import cl.logisticaverde.app.data.local.entity.TipoBasuraCatalogoEntity
import com.google.gson.annotations.SerializedName
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Cuerpo de `POST rest/v1/rpc/sincronizar_jornada`: PostgREST mapea la clave `p`
 * al parámetro `p jsonb` de la función (contrato en docs/supabase_schema.sql §4).
 */
data class SincronizarJornadaRequest(
    @SerializedName("p") val payload: JornadaSyncPayload
)

data class JornadaSyncPayload(
    @SerializedName("jornada") val jornada: JornadaRpcDto,
    @SerializedName("fotos") val fotos: List<FotoRpcDto>,
    @SerializedName("participantes") val participantes: List<ParticipanteRpcDto>,
    @SerializedName("herramientas") val herramientas: List<HerramientaRpcDto>,
    @SerializedName("basura") val basura: List<BasuraRpcDto>
)

/**
 * Cabecera de la jornada (v5, ISSUE-62). Lo derivado (líder, voluntarios, nombres de playa y
 * brigada, sectores, área, kg totales) no se envía: Supabase lo obtiene de sus tablas.
 */
data class JornadaRpcDto(
    @SerializedName("id_registro") val idRegistro: String,
    @SerializedName("fecha_jornada") val fechaJornada: String,
    @SerializedName("id_playa") val idPlaya: String,
    @SerializedName("id_brigada") val idBrigada: String,
    @SerializedName("sectores_ids") val sectoresIds: List<String>,
    @SerializedName("sin_herramientas") val sinHerramientas: Boolean,
    @SerializedName("hora_inicio_ms") val horaInicioMs: Long,
    @SerializedName("hora_fin_ms") val horaFinMs: Long,
    // null (se omite en el JSON) = sin lectura de GPS
    @SerializedName("latitud_inicial") val latitudInicial: Double?,
    @SerializedName("longitud_inicial") val longitudInicial: Double?,
    @SerializedName("km_recorridos") val kmRecorridos: Float,
    @SerializedName("cantidad_sacos") val cantidadSacos: Int,
    @SerializedName("kg_basura_normal") val kgBasuraNormal: Float,
    @SerializedName("kg_basura_reciclable") val kgBasuraReciclable: Float,
    @SerializedName("encargado_pesaje") val encargadoPesaje: String,
    @SerializedName("destino_basura_normal") val destinoBasuraNormal: String,
    @SerializedName("nro_guia_despacho") val nroGuiaDespacho: String?,
    @SerializedName("destino_basura_reciclable") val destinoBasuraReciclable: String
)

/**
 * Una de las 3 fotos (ISSUE-62): `ruta` dentro del bucket si se tomó, o `motivoOmision` si se omitió.
 * La RPC exige la ruta exacta de cada tipo: `{id}_1_inicio.jpg`, `{id}_2_limpio.jpg`, `{id}_3_sacos.jpg`.
 */
data class FotoRpcDto(
    @SerializedName("tipo") val tipo: String,
    @SerializedName("ruta") val ruta: String?,
    @SerializedName("motivo_omision") val motivoOmision: String?
)

data class ParticipanteRpcDto(
    @SerializedName("nombre") val nombre: String,
    @SerializedName("rol") val rol: String
)

data class HerramientaRpcDto(
    @SerializedName("qr_codigo") val qrCodigo: String,
    @SerializedName("estado_final") val estadoFinal: String
)

data class BasuraRpcDto(
    @SerializedName("id_tipo_basura") val idTipoBasura: String,
    @SerializedName("cantidad") val cantidad: Int
)

/**
 * Remote Data Source: Interfaz Retrofit 2 contra Supabase REST API (PostgREST) y Supabase Storage.
 * Con la clave pública solo se pueden leer catálogos, ejecutar la RPC y subir fotos nuevas (PRD §5.3).
 */
interface SupabaseApi {

    // ========================================================================
    // 1. DESCARGA DE CATÁLOGOS MAESTROS (Supabase -> Room @Upsert)
    // ========================================================================

    @GET("rest/v1/herramienta_catalogo")
    suspend fun getHerramientasCatalogo(
        @Query("select") select: String = "*"
    ): Response<List<HerramientaCatalogoEntity>>

    @GET("rest/v1/tipo_basura_catalogo")
    suspend fun getTiposBasuraCatalogo(
        @Query("select") select: String = "*"
    ): Response<List<TipoBasuraCatalogoEntity>>

    @GET("rest/v1/playa_catalogo")
    suspend fun getPlayasCatalogo(
        @Query("select") select: String = "*"
    ): Response<List<PlayaCatalogoEntity>>

    @GET("rest/v1/brigada_catalogo")
    suspend fun getBrigadasCatalogo(
        @Query("select") select: String = "*"
    ): Response<List<BrigadaCatalogoEntity>>

    @GET("rest/v1/sector_catalogo")
    suspend fun getSectoresCatalogo(
        @Query("select") select: String = "*"
    ): Response<List<SectorCatalogoEntity>>

    // ========================================================================
    // 2. SUBIDA DE EVIDENCIAS FOTOGRÁFICAS (Supabase Storage Bucket)
    // ========================================================================

    /** Sin `x-upsert`: el bucket no permite reemplazar fotos; un reintento recibe "ya existe". */
    @POST("storage/v1/object/{bucket}/{fileName}")
    suspend fun uploadEvidencePhoto(
        @Path("bucket") bucket: String = SupabaseClient.STORAGE_BUCKET_EVIDENCIAS,
        @Path("fileName") fileName: String,
        @Header("Content-Type") contentType: String = "image/jpeg",
        @Body imageBytes: RequestBody
    ): Response<Unit>

    // ========================================================================
    // 3. SINCRONIZACIÓN TRANSACCIONAL DE UNA JORNADA (Room -> Supabase)
    // ========================================================================

    /** Devuelve `"INSERTADA"`, `"YA_EXISTIA"` o `"RECHAZADA: <motivo>"` (ISSUE-42). */
    @POST("rest/v1/rpc/sincronizar_jornada")
    suspend fun sincronizarJornada(
        @Body request: SincronizarJornadaRequest
    ): Response<String>
}
