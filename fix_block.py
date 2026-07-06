import re

with open('app/src/main/java/com/example/ui/MediaViewerDialog.kt', 'r') as f:
    content = f.read()

def_block_old = """fun MetadataBlock(label: String, value: String) {
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

def_block_new = """fun MetadataBlock(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
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
