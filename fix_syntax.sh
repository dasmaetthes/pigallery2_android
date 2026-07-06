sed -i '2289,2302c\
                Text(\
                    text = "Built with open source software:\\n" +\
                           "• Android Jetpack (Apache 2.0)\\n" +\
                           "• Kotlin & Coroutines (Apache 2.0)\\n" +\
                           "• Coil Image Loader (Apache 2.0)\\n" +\
                           "• OkHttp & Retrofit & Moshi (Apache 2.0)\\n\\n" +\
                           "These libraries are licensed under the Apache License, Version 2.0. " +\
                           "You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0",\
' app/src/main/java/com/example/ui/GalleryScreen.kt
