package com.example.util

import android.content.Context
import android.os.Build
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Builds and installs the shared Coil ImageLoader so SSL trust settings
 * can be updated live without requiring an app restart.
 */
object AppImageLoaderFactory {

    @Volatile
    private var lastInstalledInsecureSsl: Boolean? = null

    fun create(context: Context, allowInsecureSsl: Boolean): ImageLoader {
        val appContext = context.applicationContext ?: context
        val isTv = DeviceUtils.isAndroidTv(appContext)
        val isLegacyOrLowRamTv = DeviceUtils.isLegacyOrLowRamTv(appContext)
        val memoryPercent = when {
            isLegacyOrLowRamTv -> 0.12
            isTv -> 0.16
            else -> 0.20
        }
        val diskCacheBytes = when {
            isLegacyOrLowRamTv -> 192L * 1024L * 1024L // 192MB for older TVs with small eMMC storage
            isTv -> 384L * 1024L * 1024L
            else -> 1024L * 1024L * 1024L // 1GB disk cache
        }
        // Older Android 8/9 TV GPUs (e.g. MiTV API 28) can crash or OOM with hardware bitmaps
        // under Compose transforms; disable hardware bitmaps on legacy/low-RAM TVs.
        val allowHardwareBitmaps = !isLegacyOrLowRamTv && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        return ImageLoader.Builder(appContext)
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(memoryPercent)
                    .strongReferencesEnabled(true)
                    .weakReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                try {
                    DiskCache.Builder()
                        .directory(appContext.cacheDir.resolve("image_cache"))
                        .maxSizeBytes(diskCacheBytes)
                        .build()
                } catch (_: Exception) {
                    null
                }
            }
            .allowHardware(allowHardwareBitmaps)
            .respectCacheHeaders(false)
            .okHttpClient {
                val dispatcher = Dispatcher().apply {
                    maxRequests = if (isLegacyOrLowRamTv) 12 else 24
                    maxRequestsPerHost = if (isLegacyOrLowRamTv) 4 else 8
                }
                val builder = OkHttpClient.Builder()
                    .dispatcher(dispatcher)
                    .connectionPool(ConnectionPool(if (isLegacyOrLowRamTv) 6 else 10, 2, TimeUnit.MINUTES))
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(25, TimeUnit.SECONDS)

                if (allowInsecureSsl) {
                    applyInsecureSsl(builder)
                }

                builder.build()
            }
            .build()
    }

    @Synchronized
    fun install(context: Context, allowInsecureSsl: Boolean, force: Boolean = false) {
        if (!force && lastInstalledInsecureSsl == allowInsecureSsl) return
        try {
            Coil.setImageLoader(create(context, allowInsecureSsl))
            lastInstalledInsecureSsl = allowInsecureSsl
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    internal fun applyInsecureSsl(builder: OkHttpClient.Builder) {
        try {
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
            )
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
