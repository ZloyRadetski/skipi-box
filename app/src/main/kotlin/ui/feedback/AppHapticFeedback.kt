// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.staticCompositionLocalOf

val LocalAppHaptics = staticCompositionLocalOf<AppHapticFeedback> {
    error("No AppHapticFeedback provided!")
}

class AppHapticFeedback(
    private val context: Context,
    private val isHapticsEnabled: () -> Boolean = { true },
) {
    private val vibrator: Vibrator? by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }

    /** Short crisp click when tapping the connect / power button. */
    fun vpnToggle() {
        if (!isHapticsEnabled()) return
        performClick()
    }

    /** Distinct pleasant double pulse upon successful VPN connection. */
    fun vpnConnected() {
        if (!isHapticsEnabled()) return
        performConnected()
    }

    /** Gentle fading pulse when the VPN disconnects. */
    fun vpnDisconnected() {
        if (!isHapticsEnabled()) return
        performDisconnected()
    }

    /** Warning/error vibration pattern on connection failure. */
    fun vpnError() {
        if (!isHapticsEnabled()) return
        performError()
    }

    /** Delicate tick when selecting a server. */
    fun serverSelected() {
        if (!isHapticsEnabled()) return
        performTick()
    }

    /** Subtle tick when switching group tabs. */
    fun groupSwitched() {
        if (!isHapticsEnabled()) return
        performTick()
    }

    /** Confirmation feedback for successful operations. */
    fun actionSuccess() {
        if (!isHapticsEnabled()) return
        performConnected()
    }

    /** Warning feedback for destructive actions or alerts. */
    fun actionWarning() {
        if (!isHapticsEnabled()) return
        performHeavyClick()
    }

    private fun performClick() {
        vibratePredefinedOrFallback(
            predefinedEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.EFFECT_CLICK else -1,
            fallbackMillis = 20L,
            fallbackAmplitude = VibrationEffect.DEFAULT_AMPLITUDE,
        )
    }

    private fun performTick() {
        vibratePredefinedOrFallback(
            predefinedEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.EFFECT_TICK else -1,
            fallbackMillis = 10L,
            fallbackAmplitude = 80,
        )
    }

    private fun performHeavyClick() {
        vibratePredefinedOrFallback(
            predefinedEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.EFFECT_HEAVY_CLICK else -1,
            fallbackMillis = 35L,
            fallbackAmplitude = 200,
        )
    }

    private fun performConnected() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                vib.vibrate(effect)
                return
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 20, 60, 30)
            val amplitudes = intArrayOf(0, 150, 0, 220)
            runCatching {
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vib.vibrate(effect)
                return
            }
        }
        @Suppress("DEPRECATION")
        runCatching {
            vib.vibrate(40L)
        }
    }

    private fun performDisconnected() {
        vibratePredefinedOrFallback(
            predefinedEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.EFFECT_TICK else -1,
            fallbackMillis = 15L,
            fallbackAmplitude = 100,
        )
    }

    private fun performError() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 40, 80, 60)
            val amplitudes = intArrayOf(0, 255, 0, 255)
            runCatching {
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vib.vibrate(effect)
                return
            }
        }
        @Suppress("DEPRECATION")
        runCatching {
            vib.vibrate(100L)
        }
    }

    private fun vibratePredefinedOrFallback(
        predefinedEffect: Int,
        fallbackMillis: Long,
        fallbackAmplitude: Int,
    ) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && predefinedEffect != -1) {
            runCatching {
                val effect = VibrationEffect.createPredefined(predefinedEffect)
                vib.vibrate(effect)
                return
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val effect = VibrationEffect.createOneShot(fallbackMillis, fallbackAmplitude.coerceIn(1, 255))
                vib.vibrate(effect)
                return
            }
        }
        @Suppress("DEPRECATION")
        runCatching {
            vib.vibrate(fallbackMillis)
        }
    }
}
