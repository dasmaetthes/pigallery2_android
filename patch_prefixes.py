with open('/app/applet/app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

old_code = """    val prefixes = listOf("directory:", "file-name:", "caption:", "person:", "keyword:", "position:", "rating:", "resolution:", "orientation:", "date:", "last-%d-days:")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (tokens.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tokens.forEach { token ->
                    InputChip(
                        selected = false,
                        onClick = { onRemoveToken(token) },
                        label = { Text(token) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                        },
                        colors = InputChipDefaults.inputChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = InputChipDefaults.inputChipBorder(
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            enabled = true,
                            selected = false
                        )
                    )
                }
            }
        }

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            prefixes.forEach { prefix ->
                Surface(
                    onClick = { onPrefixClick(prefix) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = prefix,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))"""

new_code = """    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (tokens.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tokens.forEach { token ->
                    InputChip(
                        selected = false,
                        onClick = { onRemoveToken(token) },
                        label = { Text(token) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                        },
                        colors = InputChipDefaults.inputChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = InputChipDefaults.inputChipBorder(
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            enabled = true,
                            selected = false
                        )
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        }"""

content = content.replace(old_code, new_code)

with open('/app/applet/app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
