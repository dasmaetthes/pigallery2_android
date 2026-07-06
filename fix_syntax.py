with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'r') as f:
    content = f.read()

# Replace the broken fragment
broken_fragment = """            }
            response
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()"""

content = content.replace(broken_fragment, "")

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'w') as f:
    f.write(content)
