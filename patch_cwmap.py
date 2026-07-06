import sys

file_path = "/app/applet/app/src/main/java/com/example/data/PiGalleryApi.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """            val metadata = if (metaMap != null) {
                parseMetadata(metaMap, cwMap)
            } else {"""

replacement = """            val metadata = if (metaMap != null) {
                parseMetadata(metaMap, mapObj)
            } else {"""

if target in content:
    content = content.replace(target, replacement)
    with open(file_path, "w") as f:
        f.write(content)
    print("Patched cwMap to mapObj")
else:
    print("Target not found")
