import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

settings_old = """                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Connection Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Connected to: ${viewModel.prefs.serverUrl}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Logged in as: ${if (viewModel.prefs.username.isEmpty()) "Demo / Public User" else viewModel.prefs.username}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.logout() },"""

settings_new = """                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Connection Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Connected to: ${viewModel.prefs.serverUrl}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Logged in as: ${if (viewModel.prefs.username.isEmpty()) "Demo / Public User" else viewModel.prefs.username}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        var allowInsecureSsl by remember { mutableStateOf(viewModel.prefs.allowInsecureSsl) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { 
                                allowInsecureSsl = !allowInsecureSsl
                                viewModel.prefs.allowInsecureSsl = allowInsecureSsl
                            }
                        ) {
                            androidx.compose.material3.Switch(
                                checked = allowInsecureSsl,
                                onCheckedChange = { 
                                    allowInsecureSsl = it
                                    viewModel.prefs.allowInsecureSsl = it
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Allow insecure SSL (bad/no cert)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.logout() },"""

if settings_old in content:
    content = content.replace(settings_old, settings_new)
    print("Replaced settings successfully")
else:
    print("Could not find settings_old")

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
