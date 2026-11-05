package com.example

import android.os.Bundle
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
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
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            val prefs = PreferencesManager(this)
            val imageLoader = ImageLoader.Builder(this)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.20) // Conservative 20% of heap to prevent OOM during heavy scrolling
                        .strongReferencesEnabled(true)
                        .weakReferencesEnabled(true)
                        .build()
                }
                .diskCache {
                    try {
                        DiskCache.Builder()
                            .directory(this.cacheDir.resolve("image_cache"))
                            .maxSizeBytes(1024L * 1024L * 1024L) // 1GB disk cache
                            .build()
                    } catch (e: Exception) {
                        null
                    }
                }
                .allowHardware(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) // Store bitmaps in graphic memory instead of Java heap
                .respectCacheHeaders(false) // Cache images aggressively even if the server returns no-cache/no-store headers
                .okHttpClient {
                    val dispatcher = okhttp3.Dispatcher().apply {
                        maxRequests = 24 // Balanced concurrency to avoid saturating Okio segment buffers
                        maxRequestsPerHost = 8
                    }
                    val builder = OkHttpClient.Builder()
                        .dispatcher(dispatcher)
                        .connectionPool(okhttp3.ConnectionPool(10, 2, java.util.concurrent.TimeUnit.MINUTES))
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
                    
                    if (prefs.allowInsecureSsl) {
                        try {
                            val trustAllCerts = arrayOf<TrustManager>(
                                object : X509TrustManager {
                                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                                }
                            )
                            val sslContext = SSLContext.getInstance("TLS")
                            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                            builder.hostnameVerifier { _, _ -> true }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    
                    builder.build()
                }
                .build()
            Coil.setImageLoader(imageLoader)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
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
                        if (viewModel.isBiometricLockEnabled.value) {
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
                else -> isSystemDark // "Auto"
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

                        val layoutParams = window.attributes
                        val targetBrightness = if (maxBrightnessEnabled) 1.0f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                        if (layoutParams.screenBrightness != targetBrightness) {
                            layoutParams.screenBrightness = targetBrightness
                            window.attributes = layoutParams
                        }
                    } catch (e: Exception) {
                        // Prevent crash on startup if window attributes fail
                    }
                }
            }

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
