with open('/app/applet/app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

old_code = """            // Floaty Multi-Select Action Bar at the bottom
            AnimatedVisibility(
                visible = isSelectMode,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "${selectedMediaForShare.size} items selected",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        VerticalDivider(
                            modifier = Modifier.height(24.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                        )
                        IconButton(onClick = { viewModel.exitSelectMode() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }"""

if old_code in content:
    content = content.replace(old_code, "")
    with open('/app/applet/app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
        f.write(content)
    print("Replaced successfully")
else:
    print("Old code not found")
