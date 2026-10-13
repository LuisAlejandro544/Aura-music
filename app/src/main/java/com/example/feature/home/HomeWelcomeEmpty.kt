package com.example.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.Playlist
import com.example.core.model.Track
import com.example.ui.components.ArtworkImage
import com.example.ui.components.TrackListItem
import com.example.ui.navigation.LibraryTab
import com.example.ui.navigation.NavScreen
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Calendar
/**
 * Tarjeta de bienvenida e importación cuando no hay música.
 * Rol arquitectónico: sección LazyColumn de HomeScreen (fachada intacta).
 */
fun LazyListScope.homeWelcomeEmpty(onNavigateImport: () -> Unit) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 520.dp), // Limitamos el ancho para que no se deforme en pantallas anchas
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        border = CardBorder.let { androidx.compose.foundation.BorderStroke(1.dp, it) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.LibraryMusic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = "Tu biblioteca está vacía",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (com.example.BuildConfig.ENABLE_DEMO_TRACKS) {
                                    "Aura Music respeta tu privacidad: no escanea tu teléfono automáticamente. Importa tus archivos o prueba con canciones demo."
                                } else {
                                    "Aura Music respeta tu privacidad: no escanea tu teléfono automáticamente. Importa tus archivos o descarga tu música favorita para empezar."
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(22.dp))
                            Button(
                                onClick = { onNavigateImport() },
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("home_import_cta")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Importar mi Música", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
        }
}
