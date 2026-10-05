package com.example.playback.controllers

import com.example.model.ABLoopState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Aura Music - Controlador Modular del Repetidor de Segmento A-B (A-B Loop)
 *
 * Responsabilidades:
 * - Marcado de puntos de inicio (A) y final (B) en milisegundos con límites seguros.
 * - Ajuste fino interactivo de ±1s en tiempo real desde la hoja modal de efectos.
 * - Alternancia de bucle activo/inactivo y reinicio al cambiar de pista.
 */
class ABLoopController {

    private val _abLoopState = MutableStateFlow(ABLoopState())
    val abLoopState: StateFlow<ABLoopState> = _abLoopState.asStateFlow()

    fun markABPointA(positionMs: Long, durationMs: Long) {
        val current = _abLoopState.value
        val clampedA = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        val validB = current.pointBMs?.takeIf { it > clampedA + 400L }
        _abLoopState.value = ABLoopState(
            pointAMs = clampedA,
            pointBMs = validB,
            isEnabled = validB != null
        )
    }

    fun markABPointB(positionMs: Long, durationMs: Long) {
        val current = _abLoopState.value
        val clampedB = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        val validA = current.pointAMs ?: 0L
        if (clampedB > validA + 400L) {
            _abLoopState.value = ABLoopState(
                pointAMs = validA,
                pointBMs = clampedB,
                isEnabled = true
            )
        }
    }

    fun toggleABLoopEnabled(enabled: Boolean) {
        val current = _abLoopState.value
        if (current.pointAMs != null && current.pointBMs != null) {
            _abLoopState.value = current.copy(isEnabled = enabled)
        }
    }

    fun adjustABPointA(deltaMs: Long, durationMs: Long) {
        val current = _abLoopState.value
        val currentA = current.pointAMs ?: 0L
        val maxA = (current.pointBMs ?: durationMs) - 400L
        val newA = (currentA + deltaMs).coerceIn(0L, maxA.coerceAtLeast(0L))
        _abLoopState.value = current.copy(pointAMs = newA)
    }

    fun adjustABPointB(deltaMs: Long, durationMs: Long) {
        val current = _abLoopState.value
        val currentB = current.pointBMs ?: durationMs
        val minB = (current.pointAMs ?: 0L) + 400L
        val newB = (currentB + deltaMs).coerceIn(minB, durationMs.coerceAtLeast(minB))
        _abLoopState.value = current.copy(pointBMs = newB)
    }

    fun clearABLoop() {
        _abLoopState.value = ABLoopState()
    }

    fun reset() {
        _abLoopState.value = ABLoopState()
    }
}
