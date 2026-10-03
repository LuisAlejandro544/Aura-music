package com.example.ui.screens.library.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.Playlist

/**
 * Diálogo modal para crear una nueva lista de reproducción en la biblioteca.
 */
@Composable
fun CreatePlaylistDialog(
    onDismissRequest: () -> Unit,
    onCreatePlaylist: (String, String) -> Unit
) {
    var newPlaylistName by remember { mutableStateOf("") }
    var newPlaylistDesc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Crear Lista de Reproducción", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Nombre de la lista") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = newPlaylistDesc,
                    onValueChange = { newPlaylistDesc = it },
                    label = { Text("Descripción (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        onCreatePlaylist(newPlaylistName, newPlaylistDesc)
                        onDismissRequest()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Diálogo modal para renombrar una lista de reproducción existente.
 */
@Composable
fun RenamePlaylistDialog(
    playlist: Playlist,
    onDismissRequest: () -> Unit,
    onRenamePlaylist: (Long, String, String) -> Unit
) {
    var renamePlaylistName by remember { mutableStateOf(playlist.name) }
    var renamePlaylistDesc by remember { mutableStateOf(playlist.description) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Renombrar Lista de Reproducción", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = renamePlaylistName,
                    onValueChange = { renamePlaylistName = it },
                    label = { Text("Nombre de la lista") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = renamePlaylistDesc,
                    onValueChange = { renamePlaylistDesc = it },
                    label = { Text("Descripción (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (renamePlaylistName.isNotBlank()) {
                        onRenamePlaylist(playlist.id, renamePlaylistName, renamePlaylistDesc)
                        onDismissRequest()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancelar")
            }
        }
    )
}
