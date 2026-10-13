package com.example.feature.settings.components.media

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.Track
import com.example.ui.components.ArtworkImage
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File

/**
 * Video Canvas: tamaño, aspecto, borrado y exportación.
 * Rol arquitectónico: sección de StoredMediaTrackCard (fachada pública intacta).
 */
@Composable
fun StoredMediaVideoRow(
    track: Track,
    videoFileSizeStr: String,
    videoAspectTag: String?,
    onDeleteVideoClick: () -> Unit,
    onExportVideoClick: (() -> Unit)?,
    isVideoExported: Boolean,
    isExportingVideo: Boolean
) {
    val hasArt = !track.albumArtPath.isNullOrBlank()
    val hasVid = !track.videoUri.isNullOrBlank()
            // Gestión de Video Canvas y Exportación a Carpeta del Usuario
            if (hasVid) {
                if (hasArt) Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                modifier = Modifier.size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Video Canvas",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        ),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Text(
                                        text = "• $videoFileSizeStr",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.secondary
                                        ),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = if (track.isVideoLoop) "Loop 480p" else "Sincronizado 480p",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.secondary
                                            ),
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                    if (videoAspectTag != null) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                        ) {
                                            Text(
                                                text = videoAspectTag,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                ),
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = onDeleteVideoClick,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("delete_video_${track.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideocamOff,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Borrar video",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }

                    // Botón para exportar el vídeo con su audio original a la carpeta elegida por el usuario
                    if (onExportVideoClick != null) {
                        FilledTonalButton(
                            onClick = onExportVideoClick,
                            enabled = !isExportingVideo,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isVideoExported) {
                                    Color(0xFF10B981).copy(alpha = 0.16f)
                                } else {
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                                },
                                contentColor = if (isVideoExported) {
                                    Color(0xFF10B981)
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("export_video_${track.id}")
                        ) {
                            if (isExportingVideo) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.secondary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Uniendo vídeo y audio en tu carpeta...",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                )
                            } else {
                                Icon(
                                    imageVector = if (isVideoExported) Icons.Default.CheckCircle else Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isVideoExported) {
                                        "Guardado en tu carpeta (Volver a guardar con audio)"
                                    } else {
                                        "Guardar vídeo con audio en mi carpeta"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
}
