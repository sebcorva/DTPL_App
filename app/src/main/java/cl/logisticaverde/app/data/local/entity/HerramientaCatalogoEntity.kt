package cl.logisticaverde.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "herramienta_catalogo")
data class HerramientaCatalogoEntity(
    @PrimaryKey
    @ColumnInfo(name = "qr_codigo")
    @SerializedName("qr_codigo")
    val qrCodigo: String,

    @ColumnInfo(name = "nombre_herramienta")
    @SerializedName("nombre_herramienta")
    val nombreHerramienta: String,

    @ColumnInfo(name = "estado")
    @SerializedName("estado")
    val estado: String = ESTADO_OPERATIVO,
    /**
     * ISSUE-43: en vez de borrar en Supabase, el administrador marca `is_activo = false`.
     * La app lo oculta al ingresar datos de jornadas nuevas; las jornadas ya hechas lo conservan.
     * Nullable a propósito: si el servidor aún no tiene la columna, Gson deja null y se trata como activo.
     */
    @ColumnInfo(name = "is_activo")
    @SerializedName("is_activo")
    val activo: Boolean? = true
) {
    /** Activo salvo que el servidor diga explícitamente lo contrario (null = activo). */
    val estaActivo: Boolean get() = activo != false

    companion object {
        const val ESTADO_OPERATIVO = "Operativo"
        const val ESTADO_DANADO = "Dañado"
        const val ESTADO_EXTRAVIADO = "Extraviado"

        val ESTADOS_DISPONIBLES = listOf(
            ESTADO_OPERATIVO,
            ESTADO_DANADO,
            ESTADO_EXTRAVIADO
        )
    }
}
