package com.example.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.TextSecondary

/**
 * Pantalla modular de Bienvenida e Inducción para Aura Music.
 *
 * Estructura en 3 etapas:
 * 1. Bienvenida y Filosofía: Presentación del reproductor Dark Luxury Neo-Glass fuera de línea.
 * 2. Lo que ofrecemos: Explicación de capacidades (DSP C++20, Video Canvas, Karaoke LRCLIB, Video a Música).
 * 3. Disclaimer de Almacenamiento: Aviso amigable y transparente de que los Video Canvas pueden acumular
 *    espacio en el almacenamiento interno, y cómo gestionarlos desde Ajustes > Medios.
 */
@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val totalSteps = 3

    BackHandler(enabled = currentStep > 0) {
        currentStep -= 1
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("onboarding_screen"),
        containerColor = BackgroundDark,
        bottomBar = {
            OnboardingBottomControls(
                currentStep = currentStep,
                totalSteps = totalSteps,
                onPrevious = { if (currentStep > 0) currentStep -= 1 },
                onNext = {
                    if (currentStep < totalSteps - 1) {
                        currentStep += 1
                    } else {
                        onFinishOnboarding()
                    }
                },
                onSkip = onFinishOnboarding
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            BackgroundDark,
                            BackgroundDark
                        )
                    )
                )
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        (fadeIn(animationSpec = tween(240)) + slideInHorizontally { it / 4 })
                            .togetherWith(fadeOut(animationSpec = tween(180)) + slideOutHorizontally { -it / 4 })
                    } else {
                        (fadeIn(animationSpec = tween(240)) + slideInHorizontally { -it / 4 })
                            .togetherWith(fadeOut(animationSpec = tween(180)) + slideOutHorizontally { it / 4 })
                    }
                },
                label = "OnboardingStepTransition",
                modifier = Modifier.fillMaxSize()
            ) { step ->
                when (step) {
                    0 -> OnboardingWelcomeStep()
                    1 -> OnboardingFeaturesStep()
                    2 -> OnboardingStorageDisclaimerStep(onFinish = onFinishOnboarding)
                }
            }
        }
    }
}

/**
 * Fila inferior con controles de navegación, indicador de pasos (dots/pills) y botones de acción.
 */
@Composable
private fun OnboardingBottomControls(
    currentStep: Int,
    totalSteps: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Surface(
        color = BackgroundDark,
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Botón Izquierdo: Volver o Saltar
            if (currentStep > 0) {
                TextButton(
                    onClick = onPrevious,
                    modifier = Modifier.testTag("onboarding_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Paso anterior",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Atrás",
                        style = MaterialTheme.typography.labelLarge.copy(color = TextSecondary)
                    )
                }
            } else {
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.testTag("onboarding_skip_button")
                ) {
                    Text(
                        text = "Saltar",
                        style = MaterialTheme.typography.labelLarge.copy(color = TextSecondary)
                    )
                }
            }

            // Indicador de Pasos (Píldoras)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(totalSteps) { index ->
                    val isActive = index == currentStep
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(if (isActive) 24.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary else TextSecondary.copy(alpha = 0.3f)
                            )
                    )
                }
            }

            // Botón Derecho: Siguiente o Entrar
            if (currentStep < totalSteps - 1) {
                Button(
                    onClick = onNext,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.testTag("onboarding_next_button")
                ) {
                    Text(
                        text = "Siguiente",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Siguiente paso",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                TextButton(
                    onClick = onNext,
                    modifier = Modifier.testTag("onboarding_bottom_finish_button")
                ) {
                    Text(
                        text = "Entrar",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}
