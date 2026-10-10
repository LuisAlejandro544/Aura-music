package com.example.ui.screens.settings.components.about

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
import com.example.data.storage.AppStorageManager
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
 * Pantalla dedicada de "Detrás del Proyecto" (Creador & Contribuidores de Aura Music).
 *
 * Rol arquitectónico:
 * - Presenta la tarjeta oficial del Creador (Luis Alejandro Sosa Camacho - @LuisAlejandro544)
 *   y de los Contribuidores Oficiales (@thelandy03-boop).
 * - Descarga una única vez el avatar de GitHub en segundo plano (Dispatchers.IO) cuando hay
 *   conexión a internet y lo persiste permanentemente en formato WebP sin pérdida dentro de
 *   `Android/data/.../files/images/github_avatar_<username>.webp`.
 * - En futuras visitas (con o sin conexión a internet), carga instantáneamente el archivo local
 *   guardado en disco, evitando volver a descargarlo o mostrar iconos genéricos vacíos.
 */

data class ProjectMemberProfile(
    val displayName: String,
    val githubUsername: String,
    val githubUrl: String,
    val roleBadge: String,
    val roleSubtitle: String,
    val bioDescription: String,
    val isCreator: Boolean
)

private val CREATOR_PROFILE = ProjectMemberProfile(
    displayName = "Luis Alejandro Sosa Camacho",
    githubUsername = "LuisAlejandro544",
    githubUrl = "https://github.com/LuisAlejandro544",
    roleBadge = "CREADOR & FUNDADOR",
    roleSubtitle = "Creador, Arquitecto Principal & Desarrollador Lead",
    bioDescription = "Creador y propietario intelectual de Aura Music. Diseño de la arquitectura híbrida Kotlin + ISO C++20 DSP, motor de Video Canvas, extracción resiliente y experiencia Dark Luxury Neo-Glass.",
    isCreator = true
)

private val CONTRIBUTOR_PROFILES = listOf(
    ProjectMemberProfile(
        displayName = "thelandy03-boop",
        githubUsername = "thelandy03-boop",
        githubUrl = "https://github.com/thelandy03-boop",
        roleBadge = "CONTRIBUIDOR OFICIAL",
        roleSubtitle = "Colaborador de Código, Correcciones & Mejoras",
        bioDescription = "Contribuidor oficial reconocido en el desarrollo de Aura Music, aportando commits de corrección, mejoras de estabilidad y pruebas técnicas en el repositorio.",
        isCreator = false
    )
)

/**
 * Gestor de caché persistente de avatares de GitHub en `images/github_avatar_<user>.webp`.
 * Regla clave: Si el archivo ya existe localmente en el teléfono, lo decodifica directamente
 * sin hacer ninguna petición de red (se descarga una sola vez en toda la vida de la instalación).
 */
private object GithubAvatarPersistentStore {

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

fun LazyListScope.behindTheProjectSettingsContent() {
    // 1. Banner de Identidad del Proyecto
    item {
        ProjectIdentityHeaderCard()
        Spacer(modifier = Modifier.height(20.dp))
    }

    // 2. Sección del Creador Principal
    item {
        SectionHeaderRow(
            icon = Icons.Default.Star,
            title = "Creador del Proyecto",
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(10.dp))
        DeveloperProfileCard(
            profile = CREATOR_PROFILE,
            accentColor = MaterialTheme.colorScheme.primary,
            testTagPrefix = "creator_profile"
        )
        Spacer(modifier = Modifier.height(22.dp))
    }

    // 3. Sección de Contribuidores Oficiales
    item {
        SectionHeaderRow(
            icon = Icons.Default.Groups,
            title = "Contribuidores Oficiales",
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(10.dp))
    }

    items(CONTRIBUTOR_PROFILES.size) { index ->
        val contributor = CONTRIBUTOR_PROFILES[index]
        DeveloperProfileCard(
            profile = contributor,
            accentColor = MaterialTheme.colorScheme.secondary,
            testTagPrefix = "contributor_profile_${contributor.githubUsername}"
        )
        Spacer(modifier = Modifier.height(14.dp))
    }

    // 4. Tarjeta de Licencia y Propiedad Intelectual
    item {
        Spacer(modifier = Modifier.height(8.dp))
        ProjectLicenseSummaryCard()
    }
}

@Composable
private fun SectionHeaderRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        )
    }
}

