import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

person_content = """

@Composable
fun PersonsTabContent(viewModel: GalleryViewModel) {
    val personsState by viewModel.personsState.collectAsState()
    val selectedPerson by viewModel.selectedPerson.collectAsState()
    val personContentState by viewModel.personContentState.collectAsState()
    val itemsPerRowPortrait by viewModel.itemsPerRowPortrait.collectAsState()
    val itemsPerRowLandscape by viewModel.itemsPerRowLandscape.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val itemsPerRow = if (isLandscape) itemsPerRowLandscape else itemsPerRowPortrait

    if (selectedPerson != null) {
        // Render current person's content
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = selectedPerson?.name ?: "",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            when (val state = personContentState) {
                is GalleryUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is GalleryUiState.Error -> {
                    ErrorStateView(message = state.message) {
                        selectedPerson?.let { viewModel.selectPerson(it) }
                    }
                }
                is GalleryUiState.Success -> {
                    val mediaList = state.directory.media ?: emptyList()
                    if (mediaList.isEmpty()) {
                        EmptyStateView("No items for this person", "This query yielded no matching media results.")
                    } else {
                        GalleryContentGrid(
                            subfolders = emptyList(),
                            mediaList = mediaList,
                            viewModel = viewModel,
                            onFolderClick = {},
                            onMediaClick = { media ->
                                viewModel.selectMedia(media, mediaList)
                            }
                        )
                    }
                }
                else -> {}
            }
        }
    } else {
        // Render persons list
        when (val state = personsState) {
            is PersonsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is PersonsUiState.Error -> {
                ErrorStateView(message = state.message) {
                    viewModel.loadPersons()
                }
            }
            is PersonsUiState.Success -> {
                if (state.persons.isEmpty()) {
                    EmptyStateView("No persons found", "No faces detected or server hasn't scanned persons yet.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(itemsPerRow),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(state.persons, key = { it.id }) { person ->
                            PersonItem(
                                person = person,
                                viewModel = viewModel,
                                onClick = { viewModel.selectPerson(person) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PersonItem(person: com.example.data.ApiPerson, viewModel: GalleryViewModel, onClick: () -> Unit) {
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
                .aspectRatio(aspectRatio)
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
}
"""

if "fun PersonsTabContent" not in content:
    content += person_content

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
