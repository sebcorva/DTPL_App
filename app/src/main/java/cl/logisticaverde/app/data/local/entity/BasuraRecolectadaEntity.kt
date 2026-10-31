package cl.logisticaverde.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "basura_recolectada",
    primaryKeys = ["id_registro", "id_tipo_basura"],
    foreignKeys = [
        ForeignKey(
            entity = JornadaEntity::class,
            parentColumns = ["id_registro"],
            childColumns = ["id_registro"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TipoBasuraCatalogoEntity::class,
            parentColumns = ["id_tipo_basura"],
            childColumns = ["id_tipo_basura"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["id_registro"]),
        Index(value = ["id_tipo_basura"])
    ]
)
data class BasuraRecolectadaEntity(
    @ColumnInfo(name = "id_registro")
    @SerializedName("id_registro")
    val idRegistro: String,

    @ColumnInfo(name = "id_tipo_basura")
    @SerializedName("id_tipo_basura")
    val idTipoBasura: String,

    @ColumnInfo(name = "nombre_visible")
    @SerializedName("nombre_visible")
    val nombreVisible: String,

    @ColumnInfo(name = "categoria_origen")
    @SerializedName("categoria_origen")
    val categoriaOrigen: String,

    @ColumnInfo(name = "cantidad")
    @SerializedName("cantidad")
    val cantidad: Int = 0
)
