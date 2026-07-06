import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

import_old = "import com.example.ui.theme.MyApplicationTheme"
import_new = """import com.example.ui.theme.MyApplicationTheme
import com.example.data.PreferencesManager
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient"""

if import_old in content:
    content = content.replace(import_old, import_new)
    print("Replaced imports")
else:
    print("Could not find imports")


imageloader_old = """        val imageLoader = ImageLoader.Builder(this)
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
            .build()
        Coil.setImageLoader(imageLoader)"""

imageloader_new = """        val prefs = PreferencesManager(this)
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
                    builder.hostnameVerifier { _, _ -> 
                        if (prefs.allowInsecureSsl) true else javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(_, _)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                builder.build()
            }
            .build()
        Coil.setImageLoader(imageLoader)"""

if imageloader_old in content:
    content = content.replace(imageloader_old, imageloader_new)
    print("Replaced imageloader")
else:
    print("Could not find imageloader")

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
