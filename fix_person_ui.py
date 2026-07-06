import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

person_item_old = """fun PersonItem(person: com.example.data.ApiPerson, viewModel: GalleryViewModel, onClick: () -> Unit) {
    val serverUrl = viewModel.prefs.serverUrl ?: ""
    val apiPrefix = viewModel.prefs.apiPrefix
    val thumbnailUrl = "${serverUrl}${apiPrefix}/person/${person.name}/thumbnail"
    val cookies = viewModel.prefs.cookies
    val cornerRadius by viewModel.cornerRadius.collectAsState()
    val aspectRatio by viewModel.aspectRatio.collectAsState()
    
    val imgRequest = ImageRequest.Builder(LocalContext.current)
        .data(thumbnailUrl)
        .apply {
            if (cookies.isNotEmpty()) {
                addHeader("Cookie", cookies)
            }
        }
        .crossfade(true)
        .build()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .let { modifier -> val r = aspectRatio; if (r > 0f) modifier.aspectRatio(r) else modifier.aspectRatio(1f) }
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(cornerRadius.dp))
                .clip(RoundedCornerShape(cornerRadius.dp)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imgRequest,
                contentDescription = person.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Item count overlay
            val count = person.cache?.count
            if (count != null && count > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = count.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        Text(
            text = person.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier
                .padding(vertical = 4.dp, horizontal = 4.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}"""

person_item_new = """fun PersonItem(person: com.example.data.ApiPerson, viewModel: GalleryViewModel, onClick: () -> Unit) {
    val serverUrl = viewModel.prefs.serverUrl ?: ""
    val apiPrefix = viewModel.prefs.apiPrefix
    val thumbnailUrl = "${serverUrl}${apiPrefix}/person/${person.name}/thumbnail"
    val cookies = viewModel.prefs.cookies
    val cornerRadius by viewModel.cornerRadius.collectAsState()
    val aspectRatio by viewModel.aspectRatio.collectAsState()
    val cardRatio = if (aspectRatio > 0f) aspectRatio else 1f
    
    val imgRequest = ImageRequest.Builder(LocalContext.current)
        .data(thumbnailUrl)
        .apply {
            if (cookies.isNotEmpty()) {
                addHeader("Cookie", cookies)
            }
        }
        .crossfade(true)
        .build()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(cardRatio)
            .clickable { onClick() },
        shape = RoundedCornerShape(cornerRadius.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = imgRequest,
                contentDescription = person.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            ThumbnailOverlay(
                name = person.name,
                count = "${person.cache?.count ?: 0}"
            )
        }
    }
}"""

content = content.replace(person_item_old, person_item_new)

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
