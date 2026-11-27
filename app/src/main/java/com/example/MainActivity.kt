package com.example

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.GalleryScreen
import com.example.ui.GalleryViewModel
import com.example.ui.LoginScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.data.PreferencesManager
import com.example.util.AppImageLoaderFactory
import com.example.util.LocaleHelper

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

class MainActivity : FragmentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            val prefs = PreferencesManager(this)
            com.example.util.AppImageLoaderFactory.install(this, prefs.allowInsecureSsl, force = true)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val tvCapabilities = com.example.util.TvModeHelper.capabilitiesFor(this)
        val isTvDevice = tvCapabilities.isTvMode

        if (!isTvDevice && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                val lp = window.attributes
                lp.layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = lp
            } catch (e: Exception) {
                // Ignore cutout configuration failure
            }
        }
        try {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                ),
                navigationBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                )
            )
        } catch (e: Exception) {
            // Safe fallback for window configuration
        }
        setContent {
            val viewModel: GalleryViewModel = viewModel()
            val themeColorOption by viewModel.themeColorOption.collectAsState()
            val themeMode by viewModel.themeMode.collectAsState()
            val maxBrightnessEnabled by viewModel.maxBrightnessEnabled.collectAsState()

            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                        if (tvCapabilities.allowItemLocking && viewModel.isBiometricLockEnabled.value) {
                            viewModel.lockApp()
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val isSystemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                "Dark", "Black", "OLED Black", "OLED" -> true
                "Light" -> false
                else -> if (isTvDevice) true else isSystemDark // "Auto" prefers comfortable dark UI on living-room TVs
            }
            val isOledMode = themeMode == "Black" || themeMode == "OLED Black" || themeMode == "OLED"

            val view = LocalView.current
            if (!view.isInEditMode) {
                @Suppress("DEPRECATION")
                SideEffect {
                    try {
                        val window = view.context.findActivity()?.window ?: return@SideEffect
                        window.statusBarColor = android.graphics.Color.TRANSPARENT
                        window.navigationBarColor = android.graphics.Color.TRANSPARENT
                        val insetsController = WindowCompat.getInsetsController(window, view)
                        insetsController.isAppearanceLightStatusBars = !darkTheme
                        insetsController.isAppearanceLightNavigationBars = !darkTheme

                        if (!isTvDevice) {
                            val layoutParams = window.attributes
                            val targetBrightness = if (maxBrightnessEnabled) 1.0f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                            if (layoutParams.screenBrightness != targetBrightness) {
                                layoutParams.screenBrightness = targetBrightness
                                window.attributes = layoutParams
                            }
                        }
                    } catch (e: Exception) {
                        // Prevent crash on startup if window attributes fail
                    }
                }
            }

            androidx.compose.runtime.CompositionLocalProvider(
                com.example.util.LocalIsAndroidTv provides isTvDevice
            ) {
                MyApplicationTheme(
                    themeColorOption = themeColorOption,
                    darkTheme = darkTheme,
                    isOledMode = isOledMode
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val isLoggedIn by viewModel.isLoggedIn.collectAsState()

                        if (isLoggedIn) {
                            GalleryScreen(viewModel = viewModel)
                        } else {
                            LoginScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}
