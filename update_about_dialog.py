import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

about_old = """                Text(
                    text = "Built with Jetpack Compose, Coil, OkHttp, and Moshi. All respective licenses apply.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )"""

about_new = """                Text(
                    text = "Built with open source software:\n" +
                           "• Android Jetpack (Apache 2.0)\n" +
                           "• Kotlin & Coroutines (Apache 2.0)\n" +
                           "• Coil Image Loader (Apache 2.0)\n" +
                           "• OkHttp & Retrofit & Moshi (Apache 2.0)\n\n" +
                           "These libraries are licensed under the Apache License, Version 2.0. " +
                           "You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )"""

if about_old in content:
    content = content.replace(about_old, about_new)
    print("Replaced about text successfully")
else:
    print("Could not find about_old")

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