@Composable
private fun ProjectIdentityHeaderCard() {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, primary.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("behind_project_header_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            primary.copy(alpha = 0.14f),
                            SurfaceCard,
                            secondary.copy(alpha = 0.10f)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = primary.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, primary.copy(alpha = 0.5f)),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Aura Music • ${BuildConfig.APP_CODENAME}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = "Versión ${BuildConfig.VERSION_NAME} (${BuildConfig.APP_BUILD_CODE})",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Ingeniería de audio en ISO C++20, Video Canvas reactivo y descargas de alta velocidad diseñadas para funcionar al 100% directamente desde tu móvil.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            )
        }
    }
}

@Composable
private fun DeveloperProfileCard(
    profile: ProjectMemberProfile,
    accentColor: Color,
    testTagPrefix: String
) {
    val context = LocalContext.current
    var avatarBitmap by remember(profile.githubUsername) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(profile.githubUsername) {
        avatarBitmap = GithubAvatarPersistentStore.loadOrDownloadOnce(context, profile.githubUsername)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(
            width = if (profile.isCreator) 1.5.dp else 1.dp,
            color = if (profile.isCreator) accentColor.copy(alpha = 0.65f) else CardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar persistente en WebP o insignia procedural de alta estética si nunca hubo red
                PersistentGithubAvatar(
                    bitmap = avatarBitmap,
                    displayName = profile.displayName,
                    accentColor = accentColor
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Insignia de Rol
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accentColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = profile.roleBadge,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = accentColor,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "@${profile.githubUsername}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = accentColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = profile.roleSubtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = profile.bioDescription,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Botones de acción (mínimo 48.dp de alto para accesibilidad táctil)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { openExternalGithubUrl(context, profile.githubUrl) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("${testTagPrefix}_open_github_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ver Perfil en GitHub",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                OutlinedIconButton(
                    onClick = {
                        copyProfileLinkToClipboard(context, profile.displayName, profile.githubUrl)
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("${testTagPrefix}_copy_link_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copiar enlace de GitHub de ${profile.displayName}",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PersistentGithubAvatar(
    bitmap: Bitmap?,
    displayName: String,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .border(2.dp, accentColor.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Foto de perfil de $displayName",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Arte procedural matemático elegante en caso de primer arranque sin internet
            val initials = remember(displayName) {
                displayName
                    .trim()
                    .split(Regex("\\s+|-"))
                    .filter { it.isNotBlank() }
                    .take(2)
                    .joinToString("") { it.first().uppercaseChar().toString() }
                    .ifEmpty { "AM" }
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.45f),
                            Color(0xFF141824),
                            accentColor.copy(alpha = 0.25f)
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                )
                drawCircle(
                    color = accentColor.copy(alpha = 0.18f),
                    radius = size.minDimension * 0.36f,
                    center = Offset(size.width * 0.75f, size.height * 0.25f)
                )
            }
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    letterSpacing = 1.sp
                )
            )
        }
    }
}

@Composable
private fun ProjectLicenseSummaryCard() {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("behind_project_license_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Gavel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Titularidad y Reconocimiento Oficial",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Copyright © 2026 Luis Alejandro Sosa Camacho. Todos los derechos reservados.\n\n" +
                    "Aura Music se distribuye bajo Licencia Propietaria de Código Visible (Source-Available). " +
                    "Luis Alejandro Sosa Camacho es el único propietario y titular de los derechos del proyecto, " +
                    "otorgando reconocimiento y crédito oficial a los colaboradores autorizados que aportan al desarrollo.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            )
        }
    }
}

private fun openExternalGithubUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No se pudo abrir el navegador para $url", Toast.LENGTH_SHORT).show()
    }
}

private fun copyProfileLinkToClipboard(context: Context, name: String, url: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("GitHub $name", url))
        Toast.makeText(context, "Enlace de GitHub copiado: $url", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {}
}
