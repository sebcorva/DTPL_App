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
    val estado: String = ESTADO_OPERATIVO
) {
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
