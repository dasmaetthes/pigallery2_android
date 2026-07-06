import re

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'r') as f:
    content = f.read()

bad_error_handling = """            val error = responseMap?.get("error") as? String
            if (error != null) {
                throw IOException(error)
            }"""

good_error_handling = """            val errorObj = responseMap?.get("error")
            if (errorObj != null) {
                throw IOException("Server returned error: $errorObj")
            }"""

content = content.replace(bad_error_handling, good_error_handling)

with open('app/src/main/java/com/example/data/PiGalleryApi.kt', 'w') as f:
    f.write(content)
