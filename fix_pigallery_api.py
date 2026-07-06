import re

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'r') as f:
    content = f.read()

import_old = "import java.util.concurrent.TimeUnit\nimport android.util.Log"
import_new = "import java.util.concurrent.TimeUnit\nimport android.util.Log\nimport java.security.cert.X509Certificate\nimport javax.net.ssl.SSLContext\nimport javax.net.ssl.TrustManager\nimport javax.net.ssl.X509TrustManager\nimport javax.net.ssl.HostnameVerifier"

if import_old in content:
    content = content.replace(import_old, import_new)
    print("Replaced imports")
else:
    print("Could not find imports")


client_old = """    private val client = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http_cache"), 50L * 1024L * 1024L)) // 50MB
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()"""

client_new = """    private var client: OkHttpClient
    private val prefs = PreferencesManager(context)

    init {
        client = createClient(prefs.allowInsecureSsl)
    }

    fun updateClientSsl(allowInsecure: Boolean) {
        if (prefs.allowInsecureSsl != allowInsecure) {
            prefs.allowInsecureSsl = allowInsecure
        }
        client = createClient(allowInsecure)
    }

    private fun createClient(allowInsecure: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "http_cache"), 50L * 1024L * 1024L))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)

        if (allowInsecure) {
            try {
                val trustAllCerts = arrayOf<TrustManager>(
                    object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    }
                )
                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                val sslSocketFactory = sslContext.socketFactory

                builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                builder.hostnameVerifier { _, _ -> true }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return builder.build()
    }"""

if client_old in content:
    content = content.replace(client_old, client_new)
    print("Replaced client")
else:
    print("Could not find client")

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'w') as f:
    f.write(content)
