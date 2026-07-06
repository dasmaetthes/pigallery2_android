import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

about_bad = """                Text(
                    text = "Built with open source software:" +
                           "• Android Jetpack (Apache 2.0)" +
                           "• Kotlin & Coroutines (Apache 2.0)" +
                           "• Coil Image Loader (Apache 2.0)" +
                           "• OkHttp & Retrofit & Moshi (Apache 2.0)" +
                           "These libraries are licensed under the Apache License, Version 2.0. " +
                           "You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0",
                    style = MaterialTheme.typography.bodySmall,"""

about_good = '                Text(\n                    text = """Built with open source software:\\n' + \
             '• Android Jetpack (Apache 2.0)\\n' + \
             '• Kotlin & Coroutines (Apache 2.0)\\n' + \
             '• Coil Image Loader (Apache 2.0)\\n' + \
             '• OkHttp & Retrofit & Moshi (Apache 2.0)\\n\\n' + \
             'These libraries are licensed under the Apache License, Version 2.0. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0""".trimIndent(),\n' + \
             '                    style = MaterialTheme.typography.bodySmall,'

if about_bad in content:
    content = content.replace(about_bad, about_good)
    print("Replaced bad about syntax")
else:
    print("Could not find about_bad")

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
