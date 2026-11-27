package com.example.util

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Allows overriding Android TV detection cleanly in previews and tests while defaulting
 * to automatic runtime device detection via [DeviceUtils.isAndroidTv].
 */
val LocalIsAndroidTv = staticCompositionLocalOf<Boolean?> { null }

/**
 * Centralized hardware and form-factor detection utilities with compatibility for
 * legacy Android TV models (Android 8/9 / API 26–28, e.g. MiTV-MSSP3 and AOSP TV boxes).
 */
object DeviceUtils {

    /**
     * Detects Android TV reliably across modern Leanback devices and older/OEM TV models
     * (including Android 9 / API 28 devices where [UiModeManager] or legacy [PackageManager.FEATURE_TELEVISION]
     * may be reported alongside or instead of [PackageManager.FEATURE_LEANBACK]).
     */
    fun isAndroidTv(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true
        }
        val configUiMode = context.resources?.configuration?.uiMode
        if (configUiMode != null &&
            (configUiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
        ) {
            return true
        }
        val pm = context.packageManager ?: return false
        @Suppress("DEPRECATION")
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    }

    /**
     * Returns true for low-RAM devices or older Android TV hardware (Android 9 / API 28 and below)
     * that benefit from conservative bitmap caching, software-safe decoding, and lighter video buffers.
     */
    fun isLegacyOrLowRamTv(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = am?.isLowRamDevice == true
        return isLowRam || (isAndroidTv(context) && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P)
    }
}

@Composable
fun rememberIsAndroidTv(): Boolean {
    val override = LocalIsAndroidTv.current
    if (override != null) return override
    val context = LocalContext.current
    return remember(context) { DeviceUtils.isAndroidTv(context) }
}
