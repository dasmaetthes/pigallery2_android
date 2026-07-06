import re

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'r') as f:
    content = f.read()

client_old = """    private var client: OkHttpClient
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

client_new = """    private val prefs = PreferencesManager(context)
    private val client: OkHttpClient

    init {
        val builder = OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "http_cache"), 50L * 1024L * 1024L)) // 50MB
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            
        try {
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
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { hostname, session -> 
                if (prefs.allowInsecureSsl) true else javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        client = builder.build()
    }

    fun updateClientSsl(allowInsecure: Boolean) {
        // Now handled dynamically via prefs inside the TrustManager
    }"""

if client_old in content:
    content = content.replace(client_old, client_new)
    print("Replaced client successfully")
else:
    print("Could not find client_old")

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'w') as f:
    f.write(content)
