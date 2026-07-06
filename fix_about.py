import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

about_old = """                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val version = packageInfo.versionName"""

about_new = """                val version = try {
                    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    packageInfo.versionName
                } catch (e: Exception) {
                    "1.0"
                }"""

if about_old in content:
    content = content.replace(about_old, about_new)
    print("Replaced about successfully")
else:
    print("Could not find about_old")

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
