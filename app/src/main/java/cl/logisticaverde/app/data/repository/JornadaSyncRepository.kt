package cl.logisticaverde.app.data.repository

import cl.logisticaverde.app.data.local.db.CatalogoDao
import cl.logisticaverde.app.data.local.db.JornadaDao
import cl.logisticaverde.app.data.local.entity.JornadaEntity
import cl.logisticaverde.app.data.remote.BasuraRpcDto
import cl.logisticaverde.app.data.remote.HerramientaRpcDto
import cl.logisticaverde.app.data.remote.JornadaRpcDto
import cl.logisticaverde.app.data.remote.JornadaSyncPayload
import cl.logisticaverde.app.data.remote.ParticipanteRpcDto
import cl.logisticaverde.app.data.remote.SincronizarJornadaRequest
import cl.logisticaverde.app.data.remote.SupabaseApi
import cl.logisticaverde.app.data.remote.SupabaseClient
import cl.logisticaverde.app.domain.sync.ErrorSync
import cl.logisticaverde.app.domain.sync.MensajesSync
import cl.logisticaverde.app.domain.sync.RegistroErrores
import cl.logisticaverde.app.domain.sync.SyncException
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response
import java.io.File
import java.io.IOException

/** Resultado de un lote de sincronización. [error] es el más relevante (o null si todo salió bien). */
data class SyncResult(
    val successCount: Int,
    val failedCount: Int,
    val rechazadasCount: Int = 0,
    val error: ErrorSync? = null
) {
    /** Texto para el líder (lenguaje simple). */
    val message: String
        get() = when {
            error != null -> MensajesSync.paraLider(error)
            successCount == 0 -> "No hay jornadas pendientes por sincronizar."
            else -> "Sincronización exitosa: $successCount jornada(s) enviada(s)."
        }
}

/** Estado del servidor al descargar catálogos (ISSUE-42: "Servidor en mantención"). */
enum class EstadoServidor { OK, SIN_CONFIGURACION, SIN_CONEXION, NO_PREPARADO, ERROR }

/**
 * Capa Repository Pattern (Orquestación / Sync):
 * Coordina la sincronización entre el Remote Data Source (Supabase RPC + Storage)
 * y el Local Data Source (Room DB como Single Source of Truth - SSOT).
 *
 * ISSUE-42: cada falla se traduce a un [ErrorSync]. Los rechazos de una jornada la dejan en
 * `ERROR_SYNC` con su motivo; los transitorios la dejan `PENDIENTE_SYNC`; sin internet o con el
 * servidor no preparado se detiene el lote. El detalle técnico va solo al log.
 */
