package cl.logisticaverde.app.ui.screens.resumen

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * ISSUE-26 (decisión 31-10-2026): sin META_APP_ID. Se envía la imagen directo a Instagram,
 * donde el usuario elige Historia, Feed o Mensaje; sin Instagram, se abre el selector de Android.
 */
object CompartirResumen {

    private const val PAQUETE_INSTAGRAM = "com.instagram.android"
    private const val CARPETA_GALERIA = "DejaTuPlayaLimpia"

    /** Android 8 y 9 necesitan WRITE_EXTERNAL_STORAGE para guardar en la galería. */
    val requierePermisoGaleria: Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    fun compartir(context: Context, archivo: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)
        val base = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent(base).setPackage(PAQUETE_INSTAGRAM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent.createChooser(base, "Compartir resumen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Guarda en Pictures/DejaTuPlayaLimpia. Devuelve true si quedó guardada. */
    fun guardarEnGaleria(context: Context, bitmap: Bitmap, idRegistro: String): Boolean = runCatching {
        val nombre = "resumen_${idRegistro.replace(Regex("[^A-Za-z0-9_-]"), "_")}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val valores = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, nombre)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$CARPETA_GALERIA")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, valores)
                ?: error("MediaStore no devolvió URI")
            try {
                resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    ?: error("No se pudo abrir el archivo")
                valores.clear()
                valores.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, valores, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val carpeta = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), CARPETA_GALERIA)
            carpeta.mkdirs()
            val archivo = File(carpeta, nombre)
            FileOutputStream(archivo).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            MediaScannerConnection.scanFile(context, arrayOf(archivo.absolutePath), arrayOf("image/png"), null)
        }
        true
    }.getOrDefault(false)
}
