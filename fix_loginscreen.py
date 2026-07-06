import re

with open('app/src/main/java/com/example/ui/LoginScreen.kt', 'r') as f:
    content = f.read()

vars_old = """    var serverUrl by remember { mutableStateOf(savedServerUrl) }
    var username by remember { mutableStateOf(savedUsername) }
    var password by remember { mutableStateOf(savedPassword) }
    var passwordVisible by remember { mutableStateOf(false) }

    // Update form when saved settings are loaded
    LaunchedEffect(savedServerUrl, savedUsername, savedPassword) {
        if (serverUrl.isEmpty()) serverUrl = savedServerUrl
        if (username.isEmpty()) username = savedUsername
        if (password.isEmpty()) password = savedPassword
    }"""

vars_new = """    var serverUrl by remember { mutableStateOf(savedServerUrl) }
    var username by remember { mutableStateOf(savedUsername) }
    var password by remember { mutableStateOf(savedPassword) }
    var passwordVisible by remember { mutableStateOf(false) }
    var allowInsecureSsl by remember { mutableStateOf(viewModel.prefs.allowInsecureSsl) }

    // Update form when saved settings are loaded
    LaunchedEffect(savedServerUrl, savedUsername, savedPassword) {
        if (serverUrl.isEmpty()) serverUrl = savedServerUrl
        if (username.isEmpty()) username = savedUsername
        if (password.isEmpty()) password = savedPassword
        allowInsecureSsl = viewModel.prefs.allowInsecureSsl
    }"""

if vars_old in content:
    content = content.replace(vars_old, vars_new)
    print("Replaced vars successfully")
else:
    print("Could not find vars_old")


connect_old = """                    // Connect Button
                    Button(
                        onClick = {
                            if (serverUrl.isNotEmpty()) {
                                viewModel.connectAndLogin(serverUrl, username, password)
                            }
                        },"""

connect_new = """                    // Insecure SSL Checkbox
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { allowInsecureSsl = !allowInsecureSsl },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = allowInsecureSsl,
                            onCheckedChange = { allowInsecureSsl = it }
                        )
                        Text("Allow insecure SSL (bad/no cert)", style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Connect Button
                    Button(
                        onClick = {
                            if (serverUrl.isNotEmpty()) {
                                viewModel.connectAndLogin(serverUrl, username, password, allowInsecureSsl)
                            }
                        },"""

if connect_old in content:
    content = content.replace(connect_old, connect_new)
    print("Replaced connect successfully")
else:
    print("Could not find connect_old")

with open('app/src/main/java/com/example/ui/LoginScreen.kt', 'w') as f:
    f.write(content)
