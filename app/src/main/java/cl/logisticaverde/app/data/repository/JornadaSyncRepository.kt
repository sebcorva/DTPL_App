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
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

data class SyncResult(
    val successCount: Int,
    val failedCount: Int,
    val message: String
)

/**
 * Capa Repository Pattern (Orquestación / Sync):
 * Coordina la sincronización entre el Remote Data Source (Supabase RPC + Storage)
 * y el Local Data Source (Room DB como Single Source of Truth - SSOT).
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
        const val MENSAJE_SIN_CONFIGURACION =
            "Este APK no tiene configuración de nube (SUPABASE_URL / SUPABASE_PUBLISHABLE_KEY). " +
                "Las jornadas siguen guardadas en el teléfono."
    }

    /**
     * Descarga las 4 tablas maestras desde Supabase PostgreSQL y actualiza Room mediante @Upsert.
     * Si las credenciales de Supabase aún no están configuradas o falla la red, Room mantiene su Seed local.
     *
     * BR-10: el estado de una herramienta usada en una jornada EN_CURSO o PENDIENTE_SYNC no se
     * reemplaza con el del servidor; prevalece el dato local hasta que esa jornada se sincronice.
     */
    suspend fun sincronizarCatalogosMaestros(): Boolean = withContext(Dispatchers.IO) {
        if (!isCloudConfigured()) return@withContext false

        try {
            val herramientasResp = supabaseApi.getHerramientasCatalogo()
            if (herramientasResp.isSuccessful) {
                herramientasResp.body()?.takeIf { it.isNotEmpty() }?.let { remotas ->
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
            }

            val tiposBasuraResp = supabaseApi.getTiposBasuraCatalogo()
            if (tiposBasuraResp.isSuccessful) {
                tiposBasuraResp.body()?.takeIf { it.isNotEmpty() }?.let {
                    catalogoDao.upsertTiposBasura(it)
                }
            }

            // Playas antes que sectores: cada sector pertenece a una playa
            val playasResp = supabaseApi.getPlayasCatalogo()
            if (playasResp.isSuccessful) {
                playasResp.body()?.takeIf { it.isNotEmpty() }?.let {
                    catalogoDao.upsertPlayas(it)
                }
            }

            val sectoresResp = supabaseApi.getSectoresCatalogo()
            if (sectoresResp.isSuccessful) {
                sectoresResp.body()?.takeIf { it.isNotEmpty() }?.let {
                    catalogoDao.upsertSectores(it)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Sincroniza todas las jornadas en estado `PENDIENTE_SYNC` hacia Supabase Storage y la RPC
     * `sincronizar_jornada` (PRD §5.1). Una jornada solo pasa a `SINCRONIZADO` cuando la RPC
     * confirma `INSERTADA` o `YA_EXISTIA`; cualquier otro resultado la deja en `PENDIENTE_SYNC`.
     */
    suspend fun sincronizarJornadasPendientes(): SyncResult = withContext(Dispatchers.IO) {
        val pendientes = jornadaDao.getJornadasPendientesSync()
        if (pendientes.isEmpty()) {
            return@withContext SyncResult(
                successCount = 0,
                failedCount = 0,
                message = "No hay jornadas pendientes por sincronizar."
            )
        }

        // Sin credenciales no hay "modo local": nada se marca como sincronizado
        if (!isCloudConfigured()) {
            return@withContext SyncResult(
                successCount = 0,
                failedCount = pendientes.size,
                message = MENSAJE_SIN_CONFIGURACION
            )
        }

        sincronizarCatalogosMaestros()

        var okCount = 0
        var errorCount = 0
        var lastErrorMsg = ""

        for (jornada in pendientes) {
            try {
                // 1. Fotos (idempotentes: un reintento que encuentra la foto ya subida cuenta como éxito)
                val urlInicio = uploadPhotoStrict(
                    localPath = jornada.pathFotoInicio,
                    existingRemoteUrl = jornada.urlFotoInicio,
                    remoteFileName = "${jornada.idRegistro}_1_inicio.jpg"
                )
                val urlLimpio = uploadPhotoStrict(
                    localPath = jornada.pathFotoLimpio,
                    existingRemoteUrl = jornada.urlFotoLimpio,
                    remoteFileName = "${jornada.idRegistro}_2_limpio.jpg"
                )
                val urlSacos = uploadPhotoStrict(
                    localPath = jornada.pathFotoSacos,
                    existingRemoteUrl = jornada.urlFotoSacos,
                    remoteFileName = "${jornada.idRegistro}_3_sacos.jpg"
                )

                // Se guardan las URLs ya obtenidas: si la RPC falla, el reintento no vuelve a subir fotos
                val conUrls = jornada.copy(
                    urlFotoInicio = urlInicio,
                    urlFotoLimpio = urlLimpio,
                    urlFotoSacos = urlSacos
                )
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
                val response = supabaseApi.sincronizarJornada(request)
                val resultado = response.body()
                if (!response.isSuccessful) {
                    throw IllegalStateException(
                        describirError(response.code(), response.errorBody()?.string(), "al sincronizar la jornada")
                    )
                }
                if (resultado != RPC_INSERTADA && resultado != RPC_YA_EXISTIA) {
                    throw IllegalStateException("Respuesta inesperada del servidor: \"$resultado\"")
                }

                // 3. Solo con la confirmación del servidor se marca como SINCRONIZADO
                herramientas.forEach { hj ->
                    catalogoDao.updateEstadoHerramientaCatalogo(hj.qrCodigo, hj.estadoFinal)
                }
                jornadaDao.updateEstadoSync(jornada.idRegistro, JornadaEntity.SYNC_SINCRONIZADO)
                okCount++
            } catch (e: Exception) {
                errorCount++
                lastErrorMsg = e.localizedMessage ?: "Error de red con Supabase"
            }
        }

        val finalMessage = when {
            errorCount == 0 -> "Sincronización exitosa: $okCount jornada(s) enviada(s) a Supabase."
            okCount > 0 -> "Sincronizadas $okCount jornada(s), $errorCount con error ($lastErrorMsg)."
            else -> "No se pudo sincronizar con Supabase ($lastErrorMsg). Tus datos siguen seguros en el teléfono."
        }

        SyncResult(
            successCount = okCount,
            failedCount = errorCount,
            message = finalMessage
        )
    }

    /**
     * Sube una foto local a Supabase Storage sin sobrescribir.
     * Si el archivo ya existe en el bucket (reintento tras un corte), se considera éxito.
     * Cualquier otro fallo lanza excepción para dejar la jornada en `PENDIENTE_SYNC`.
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
            throw IllegalStateException("No se encontró el archivo de foto local ($remoteFileName)")
        }

        val requestBody = file.asRequestBody("image/jpeg".toMediaType())
        val response = supabaseApi.uploadEvidencePhoto(
            fileName = remoteFileName,
            imageBytes = requestBody
        )
        if (!response.isSuccessful) {
            val cuerpo = response.errorBody()?.string()
            if (!esFotoYaExistente(response.code(), cuerpo)) {
                throw IllegalStateException(describirError(response.code(), cuerpo, "al subir foto $remoteFileName"))
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

    /** Usa el mensaje del servidor (PostgREST / Storage: `{"message": ...}`) si viene en la respuesta. */
    private fun describirError(code: Int, cuerpo: String?, contexto: String): String {
        val mensajeServidor = runCatching {
            JsonParser.parseString(cuerpo.orEmpty()).asJsonObject.get("message")?.asString
        }.getOrNull()
        return "HTTP $code $contexto" + (mensajeServidor?.let { ": $it" } ?: "")
    }

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
