package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: GalleryViewModel,
    modifier: Modifier = Modifier
) {
    val loginState by viewModel.loginState.collectAsState()
    val savedServerUrl by viewModel.savedServerUrl.collectAsState()
    val savedUsername by viewModel.savedUsername.collectAsState()
    val savedPassword by viewModel.savedPassword.collectAsState()

    var serverUrl by remember { mutableStateOf(savedServerUrl) }
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
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Beautiful App Icon Logo
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(20.dp),
                color = androidx.compose.ui.graphics.Color.Black,
                tonalElevation = 2.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "App Logo",
                        modifier = Modifier.size(80.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Title & Subtitle
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "PiGallery2",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Connect to your self-hosted directory-first photo gallery",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Form Content
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Server Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Server URL Input
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("Server URL") },
                        placeholder = { Text("http://192.168.1.100:3000") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Server URL"
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("server_url_input"),
                        colors = OutlinedTextFieldDefaults.colors()
                    )

                    // Username Input
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username (Optional)") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Username"
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("username_input")
                    )

                    // Password Input
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password (Optional)") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Password"
                            )
                        },
                        trailingIcon = {
                            val image = if (passwordVisible)
                                Icons.Default.Visibility
                            else Icons.Default.VisibilityOff

                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = "Toggle password")
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Insecure SSL Checkbox
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
                        },
                        enabled = serverUrl.isNotEmpty() && loginState !is LoginUiState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("connect_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (loginState is LoginUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                "Connect & Login",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Use Demo Server Prefill
                    OutlinedButton(
                        onClick = {
                            serverUrl = "https://pigallery2.onrender.com"
                            username = ""
                            password = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Fill with Public Demo Server")
                    }
                }
            }

            // Connection Status Message
            AnimatedVisibility(visible = loginState is LoginUiState.Error) {
                if (loginState is LoginUiState.Error) {
                    val errorMsg = (loginState as LoginUiState.Error).message
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, start = 24.dp, end = 24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = errorMsg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            AnimatedVisibility(visible = loginState is LoginUiState.Success) {
                if (loginState is LoginUiState.Success) {
                    val successMsg = (loginState as LoginUiState.Success).message
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, start = 24.dp, end = 24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = successMsg,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(16.dp),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
