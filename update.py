import re

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'r') as f:
    content = f.read()

content = content.replace('MetadataRow(label = "Dateiname", value = media.name)', 'MetadataRow(icon = Icons.Outlined.InsertDriveFile, label = "Dateiname", value = media.name)')
content = content.replace('MetadataRow(label = "Ordner", value = it)', 'MetadataRow(icon = Icons.Outlined.Folder, label = "Ordner", value = it)')
content = content.replace('MetadataRow(label = "Abmessungen", value = "${size.width} × ${size.height} px")', 'MetadataRow(icon = Icons.Outlined.AspectRatio, label = "Abmessungen", value = "${size.width} × ${size.height} px")')
content = content.replace('MetadataRow(label = "Aufnahmedatum", value = dateStr)', 'MetadataRow(icon = Icons.Outlined.DateRange, label = "Aufnahmedatum", value = dateStr)')
content = content.replace('MetadataRow(label = "Typ", value = if (media.isVideo) "Video (MP4)" else "Image")', 'MetadataRow(icon = if (media.isVideo) Icons.Outlined.Videocam else Icons.Outlined.Image, label = "Typ", value = if (media.isVideo) "Video (MP4)" else "Image")')
content = content.replace('MetadataRow(label = "Kamera", value = cameraName)', 'MetadataRow(icon = Icons.Outlined.CameraAlt, label = "Kamera", value = cameraName)')
content = content.replace('MetadataRow(label = "Objektiv", value = lens)', 'MetadataRow(icon = Icons.Outlined.Lens, label = "Objektiv", value = lens)')
content = content.replace('MetadataRow(label = "Brennweite", value = focalLengthStr)', 'MetadataRow(icon = Icons.Outlined.Straighten, label = "Brennweite", value = focalLengthStr)')
content = content.replace('MetadataRow(label = "ISO", value = it.toString())', 'MetadataRow(icon = Icons.Outlined.Iso, label = "ISO", value = it.toString())')
content = content.replace('MetadataRow(label = "Blende", value = "f/${it}")', 'MetadataRow(icon = Icons.Outlined.Camera, label = "Blende", value = "f/${it}")')
content = content.replace('MetadataRow(label = "Belichtungszeit", value = exposureStr)', 'MetadataRow(icon = Icons.Outlined.Timer, label = "Belichtungszeit", value = exposureStr)')
content = content.replace('MetadataBlock(label = "Schlagwörter", value = keywords.joinToString(", "))', 'MetadataBlock(icon = Icons.Outlined.Label, label = "Schlagwörter", value = keywords.joinToString(", "))')
content = content.replace('MetadataBlock(label = "Personen", value = faceNames.joinToString(", "))', 'MetadataBlock(icon = Icons.Outlined.Person, label = "Personen", value = faceNames.joinToString(", "))')


def_row_old = """@Composable
fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(end = 16.dp)
        )
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Normal,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}"""

def_row_new = """@Composable
fun MetadataRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal
            )
        }
    }
}"""
content = content.replace(def_row_old, def_row_new)

def_block_old = """@Composable
fun MetadataBlock(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Normal
        )
    }
}"""

def_block_new = """@Composable
fun MetadataBlock(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal
            )
        }
    }
}"""
content = content.replace(def_block_old, def_block_new)

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'w') as f:
    f.write(content)

