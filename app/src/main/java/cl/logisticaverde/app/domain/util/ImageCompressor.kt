package cl.logisticaverde.app.domain.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Gestiona la creación de archivos temporales para CameraX/TakePicture y comprime
 * cada fotografía a un máximo de 1280x720 px (JPEG 75%, ~180 KB) para subida rápida a Supabase Storage.
 */
object ImageCompressor {

    private const val MAX_WIDTH = 1280
    private const val MAX_HEIGHT = 720
    private const val JPEG_QUALITY = 75

    fun createTempImageUri(context: Context, prefix: String): Pair<File, Uri> {
        val tempDir = File(context.cacheDir, "evidencias_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "${prefix}_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
        return tempFile to uri
    }

    suspend fun compressAndSaveEvidencePhoto(
        context: Context,
        sourceFile: File,
        idRegistro: String,
        tipoEvidencia: String
    ): String = withContext(Dispatchers.IO) {
        val outputDir = File(context.filesDir, "evidencias").apply { mkdirs() }
        val finalFile = File(outputDir, "${idRegistro}_${tipoEvidencia}.jpg")

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(sourceFile.absolutePath, options)

        options.inSampleSize = calculateInSampleSize(options, MAX_WIDTH, MAX_HEIGHT)
        options.inJustDecodeBounds = false

        val decodedBitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, options)
            ?: return@withContext sourceFile.absolutePath

        val scaledBitmap = scaleBitmapPreservingRatio(decodedBitmap, MAX_WIDTH, MAX_HEIGHT)

        FileOutputStream(finalFile).use { out ->
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.flush()
        }

        if (scaledBitmap != decodedBitmap) {
            scaledBitmap.recycle()
        }
        decodedBitmap.recycle()
        sourceFile.delete()

        finalFile.absolutePath
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun scaleBitmapPreservingRatio(
        bitmap: Bitmap,
        maxWidth: Int,
        maxHeight: Int
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxWidth && height <= maxHeight) return bitmap

        val ratioBitmap = width.toFloat() / height.toFloat()
        val ratioMax = maxWidth.toFloat() / maxHeight.toFloat()

        var finalWidth = maxWidth
        var finalHeight = maxHeight
        if (ratioMax > ratioBitmap) {
            finalWidth = (maxHeight.toFloat() * ratioBitmap).toInt()
        } else {
            finalHeight = (maxWidth.toFloat() / ratioBitmap).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, finalWidth.coerceAtLeast(1), finalHeight.coerceAtLeast(1), true)
    }
}
