import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

imports = """import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache"""

if 'coil.Coil' not in content:
    content = content.replace('import android.os.Bundle', 'import android.os.Bundle\n' + imports)

setup_coil = """        super.onCreate(savedInstanceState)
        
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05) // 5% of free disk space
                    .build()
            }
            .build()
        Coil.setImageLoader(imageLoader)"""

content = content.replace('        super.onCreate(savedInstanceState)', setup_coil)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
