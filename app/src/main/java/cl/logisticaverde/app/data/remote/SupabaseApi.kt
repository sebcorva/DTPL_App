package cl.logisticaverde.app.data.remote

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
    @SerializedName("participantes") val participantes: List<ParticipanteRpcDto>,
    @SerializedName("herramientas") val herramientas: List<HerramientaRpcDto>,
    @SerializedName("basura") val basura: List<BasuraRpcDto>
)

/**
 * Cabecera de la jornada. El servidor recalcula nombre_lider, voluntarios_csv,
 * total_participantes, nombre_playa, sectores_csv, area_total_m2 y kg_totales, por eso no se envían.
 */
data class JornadaRpcDto(
    @SerializedName("id_registro") val idRegistro: String,
    @SerializedName("brigada") val brigada: String,
    @SerializedName("fecha_jornada") val fechaJornada: String,
    @SerializedName("id_playa") val idPlaya: String,
    @SerializedName("sectores_ids") val sectoresIds: List<String>,
    @SerializedName("sin_herramientas") val sinHerramientas: Boolean,
    @SerializedName("hora_inicio_ms") val horaInicioMs: Long,
    @SerializedName("hora_fin_ms") val horaFinMs: Long,
    @SerializedName("latitud_inicial") val latitudInicial: Double,
    @SerializedName("longitud_inicial") val longitudInicial: Double,
    @SerializedName("km_recorridos") val kmRecorridos: Float,
    @SerializedName("url_foto_inicio") val urlFotoInicio: String?,
    @SerializedName("url_foto_limpio") val urlFotoLimpio: String?,
    @SerializedName("url_foto_sacos") val urlFotoSacos: String?,
    @SerializedName("motivo_omision_inicio") val motivoOmisionInicio: String?,
    @SerializedName("motivo_omision_limpio") val motivoOmisionLimpio: String?,
    @SerializedName("motivo_omision_sacos") val motivoOmisionSacos: String?,
    @SerializedName("cantidad_sacos") val cantidadSacos: Int,
    @SerializedName("kg_basura_normal") val kgBasuraNormal: Float,
    @SerializedName("kg_basura_reciclable") val kgBasuraReciclable: Float,
    @SerializedName("encargado_pesaje") val encargadoPesaje: String,
    @SerializedName("destino_basura_normal") val destinoBasuraNormal: String,
    @SerializedName("nro_guia_despacho") val nroGuiaDespacho: String?,
    @SerializedName("destino_basura_reciclable") val destinoBasuraReciclable: String
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

    /** Devuelve `"INSERTADA"` o `"YA_EXISTIA"`; datos inválidos responden HTTP 400. */
    @POST("rest/v1/rpc/sincronizar_jornada")
    suspend fun sincronizarJornada(
        @Body request: SincronizarJornadaRequest
    ): Response<String>
}
