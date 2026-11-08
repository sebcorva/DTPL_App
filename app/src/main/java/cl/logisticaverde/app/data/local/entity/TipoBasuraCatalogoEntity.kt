package cl.logisticaverde.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "tipo_basura_catalogo")
data class TipoBasuraCatalogoEntity(
    @PrimaryKey
    @ColumnInfo(name = "id_tipo_basura")
    @SerializedName("id_tipo_basura")
    val idTipoBasura: String,

    @ColumnInfo(name = "nombre_visible")
    @SerializedName("nombre_visible")
    val nombreVisible: String,

    @ColumnInfo(name = "categoria_origen")
    @SerializedName("categoria_origen")
    val categoriaOrigen: String,
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
        const val ORIGEN_RECREATIVA = "Recreativa"
        const val ORIGEN_MARITIMA = "Marítima"
        const val ORIGEN_HIGIENE = "Higiene"
    }
}