class JornadaSyncRepository(
    private val catalogoDao: CatalogoDao,
    private val jornadaDao: JornadaDao,
    private val supabaseApi: SupabaseApi = SupabaseClient.api,
    private val isCloudConfigured: () -> Boolean = { SupabaseClient.isConfigured() },
    private val publicPhotoUrlBuilder: (String) -> String = { SupabaseClient.buildPublicPhotoUrl(it) }
) {

    companion object {
        const val RPC_INSERTADA = "INSERTADA"
        const val RPC_YA_EXISTIA = "YA_EXISTIA"
        const val RPC_PREFIJO_RECHAZADA = "RECHAZADA"
        private const val TAG = "JornadaSync"
    }

    /**
     * Descarga las 4 tablas maestras desde Supabase PostgreSQL y actualiza Room mediante @Upsert.
     * Si falla, Room mantiene su catálogo local (la app sigue funcionando) y se informa el estado.
     *
     * BR-10: el estado de una herramienta usada en una jornada no sincronizada no se
     * reemplaza con el del servidor; prevalece el dato local hasta que esa jornada se sincronice.
     */
    suspend fun sincronizarCatalogosMaestros(): EstadoServidor = withContext(Dispatchers.IO) {
        if (!isCloudConfigured()) return@withContext EstadoServidor.SIN_CONFIGURACION

        try {
            val herramientas = supabaseApi.getHerramientasCatalogo().cuerpoOError("catálogo de herramientas")
            herramientas.takeIf { it.isNotEmpty() }?.let { remotas ->
                val protegidas = jornadaDao.getQrHerramientasNoSincronizadas().toSet()
                val fusionadas = remotas.map { remota ->
                    val local = if (remota.qrCodigo in protegidas) {
                        catalogoDao.getHerramientaByQr(remota.qrCodigo)
                    } else {
                        null
                    }
                    if (local != null) remota.copy(estado = local.estado) else remota
                }
                catalogoDao.upsertHerramientas(fusionadas)
            }

            supabaseApi.getTiposBasuraCatalogo().cuerpoOError("catálogo de residuos")
                .takeIf { it.isNotEmpty() }?.let { catalogoDao.upsertTiposBasura(it) }

            // Playas antes que sectores: cada sector pertenece a una playa
            supabaseApi.getPlayasCatalogo().cuerpoOError("catálogo de playas")
                .takeIf { it.isNotEmpty() }?.let { catalogoDao.upsertPlayas(it) }

            supabaseApi.getSectoresCatalogo().cuerpoOError("catálogo de sectores")
                .takeIf { it.isNotEmpty() }?.let { catalogoDao.upsertSectores(it) }

            EstadoServidor.OK
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = clasificar(e)
            RegistroErrores.advertencia(TAG, "Catálogos no actualizados (${error.nombre()}): ${e.message}")
            when (error) {
                ErrorSync.SinConexion -> EstadoServidor.SIN_CONEXION
                ErrorSync.ServidorNoPreparado -> EstadoServidor.NO_PREPARADO
                else -> EstadoServidor.ERROR
            }
        }
    }

    /**
     * Sincroniza todas las jornadas `PENDIENTE_SYNC` (PRD §5.1). Una jornada solo pasa a
     * `SINCRONIZADO` cuando la RPC confirma `INSERTADA` o `YA_EXISTIA`.
     */
    suspend fun sincronizarJornadasPendientes(): SyncResult = withContext(Dispatchers.IO) {
        sincronizarLote(jornadaDao.getJornadasPendientesSync())
    }

    /** "Reintentar" de Reportes: reenvía exactamente los mismos datos de una jornada (sin editarla). */
    suspend fun sincronizarJornada(idRegistro: String): SyncResult = withContext(Dispatchers.IO) {
        val jornada = jornadaDao.getJornadaById(idRegistro)
            ?.takeIf { it.estadoSync == JornadaEntity.SYNC_ERROR || it.estadoSync == JornadaEntity.SYNC_PENDIENTE }
        sincronizarLote(listOfNotNull(jornada))
    }

    private suspend fun sincronizarLote(jornadas: List<JornadaEntity>): SyncResult {
        if (jornadas.isEmpty()) return SyncResult(successCount = 0, failedCount = 0)

        // Sin credenciales no hay "modo local": nada se marca como sincronizado
        if (!isCloudConfigured()) {
            return SyncResult(successCount = 0, failedCount = jornadas.size, error = ErrorSync.SinConfiguracion)
        }

        when (sincronizarCatalogosMaestros()) {
            EstadoServidor.NO_PREPARADO ->
                return SyncResult(0, jornadas.size, error = ErrorSync.ServidorNoPreparado)
            EstadoServidor.SIN_CONEXION ->
                return SyncResult(0, jornadas.size, error = ErrorSync.SinConexion)
            else -> Unit // con otro error de catálogo se intenta igual: Room tiene su copia local
        }

        var ok = 0
        var fallidas = 0
        var rechazadas = 0
        var errorPrincipal: ErrorSync? = null

        for ((indice, jornada) in jornadas.withIndex()) {
            val error = sincronizarUna(jornada) ?: run { ok++; null }
            if (error != null) {
                fallidas++
                if (error.rechazaJornada) {
                    rechazadas++
                    jornadaDao.marcarErrorSync(jornada.idRegistro, MensajesSync.motivoRechazo(error))
                }
                // Un error que afecta a todas (sin internet, servidor) pesa más que un rechazo puntual
                if (errorPrincipal == null || error.detenerLote) errorPrincipal = error
                if (error.detenerLote) {
                    fallidas += jornadas.size - indice - 1
                    break
                }
            }
        }

        return SyncResult(successCount = ok, failedCount = fallidas, rechazadasCount = rechazadas, error = errorPrincipal)
    }

    /** Devuelve null si la jornada quedó en Supabase, o el error si no. */
    private suspend fun sincronizarUna(jornada: JornadaEntity): ErrorSync? = try {
        // 1. Fotos (idempotentes: un reintento que encuentra la foto ya subida cuenta como éxito)
        val urlInicio = uploadPhotoStrict(jornada.pathFotoInicio, jornada.urlFotoInicio, "${jornada.idRegistro}_1_inicio.jpg")
        val urlLimpio = uploadPhotoStrict(jornada.pathFotoLimpio, jornada.urlFotoLimpio, "${jornada.idRegistro}_2_limpio.jpg")
        val urlSacos = uploadPhotoStrict(jornada.pathFotoSacos, jornada.urlFotoSacos, "${jornada.idRegistro}_3_sacos.jpg")

        // Se guardan las URLs ya obtenidas: si la RPC falla, el reintento no vuelve a subir fotos
        val conUrls = jornada.copy(urlFotoInicio = urlInicio, urlFotoLimpio = urlLimpio, urlFotoSacos = urlSacos)
        jornadaDao.updateJornada(conUrls)

        // 2. Una sola llamada transaccional con toda la jornada
        val herramientas = jornadaDao.getHerramientasJornadaList(jornada.idRegistro)
        val request = SincronizarJornadaRequest(
            JornadaSyncPayload(
                jornada = conUrls.toRpcDto(),
                participantes = jornadaDao.getParticipantesList(jornada.idRegistro)
                    .map { ParticipanteRpcDto(nombre = it.nombre, rol = it.rol) },
                herramientas = herramientas
                    .map { HerramientaRpcDto(qrCodigo = it.qrCodigo, estadoFinal = it.estadoFinal) },
                basura = jornadaDao.getBasuraRecolectadaList(jornada.idRegistro)
                    .map { BasuraRpcDto(idTipoBasura = it.idTipoBasura, cantidad = it.cantidad) }
            )
        )
        val resultado = supabaseApi.sincronizarJornada(request).cuerpoOError("al sincronizar la jornada")

        when {
            resultado == RPC_INSERTADA || resultado == RPC_YA_EXISTIA -> {
                // 3. Solo con la confirmación del servidor se marca como SINCRONIZADO
                herramientas.forEach { hj -> catalogoDao.updateEstadoHerramientaCatalogo(hj.qrCodigo, hj.estadoFinal) }
                jornadaDao.updateEstadoSync(jornada.idRegistro, JornadaEntity.SYNC_SINCRONIZADO)
                null
            }
            resultado.startsWith(RPC_PREFIJO_RECHAZADA) -> {
                val motivo = resultado.removePrefix(RPC_PREFIJO_RECHAZADA).trimStart(':', ' ').ifBlank { "Sin detalle" }
                RegistroErrores.advertencia(TAG, "${jornada.idRegistro} rechazada: $motivo")
                ErrorSync.Rechazada(motivo)
            }
            else -> {
                RegistroErrores.advertencia(TAG, "${jornada.idRegistro}: respuesta inesperada \"$resultado\"")
                ErrorSync.Desconocido
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val error = clasificar(e)
        RegistroErrores.advertencia(TAG, "${jornada.idRegistro} no sincronizada (${error.nombre()}): ${e.message}")
        error
    }

    /**
     * Sube una foto local a Supabase Storage sin sobrescribir.
     * Si el archivo ya existe en el bucket (reintento tras un corte), se considera éxito.
     */
    private suspend fun uploadPhotoStrict(
        localPath: String,
        existingRemoteUrl: String?,
        remoteFileName: String
    ): String? {
        if (!existingRemoteUrl.isNullOrBlank()) return existingRemoteUrl
        if (localPath.isBlank()) return null

        val file = File(localPath)
        if (!file.exists() || file.length() == 0L) {
            throw SyncException(ErrorSync.FotoFaltante, "No se encontró el archivo de foto local ($remoteFileName)")
        }

        val requestBody = file.asRequestBody("image/jpeg".toMediaType())
        val response = supabaseApi.uploadEvidencePhoto(fileName = remoteFileName, imageBytes = requestBody)
        if (!response.isSuccessful) {
            val cuerpo = response.errorBody()?.string()
            if (!esFotoYaExistente(response.code(), cuerpo)) {
                throw errorHttp(response.code(), cuerpo, "al subir foto $remoteFileName")
            }
        }
        return publicPhotoUrlBuilder(remoteFileName)
    }

    /** Storage responde 409, o 400 con `"error":"Duplicate"`, según la versión de Supabase. */
    private fun esFotoYaExistente(code: Int, cuerpo: String?): Boolean {
        if (code == 409) return true
        if (code != 400 || cuerpo == null) return false
        return cuerpo.contains("Duplicate", ignoreCase = true) ||
            cuerpo.contains("already exists", ignoreCase = true) ||
            cuerpo.contains("\"409\"")
    }

    private fun <T> Response<T>.cuerpoOError(contexto: String): T {
        if (!isSuccessful) throw errorHttp(code(), errorBody()?.string(), contexto)
        return body() ?: throw SyncException(ErrorSync.Desconocido, "Respuesta vacía $contexto")
    }

    /** Traduce una respuesta HTTP de PostgREST / Storage a un [ErrorSync]. */
    private fun errorHttp(code: Int, cuerpo: String?, contexto: String): SyncException {
        val json = runCatching { JsonParser.parseString(cuerpo.orEmpty()).asJsonObject }.getOrNull()
        val codigo = json?.get("code")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        val mensaje = json?.get("message")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        val error = when {
            codigo == "PGRST202" || codigo == "PGRST205" || code == 404 -> ErrorSync.ServidorNoPreparado
            code == 401 || code == 403 -> ErrorSync.NoAutorizado
            // Servidor antiguo (antes de jornada_rechazada): la validación respondía 400 con 22xxx/23xxx
            code == 400 && (codigo.startsWith("22") || codigo.startsWith("23") || codigo == "P0001") ->
                ErrorSync.Rechazada(mensaje.ifBlank { "Datos inválidos" })
            code >= 500 -> ErrorSync.ErrorServidor
            else -> ErrorSync.Desconocido
        }
        return SyncException(error, "HTTP $code $contexto ${codigo.ifBlank { "" }} $mensaje".trim())
    }

    private fun clasificar(e: Exception): ErrorSync = when (e) {
        is SyncException -> e.error
        is IOException -> ErrorSync.SinConexion // sin red, DNS, timeout, conexión cortada
        else -> ErrorSync.Desconocido
    }

    private fun ErrorSync.nombre(): String = this::class.simpleName ?: "ErrorSync"

    private fun JornadaEntity.toRpcDto() = JornadaRpcDto(
        idRegistro = idRegistro,
        brigada = brigada,
        fechaJornada = fechaJornada,
        idPlaya = idPlaya,
        sectoresIds = sectoresIdsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        sinHerramientas = sinHerramientas,
        horaInicioMs = horaInicioMs,
        horaFinMs = horaFinMs,
        latitudInicial = latitudInicial,
        longitudInicial = longitudInicial,
        kmRecorridos = kmRecorridos,
        urlFotoInicio = urlFotoInicio,
        urlFotoLimpio = urlFotoLimpio,
        urlFotoSacos = urlFotoSacos,
        // Solo se envía el motivo de las fotos que efectivamente no tienen URL
        motivoOmisionInicio = motivoOmisionInicio.takeIf { urlFotoInicio == null },
        motivoOmisionLimpio = motivoOmisionLimpio.takeIf { urlFotoLimpio == null },
        motivoOmisionSacos = motivoOmisionSacos.takeIf { urlFotoSacos == null },
        cantidadSacos = cantidadSacos,
        kgBasuraNormal = kgBasuraNormal,
        kgBasuraReciclable = kgBasuraReciclable,
        encargadoPesaje = encargadoPesaje,
        destinoBasuraNormal = destinoBasuraNormal,
        nroGuiaDespacho = nroGuiaDespacho,
        destinoBasuraReciclable = destinoBasuraReciclable
    )
}
