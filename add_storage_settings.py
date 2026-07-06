import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

storage_card = """
                // --- Storage Settings ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Speicher",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        val cacheSize by viewModel.cacheSize.collectAsState()
                        LaunchedEffect(selectedSettingsTab) {
                            if (selectedSettingsTab == 2) {
                                viewModel.updateCacheSize()
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Cache-Speicher",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Aktuell belegt: $cacheSize",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { viewModel.clearCaches() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Text("Leeren")
                            }
                        }
                    }
                }"""

# Find the end of Suffix Settings Card
suffix_end = """                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }"""

content = content.replace(suffix_end, suffix_end + "\n" + storage_card)

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
