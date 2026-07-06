import sys

file_path = "/app/applet/app/src/main/java/com/example/ui/MediaViewerDialog.kt"

with open(file_path, "r") as f:
    content = f.read()

target_to_remove = """    // Download video to a local cache file for offline-robust local playback
    LaunchedEffect(mediaUrl) {
        if (media.isVideo) {
            isDownloading = true
            downloadError = null
            downloadProgress = 0f
            withContext(Dispatchers.IO) {
                try {
                    val tempFile = File(context.cacheDir, "temp_video_${media.id ?: kotlin.random.Random.nextInt()}.mp4")
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }

                    val request = okhttp3.Request.Builder()
                        .url(mediaUrl)
                        .apply {
                            if (cookies.isNotEmpty()) {
                                addHeader("Cookie", cookies)
                            }
                        }
                        .build()

                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException("Server returned error code ${response.code}")
                        }
                        val body = response.body ?: throw IOException("Empty response body")
                        val totalBytes = body.contentLength()

                        body.byteStream().use { inputStream ->
                            tempFile.outputStream().use { outputStream ->
                                val buffer = ByteArray(64 * 1024)
                                var bytesRead: Int
                                var totalBytesRead = 0L
                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                    outputStream.write(buffer, 0, bytesRead)
                                    totalBytesRead += bytesRead
                                    if (totalBytes > 0) {
                                        downloadProgress = totalBytesRead.toFloat() / totalBytes.toFloat()
                                        if (downloadProgress >= 0.05f && localVideoUri == null) {
                                            localVideoUri = Uri.fromFile(tempFile)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (localVideoUri == null) {
                        localVideoUri = Uri.fromFile(tempFile)
                    }
                } catch (e: Exception) {
                    downloadError = e.localizedMessage ?: "Failed to buffer video file"
                } finally {
                    isDownloading = false
                }
            }
        }
    }"""

if target_to_remove in content:
    content = content.replace(target_to_remove, "")
else:
    print("Warning: target_to_remove not found")

target_replace = """        if (media.isVideo) {
            if (downloadError != null) {
                Text(
                    text = "Error: ${downloadError}. Tap download to view locally.",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            } else if (localVideoUri != null) {
                var isPlaying by remember { mutableStateOf(false) }
                var hasError by remember { mutableStateOf(false) }

                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            val mediaController = MediaController(ctx)
                            mediaController.setAnchorView(this)
                            setMediaController(mediaController)

                            setVideoURI(localVideoUri)

                            setOnPreparedListener {
                                isPlaying = true
                                start()
                            }
                            setOnCompletionListener {
                                isPlaying = false
                            }
                            setOnErrorListener { _, _, _ ->
                                hasError = true
                                false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (hasError) {
                    Text(
                        text = "Playback failed. Tap download to view locally.",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                
                if (isDownloading && !isPlaying) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else if (isDownloading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    if (downloadProgress > 0f) {
                        CircularProgressIndicator(
                            progress = { downloadProgress },
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp
                        )
                    } else {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Buffering video... ${(downloadProgress * 100).toInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {"""

replacement = """        if (media.isVideo) {
            var isPlaying by remember { mutableStateOf(false) }
            var hasError by remember { mutableStateOf(false) }

            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val mediaController = MediaController(ctx)
                        mediaController.setAnchorView(this)
                        setMediaController(mediaController)

                        if (cookies.isNotEmpty()) {
                            setVideoURI(Uri.parse(mediaUrl), mapOf("Cookie" to cookies))
                        } else {
                            setVideoURI(Uri.parse(mediaUrl))
                        }

                        setOnPreparedListener {
                            isPlaying = true
                            start()
                        }
                        setOnCompletionListener {
                            isPlaying = false
                        }
                        setOnErrorListener { _, _, _ ->
                            hasError = true
                            false
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (hasError) {
                Text(
                    text = "Playback failed. Stream might be unsupported.",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            if (!isPlaying && !hasError) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Buffering video...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {"""

if target_replace in content:
    content = content.replace(target_replace, replacement)
    with open(file_path, "w") as f:
        f.write(content)
    print("Replaced successfully")
else:
    print("Target not found")
