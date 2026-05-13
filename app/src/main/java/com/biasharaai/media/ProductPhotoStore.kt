package com.biasharaai.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Saves product images under [Context.getFilesDir]/`product_photos/` as scaled JPEGs.
 * Paths are stored in [com.biasharaai.data.local.db.Product.imageUrl] as absolute file paths.
 */
@Singleton
class ProductPhotoStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val photoDir: File
        get() = File(context.filesDir, "product_photos").apply { mkdirs() }

    fun isAppStoredAbsolutePath(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        return try {
            val dir = photoDir.canonicalPath
            File(path).canonicalPath.startsWith(dir)
        } catch (_: Exception) {
            false
        }
    }

    /** Empty file for [androidx.activity.result.contract.ActivityResultContracts.TakePicture]. */
    fun createCameraCaptureFile(): File =
        File(photoDir, "capture_${UUID.randomUUID()}.jpg").apply {
            parentFile?.mkdirs()
            if (!exists()) createNewFile()
        }

    /**
     * Reads [sourceFile], scales so the longest edge is at most [maxEdgePx], writes JPEG to a new file.
     * Deletes [sourceFile] when it lives under app photo storage and is not the same as the output file.
     */
    fun saveScaledJpeg(sourceFile: File, maxEdgePx: Int = 1024, quality: Int = 82): String? {
        val decoded = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return null
        val scaled = scaleDown(decoded, maxEdgePx)
        if (scaled !== decoded) decoded.recycle()
        return try {
            val out = File(photoDir, "product_${System.currentTimeMillis()}.jpg")
            FileOutputStream(out).use { fos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            }
            if (sourceFile.absolutePath != out.absolutePath && isAppStoredAbsolutePath(sourceFile.absolutePath)) {
                sourceFile.delete()
            }
            out.absolutePath
        } catch (_: Exception) {
            null
        } finally {
            if (!scaled.isRecycled) scaled.recycle()
        }
    }

    fun saveScaledFromContentUri(uri: Uri, maxEdgePx: Int = 1024, quality: Int = 82): String? {
        val decoded = try {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        } ?: return null
        val scaled = scaleDown(decoded, maxEdgePx)
        if (scaled !== decoded) decoded.recycle()
        return try {
            val out = File(photoDir, "product_${System.currentTimeMillis()}.jpg")
            FileOutputStream(out).use { fos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            }
            out.absolutePath
        } catch (_: Exception) {
            null
        } finally {
            if (!scaled.isRecycled) scaled.recycle()
        }
    }

    fun deleteIfAppStored(path: String?) {
        if (path.isNullOrBlank()) return
        if (!isAppStoredAbsolutePath(path)) return
        runCatching { File(path).delete() }
    }

    private fun scaleDown(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longest = max(w, h)
        if (longest <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / longest
        val tw = (w * scale).roundToInt().coerceAtLeast(1)
        val th = (h * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, tw, th, true)
    }
}
