package com.example.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember

/**
 * Immutable feature policy derived from whether the app is running in Android TV mode.
 * Acts as the single source of truth for enforcing Android TV restrictions across all
 * composables and ViewModel actions.
 */
@Immutable
data class TvFeatureCapabilities(
    val isTvMode: Boolean,
    val allowMultiSelect: Boolean = !isTvMode,
    val allowSharing: Boolean = !isTvMode,
    val allowShareLinks: Boolean = !isTvMode,
    val allowDownloads: Boolean = !isTvMode,
    val allowMapView: Boolean = !isTvMode,
    val allowAlbumManagement: Boolean = !isTvMode,
    val allowFavoriteMarking: Boolean = !isTvMode,
    val allowItemLocking: Boolean = !isTvMode,
    val allowTouchGestures: Boolean = !isTvMode
) {
    fun isSelectModeActive(requestedSelectMode: Boolean): Boolean =
        allowMultiSelect && requestedSelectMode

    fun canManageAlbums(serverAllowsAlbums: Boolean): Boolean =
        allowAlbumManagement && serverAllowsAlbums

    fun canCreateShareLinks(serverAllowsSharing: Boolean): Boolean =
        allowShareLinks && serverAllowsSharing

    fun shouldEnforceItemLocking(requested: Boolean): Boolean =
        allowItemLocking && requested
}

/**
 * Centralized helper utility to detect Android TV mode (including older Android 8/9 TV models)
 * and resolve the allowed feature capabilities.
 */
object TvModeHelper {
    fun isTvMode(context: Context): Boolean = DeviceUtils.isAndroidTv(context)

    fun isLegacyOrLowRamTv(context: Context): Boolean = DeviceUtils.isLegacyOrLowRamTv(context)

    fun capabilitiesFor(isTvMode: Boolean): TvFeatureCapabilities =
        TvFeatureCapabilities(isTvMode = isTvMode)

    fun capabilitiesFor(context: Context): TvFeatureCapabilities =
        capabilitiesFor(isTvMode(context))
}

/**
 * Composable helper that checks whether the app is currently in Android TV mode,
 * respecting [LocalIsAndroidTv] overrides in tests and previews.
 */
@Composable
fun rememberIsTvMode(): Boolean = rememberIsAndroidTv()

/**
 * Composable helper returning the memoized [TvFeatureCapabilities] for the current device mode.
 */
@Composable
fun rememberTvCapabilities(): TvFeatureCapabilities {
    val isTv = rememberIsTvMode()
    return remember(isTv) { TvModeHelper.capabilitiesFor(isTv) }
}
