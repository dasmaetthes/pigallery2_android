import sys

file_path = "/app/applet/app/src/main/java/com/example/ui/MediaViewerDialog.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """                            val dateStr = formatCreationDate(currentMedia.metadata?.creationDate)
                            MetadataRow(label = "Date Taken", value = dateStr)

                            MetadataRow(label = "Type", value = if (currentMedia.isVideo) "Video (MP4)" else "Image")
                        }"""

replacement = """                            val dateStr = formatCreationDate(currentMedia.metadata?.creationDate)
                            MetadataRow(label = "Date Taken", value = dateStr)

                            MetadataRow(label = "Type", value = if (currentMedia.isVideo) "Video (MP4)" else "Image")
                            
                            currentMedia.metadata?.cameraData?.let { camera ->
                                val cameraName = listOfNotNull(camera.make, camera.model).joinToString(" ")
                                if (cameraName.isNotBlank()) {
                                    MetadataRow(label = "Camera", value = cameraName)
                                }
                                camera.ISO?.let { MetadataRow(label = "ISO", value = it.toString()) }
                                camera.fStop?.let { MetadataRow(label = "Aperture", value = "f/${it}") }
                                camera.exposure?.let { 
                                    val exposureStr = if (it < 1.0 && it > 0.0) "1/${(1.0 / it).toInt()}s" else "${it}s"
                                    MetadataRow(label = "Exposure Time", value = exposureStr) 
                                }
                            }
                            
                            val keywords = currentMedia.metadata?.keywords
                            if (!keywords.isNullOrEmpty()) {
                                MetadataRow(label = "Keywords", value = keywords.joinToString(", "))
                            }
                            
                            val faces = currentMedia.metadata?.faces
                            if (!faces.isNullOrEmpty()) {
                                val faceNames = faces.mapNotNull { it.name }.filter { it.isNotBlank() }
                                if (faceNames.isNotEmpty()) {
                                    MetadataRow(label = "Persons", value = faceNames.joinToString(", "))
                                }
                            }
                        }"""

if target in content:
    content = content.replace(target, replacement)
    with open(file_path, "w") as f:
        f.write(content)
    print("Patched metadata")
else:
    print("Target not found")
