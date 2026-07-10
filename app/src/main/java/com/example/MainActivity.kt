package com.example

import android.os.Bundle
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import androidx.activity.ComponentActivity
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = PreferencesManager(this)
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(500L * 1024L * 1024L) // 500MB fixed size
                    .build()
            }
            .okHttpClient {
                val builder = OkHttpClient.Builder()
                
                // We use a custom TrustManager if allowInsecureSsl is enabled
                // But since it can change, we check it per request? No, it's easier to just use an insecure client
                // if it's currently true. For dynamic changes, we'll just allow all if it was true at startup,
                // or we use a wrapper.
                
                val trustAllCerts = arrayOf<TrustManager>(
                    object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                            if (!prefs.allowInsecureSsl) {
                                val defaultTrustManagerFactory = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm())
                                defaultTrustManagerFactory.init(null as java.security.KeyStore?)
                                val defaultTrustManager = defaultTrustManagerFactory.trustManagers[0] as X509TrustManager
                                defaultTrustManager.checkServerTrusted(chain, authType)
                            }
                        }
                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    }
                )
                try {
                    val sslContext = SSLContext.getInstance("SSL")
                    sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                    builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                    builder.hostnameVerifier { hostname, session -> 
                        if (prefs.allowInsecureSsl) true else javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                builder.build()
            }
            .build()
        Coil.setImageLoader(imageLoader)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
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
        setContent {
            val viewModel: GalleryViewModel = viewModel()
            val themeColorOption by viewModel.themeColorOption.collectAsState()
            val themeMode by viewModel.themeMode.collectAsState()

            val isSystemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                "Dark" -> true
                "Light" -> false
                else -> isSystemDark // "Auto"
            }

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = view.context.findActivity()?.window ?: return@SideEffect
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    val insetsController = WindowCompat.getInsetsController(window, view)
                    insetsController.isAppearanceLightStatusBars = !darkTheme
                    insetsController.isAppearanceLightNavigationBars = !darkTheme
                }
            }

            MyApplicationTheme(
                themeColorOption = themeColorOption,
                darkTheme = darkTheme
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