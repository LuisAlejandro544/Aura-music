package com.example.ui.screens.settings.components.appearance

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppWallpaperConfig
import com.example.model.WallpaperMediaType
import com.example.model.WallpaperScreenScope
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Componente modular de Ajustes para el Fondo de Pantalla Personalizado de Galería (Imagen WebP o Video corto MP4).
 *
 * Permite al usuario:
 * - Elegir una foto de su galería (convertida a WebP sin pérdida) o un video corto como fondo de pantalla.
 * - Configurar en qué pantallas se muestra (Solo en Tu Biblioteca, En Biblioteca e Inicio, o En toda la app).
 * - Ajustar el nivel de oscurecimiento (opacidad del velo OLED) para que los textos siempre se lean bien.
 * - Ajustar el nivel de desenfoque (blur) de 0 dp a 25 dp.
 */
fun LazyListScope.appWallpaperSettingsSection(
    wallpaperConfig: AppWallpaperConfig,
    onToggleWallpaperEnabled: (Boolean) -> Unit,
    onSelectWallpaperImage: (Uri) -> Unit,
    onSelectWallpaperVideo: (Uri) -> Unit,
    onClearWallpaper: () -> Unit,
    onSelectWallpaperScope: (WallpaperScreenScope) -> Unit,
    onChangeDimAlpha: (Float) -> Unit,
    onChangeBlurDp: (Int) -> Unit
) {
    item {
        Spacer(modifier = Modifier.height(14.dp))
        AppWallpaperCustomizationCard(
            wallpaperConfig = wallpaperConfig,
            onToggleWallpaperEnabled = onToggleWallpaperEnabled,
            onSelectWallpaperImage = onSelectWallpaperImage,
            onSelectWallpaperVideo = onSelectWallpaperVideo,
            onClearWallpaper = onClearWallpaper,
            onSelectWallpaperScope = onSelectWallpaperScope,
            onChangeDimAlpha = onChangeDimAlpha,
            onChangeBlurDp = onChangeBlurDp
        )
    }
}

@Composable
fun AppWallpaperCustomizationCard(
    wallpaperConfig: AppWallpaperConfig,
    onToggleWallpaperEnabled: (Boolean) -> Unit,
    onSelectWallpaperImage: (Uri) -> Unit,
    onSelectWallpaperVideo: (Uri) -> Unit,
    onClearWallpaper: () -> Unit,
    onSelectWallpaperScope: (WallpaperScreenScope) -> Unit,
    onChangeDimAlpha: (Float) -> Unit,
    onChangeBlurDp: (Int) -> Unit
) {
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onSelectWallpaperImage(uri)
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onSelectWallpaperVideo(uri)
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_wallpaper_settings_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Cabecera y Switch principal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Wallpaper,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Fondo de Pantalla de Galería (Biblioteca / App)",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Personaliza el fondo de tu Biblioteca o de toda la app con cualquier foto o video corto de tu galería.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }

                if (wallpaperConfig.mediaType != WallpaperMediaType.NONE && wallpaperConfig.mediaPath.isNotBlank()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = wallpaperConfig.isEnabled,
                        onCheckedChange = onToggleWallpaperEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.testTag("app_wallpaper_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Botones para elegir Imagen o Video de la Galería
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("pick_wallpaper_image_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Foto de Galería",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                FilledTonalButton(
                    onClick = {
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("pick_wallpaper_video_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Video de Fondo",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // Estado actual y botón para restaurar negro OLED
            if (wallpaperConfig.mediaType != WallpaperMediaType.NONE && wallpaperConfig.mediaPath.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141824))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Activo: ${wallpaperConfig.mediaType.label}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        )
                        Text(
                            text = wallpaperConfig.screenScope.label,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }

                    TextButton(
                        onClick = onClearWallpaper,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("clear_wallpaper_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Quitar fondo",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Quitar",
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Selector de en qué pantallas se muestra
            Text(
                text = "¿Dónde quieres mostrar el fondo?",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            WallpaperScreenScope.entries.forEach { scopeOption ->
                val isSelected = wallpaperConfig.screenScope == scopeOption
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color(0xFF141824),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else CardBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectWallpaperScope(scopeOption) }
                        .testTag("wallpaper_scope_${scopeOption.name}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = scopeOption.label,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else TextPrimary
                                )
                            )
                            Text(
                                text = scopeOption.description,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Seleccionado",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Control de Oscurecimiento (Opacidad del velo para legibilidad de textos)
            val dimPercent = (wallpaperConfig.dimOverlayAlpha * 100f).roundToInt()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Oscurecimiento para lectura (Opacidad)",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = "$dimPercent%",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Slider(
                value = wallpaperConfig.dimOverlayAlpha,
                onValueChange = onChangeDimAlpha,
                valueRange = 0.25f..0.92f,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wallpaper_dim_slider")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Control de Desenfoque (Blur)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Desenfoque del fondo (Blur)",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = if (wallpaperConfig.blurRadiusDp == 0) "Nítido (0 dp)" else "${wallpaperConfig.blurRadiusDp} dp",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Slider(
                value = wallpaperConfig.blurRadiusDp.toFloat(),
                onValueChange = { onChangeBlurDp(it.roundToInt()) },
                valueRange = 0f..25f,
                steps = 24,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wallpaper_blur_slider")
            )
        }
    }
}
