package com.example.ui.screens.library.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Vista de estado vacío para la biblioteca con botón directo para importar canciones con SAF.
 */
@Composable
fun EmptyLibraryView(onImportClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(60.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Sin canciones en tu biblioteca",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Importa canciones de tu almacenamiento para comenzar a escuchar.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onImportClick,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Importar Música", fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Placeholder genérico para secciones vacías (ej: favoritos sin marcar, listas vacías).
 */
@Composable
fun EmptyPlaceholder(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(54.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
            textAlign = TextAlign.Center
        )
    }
}
