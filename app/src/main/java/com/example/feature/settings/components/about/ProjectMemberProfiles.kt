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
 * Perfiles del proyecto (creador y contribuidores oficiales).
 * Rol arquitectónico: datos estáticos de identidad y reconocimiento.
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

internal val CREATOR_PROFILE = ProjectMemberProfile(
    displayName = "Luis Alejandro Sosa Camacho",
    githubUsername = "LuisAlejandro544",
    githubUrl = "https://github.com/LuisAlejandro544",
    roleBadge = "CREADOR & FUNDADOR",
    roleSubtitle = "Creador, Arquitecto Principal & Desarrollador Lead",
    bioDescription = "Creador y propietario intelectual de Aura Music. Diseño de la arquitectura híbrida Kotlin + ISO C++20 DSP, motor de Video Canvas, extracción resiliente y experiencia Dark Luxury Neo-Glass.",
    isCreator = true
)

internal val CONTRIBUTOR_PROFILES = listOf(
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
