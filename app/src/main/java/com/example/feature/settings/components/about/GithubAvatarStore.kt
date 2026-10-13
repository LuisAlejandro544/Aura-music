package com.example.feature.settings.components.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.core.data.storage.AppStorageManager
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL


/**
 * Gestor de caché persistente de avatares de GitHub en `images/github_avatar_<user>.webp`.
 * Regla clave: Si el archivo ya existe localmente en el teléfono, lo decodifica directamente
 * sin hacer ninguna petición de red (se descarga una sola vez en toda la vida de la instalación).
 */
/**
 * Gestor de caché persistente de avatares de GitHub en `images/github_avatar_<user>.webp`.
 * Regla clave: Si el archivo ya existe localmente en el teléfono, lo decodifica directamente
 * sin hacer ninguna petición de red (se descarga una sola vez en toda la vida de la instalación).
 */
internal object GithubAvatarPersistentStore {
    suspend fun loadOrDownloadOnce(context: Context, githubUsername: String): Bitmap? = withContext(Dispatchers.IO) {
        val safeUser = githubUsername.replace(Regex("[^a-zA-Z0-9_-]"), "_").lowercase()
        val storageManager = AppStorageManager(context)
        val avatarFile = File(storageManager.imagesDir, "github_avatar_${safeUser}.webp")

        // 1. Si ya se descargó antes y está guardado en disco, usarlo directamente sin gastar red
        if (avatarFile.exists() && avatarFile.length() > 128L) {
            try {
                val cachedBitmap = BitmapFactory.decodeFile(avatarFile.absolutePath)
                if (cachedBitmap != null && cachedBitmap.width > 16 && cachedBitmap.height > 16) {
                    return@withContext cachedBitmap
                }
            } catch (_: Exception) {
                // Si el archivo estuviera corrupto, proceder a re-descargar
            }
        }

        // 2. Si no existe en disco y hay internet, descargarlo una única vez y guardarlo en WebP
        val avatarUrls = listOf(
            "https://github.com/${githubUsername}.png?size=240",
            "https://avatars.githubusercontent.com/${githubUsername}?s=240"
        )

        for (candidateUrl in avatarUrls) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(candidateUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    instanceFollowRedirects = true
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14) AuraMusic/${BuildConfig.VERSION_NAME}"
                    )
                    setRequestProperty("Accept", "image/webp,image/png,image/*,*/*;q=0.8")
                }

                if (connection.responseCode in 200..299) {
                    val bytes = connection.inputStream.use { it.readBytes() }
                    if (bytes.isNotEmpty()) {
                        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (decoded != null && decoded.width > 16 && decoded.height > 16) {
                            val tempFile = File(storageManager.imagesDir, "github_avatar_${safeUser}.tmp")
                            FileOutputStream(tempFile).use { out ->
                                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    Bitmap.CompressFormat.WEBP_LOSSLESS
                                } else {
                                    @Suppress("DEPRECATION")
                                    Bitmap.CompressFormat.WEBP
                                }
                                decoded.compress(format, 100, out)
                            }
                            if (tempFile.exists() && tempFile.length() > 128L) {
                                if (avatarFile.exists()) avatarFile.delete()
                                tempFile.renameTo(avatarFile)
                            } else {
                                tempFile.delete()
                            }
                            return@withContext decoded
                        }
                    }
                }
            } catch (_: Exception) {
                // Sin conexión o error temporal; continuar con el siguiente candidato o usar arte procedural
            } finally {
                try {
                    connection?.disconnect()
                } catch (_: Exception) {}
            }
        }

        null
    }
}
