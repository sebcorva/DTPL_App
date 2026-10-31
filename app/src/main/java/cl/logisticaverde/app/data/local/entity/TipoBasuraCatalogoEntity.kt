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
    val categoriaOrigen: String
) {
    companion object {
        const val ORIGEN_RECREATIVA = "Recreativa"
        const val ORIGEN_MARITIMA = "Marítima"
        const val ORIGEN_HIGIENE = "Higiene"
    }
}
