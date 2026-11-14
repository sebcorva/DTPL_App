package cl.logisticaverde.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "jornada",
    indices = [
        Index(value = ["estado_sync"])
    ]
)
data class JornadaEntity(
    @PrimaryKey
    @ColumnInfo(name = "id_registro")
    @SerializedName("id_registro")
    val idRegistro: String,

    @ColumnInfo(name = "pantalla_actual")
    val pantallaActual: Int = 2,

    // ISSUE-62: brigada elegida del catálogo de la playa (id para Supabase, nombre para mostrar)
    @ColumnInfo(name = "id_brigada")
    @SerializedName("id_brigada")
    val idBrigada: String = "",

    @ColumnInfo(name = "brigada")
    @SerializedName("brigada")
    val brigada: String = "",

    // Fecha de realización en hora de Chile (AAAA-MM-DD), fijada al crear la jornada en P2.
    // Es distinta de fecha_sincronizacion, que existe solo en Supabase.
    @ColumnInfo(name = "fecha_jornada")
    @SerializedName("fecha_jornada")
    val fechaJornada: String = "",

    @ColumnInfo(name = "id_playa")
    @SerializedName("id_playa")
    val idPlaya: String = "",

    @ColumnInfo(name = "nombre_playa")
    @SerializedName("nombre_playa")
    val nombrePlaya: String = "",

    @ColumnInfo(name = "nombre_lider")
    @SerializedName("nombre_lider")
    val nombreLider: String = "",

    @ColumnInfo(name = "voluntarios_csv")
    @SerializedName("voluntarios_csv")
    val voluntariosCsv: String = "",

    // 1 líder + N voluntarios; se mantiene igual a COUNT(participante_jornada)
    @ColumnInfo(name = "total_participantes")
    @SerializedName("total_participantes")
    val totalParticipantes: Int = 0,

    // IDs de sector separados por coma: reanudar P3 y deduplicar la densidad diaria
    @ColumnInfo(name = "sectores_ids_csv")
    @SerializedName("sectores_ids_csv")
    val sectoresIdsCsv: String = "",

    @ColumnInfo(name = "sectores_csv")
    @SerializedName("sectores_csv")
    val sectoresCsv: String = "",

    @ColumnInfo(name = "area_total_m2")
    @SerializedName("area_total_m2")
    val areaTotalM2: Int = 0,

    @ColumnInfo(name = "hora_inicio_ms")
    @SerializedName("hora_inicio_ms")
    val horaInicioMs: Long = 0L,

    @ColumnInfo(name = "hora_fin_ms")
    @SerializedName("hora_fin_ms")
    val horaFinMs: Long = 0L,

    // ISSUE-62: null = aún no hay lectura de GPS (antes 0, 0, que es un punto real en el golfo de Guinea)
    @ColumnInfo(name = "latitud_inicial")
    @SerializedName("latitud_inicial")
    val latitudInicial: Double? = null,

    @ColumnInfo(name = "longitud_inicial")
    @SerializedName("longitud_inicial")
    val longitudInicial: Double? = null,

    @ColumnInfo(name = "km_recorridos")
    @SerializedName("km_recorridos")
    val kmRecorridos: Float = 0f,

    // ISSUE-34: la brigada declaró en P4 que no lleva herramientas (se salta P7)
    @ColumnInfo(name = "sin_herramientas")
    @SerializedName("sin_herramientas")
    val sinHerramientas: Boolean = false,

    @ColumnInfo(name = "path_foto_inicio")
    val pathFotoInicio: String = "",

    @ColumnInfo(name = "path_foto_limpio")
    val pathFotoLimpio: String = "",

    @ColumnInfo(name = "path_foto_sacos")
    val pathFotoSacos: String = "",

    // ISSUE-62: ruta de la foto dentro del bucket (REG-…_1_inicio.jpg), no la URL.
    // null hasta que la foto se sube; un reintento no la vuelve a subir.
    @ColumnInfo(name = "ruta_foto_inicio")
    val rutaFotoInicio: String? = null,

    @ColumnInfo(name = "ruta_foto_limpio")
    val rutaFotoLimpio: String? = null,

    @ColumnInfo(name = "ruta_foto_sacos")
    val rutaFotoSacos: String? = null,

    // ISSUE-35: motivo obligatorio cuando se omite una foto (null si la foto se tomó)
    @ColumnInfo(name = "motivo_omision_inicio")
    @SerializedName("motivo_omision_inicio")
    val motivoOmisionInicio: String? = null,

    @ColumnInfo(name = "motivo_omision_limpio")
    @SerializedName("motivo_omision_limpio")
    val motivoOmisionLimpio: String? = null,

    @ColumnInfo(name = "motivo_omision_sacos")
    @SerializedName("motivo_omision_sacos")
    val motivoOmisionSacos: String? = null,

    @ColumnInfo(name = "cantidad_sacos")
    @SerializedName("cantidad_sacos")
    val cantidadSacos: Int = 0,

    @ColumnInfo(name = "kg_basura_normal")
    @SerializedName("kg_basura_normal")
    val kgBasuraNormal: Float = 0f,

    @ColumnInfo(name = "kg_basura_reciclable")
    @SerializedName("kg_basura_reciclable")
    val kgBasuraReciclable: Float = 0f,

    @ColumnInfo(name = "kg_totales")
    @SerializedName("kg_totales")
    val kgTotales: Float = 0f,

    @ColumnInfo(name = "encargado_pesaje")
    @SerializedName("encargado_pesaje")
    val encargadoPesaje: String = "",

    @ColumnInfo(name = "destino_basura_normal")
    @SerializedName("destino_basura_normal")
    val destinoBasuraNormal: String = DESTINO_NORMAL_DIMAO,

    @ColumnInfo(name = "nro_guia_despacho")
    @SerializedName("nro_guia_despacho")
    val nroGuiaDespacho: String? = null,

    @ColumnInfo(name = "destino_basura_reciclable")
    @SerializedName("destino_basura_reciclable")
    val destinoBasuraReciclable: String = DESTINO_RECICLABLE_PUNTO_LIMPIO,

    @ColumnInfo(name = "estado_sync")
    val estadoSync: String = SYNC_EN_CURSO,

    // ISSUE-42: motivo del rechazo (solo con estado ERROR_SYNC). Solo local: no viaja a Supabase.
    @ColumnInfo(name = "motivo_error_sync")
    val motivoErrorSync: String? = null
) {
    companion object {
        // Estados de sincronización local
        const val SYNC_EN_CURSO = "EN_CURSO"
        const val SYNC_PENDIENTE = "PENDIENTE_SYNC"
        const val SYNC_SINCRONIZADO = "SINCRONIZADO"
        // ISSUE-42: el servidor la rechazó (o falta una foto). No se edita; solo "Reintentar".
        const val SYNC_ERROR = "ERROR_SYNC"

        // Opciones de Entrega para Basura Normal (Pantalla 9)
        const val DESTINO_NORMAL_DIMAO = "Entregado a camión DIMAO"
        const val DESTINO_NORMAL_PENDIENTE = "Pendiente de retiro"
        const val DESTINO_NORMAL_NO_APLICA = "No aplicable"

        val OPCIONES_DESTINO_NORMAL = listOf(
            DESTINO_NORMAL_DIMAO,
            DESTINO_NORMAL_PENDIENTE,
            DESTINO_NORMAL_NO_APLICA
        )

        // Opciones de Entrega para Basura Reciclable (Pantalla 9)
        const val DESTINO_RECICLABLE_PUNTO_LIMPIO = "Entregado a Punto Limpio Municipal"
        const val DESTINO_RECICLABLE_RECICLADOR_BASE = "Entregado a Reciclador de Base"
        const val DESTINO_RECICLABLE_PENDIENTE = "Pendiente de entrega"
        const val DESTINO_RECICLABLE_NO_APLICA = "No aplicable"

        val OPCIONES_DESTINO_RECICLABLE = listOf(
            DESTINO_RECICLABLE_PUNTO_LIMPIO,
            DESTINO_RECICLABLE_RECICLADOR_BASE,
            DESTINO_RECICLABLE_PENDIENTE,
            DESTINO_RECICLABLE_NO_APLICA
        )
    }
}
