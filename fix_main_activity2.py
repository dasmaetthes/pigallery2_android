import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

bad = """                    builder.hostnameVerifier { _, _ -> 
                        if (prefs.allowInsecureSsl) true else javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(_, _)
                    }"""

good = """                    builder.hostnameVerifier { hostname, session -> 
                        if (prefs.allowInsecureSsl) true else javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
                    }"""

if bad in content:
    content = content.replace(bad, good)
    print("Replaced bad hostnameVerifier")
else:
    print("Could not find bad hostnameVerifier")

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
