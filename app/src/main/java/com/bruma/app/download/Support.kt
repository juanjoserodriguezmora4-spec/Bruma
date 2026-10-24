package com.bruma.app.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException

object Net {
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

object Storage {
    fun freeBytes(context: Context): Long =
        runCatching {
            StatFs(context.filesDir.absolutePath).availableBytes
        }.getOrDefault(Long.MAX_VALUE)
}

/** Limita cuántas descargas corren al mismo tiempo. */
class ConcurrencyGate {
    private val mutex = Mutex()
    private var active = 0

    suspend fun acquire(max: suspend () -> Int) {
        while (true) {
            val limit = max()
            val ok =
                mutex.withLock {
                    if (active < limit) {
                        active++
                        true
                    } else {
                        false
                    }
                }
            if (ok) return
            delay(1000)
        }
    }

    suspend fun release() {
        mutex.withLock { active = (active - 1).coerceAtLeast(0) }
    }
}

object Mime {
    fun of(ext: String): String =
        when (ext.lowercase()) {
            "mp4" -> "video/mp4"
            "m4v" -> "video/x-m4v"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "flac" -> "audio/flac"
            "opus", "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            else -> "application/octet-stream"
        }
}

/** Guarda el archivo terminado en la carpeta pública (Música o Películas) para que otras apps lo vean. */
object MediaSaver {
    fun save(
        context: Context,
        src: File,
        isVideo: Boolean,
        folder: String,
    ): Uri {
        val mime = Mime.of(src.extension)
        val sub = folder.trim().ifBlank { "Bruma" }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection =
                if (isVideo) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }
            val values =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, src.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        (if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC) + "/" + sub,
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            val uri = resolver.insert(collection, values) ?: throw IOException("No se pudo crear el archivo de destino")
            try {
                val out = resolver.openOutputStream(uri) ?: throw IOException("No se pudo abrir el destino")
                out.use { o -> src.inputStream().use { it.copyTo(o) } }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
            uri
        } else {
            @Suppress("DEPRECATION")
            val base =
                Environment.getExternalStoragePublicDirectory(
                    if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC,
                )
            val dir = File(base, sub).apply { mkdirs() }
            var dest = File(dir, src.name)
            var n = 1
            while (dest.exists()) dest = File(dir, "${src.nameWithoutExtension} ($n).${src.extension}").also { n++ }
            src.copyTo(dest)
            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf(mime), null)
            Uri.fromFile(dest)
        }
    }

    fun delete(
        context: Context,
        uriString: String,
    ) {
        val uri = Uri.parse(uriString)
        runCatching {
            if (uri.scheme == "file") {
                uri.path?.let { File(it).delete() }
            } else {
                context.contentResolver.delete(uri, null, null)
            }
        }
    }
}
