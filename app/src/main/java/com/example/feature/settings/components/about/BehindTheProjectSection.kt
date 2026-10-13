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
 * Contenido Lazy del apartado Detrás del Proyecto (banner, creador, contribuidores y licencia).
 * Rol arquitectónico: orquesta las tarjetas del apartado en 4 bloques.
 */
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
