package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object ImageUtils {
    private const val MAX_DIMENSION_PX = 480
    private const val MAX_RAW_BYTES = 54_000

    private val decodedBitmapCache = object : LruCache<String, Bitmap>(28) {}

    fun encodeUriToCompressedBase64(context: Context, uri: Uri): String? {
        return try {
            val original = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } ?: return null

            val maxSide = max(original.width, original.height)
            val scaled = if (maxSide > MAX_DIMENSION_PX) {
                val scale = MAX_DIMENSION_PX.toFloat() / maxSide.toFloat()
                val w = (original.width * scale).roundToInt().coerceAtLeast(1)
                val h = (original.height * scale).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(original, w, h, true)
            } else {
                original
            }

            var quality = 75
            var bytes: ByteArray
            do {
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                bytes = baos.toByteArray()
                quality -= 12
            } while (bytes.size > MAX_RAW_BYTES && quality >= 25)

            if (scaled != original) original.recycle()
            if (bytes.size > MAX_RAW_BYTES) return null

            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    fun getCachedBitmap(base64: String): Bitmap? {
        val key = cacheKey(base64)
        return decodedBitmapCache.get(key)
    }

    suspend fun decodeBase64ToBitmapAsync(base64: String): Bitmap? {
        val key = cacheKey(base64)
        decodedBitmapCache.get(key)?.let { return it }
        return withContext(Dispatchers.Default) {
            decodedBitmapCache.get(key)?.let { return@withContext it }
            decodeBase64ToBitmap(base64)?.also { bmp ->
                decodedBitmapCache.put(key, bmp)
            }
        }
    }

    fun decodeBase64ToBitmap(base64: String): Bitmap? {
        val key = cacheKey(base64)
        decodedBitmapCache.get(key)?.let { return it }
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { bmp ->
                decodedBitmapCache.put(key, bmp)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun cacheKey(base64: String): String {
        return "${base64.length}_${base64.hashCode()}"
    }
}
