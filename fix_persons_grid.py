import re

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'r') as f:
    content = f.read()

persons_grid_old = """            is PersonsUiState.Success -> {
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
            }"""

persons_grid_new = """            is PersonsUiState.Success -> {
                if (state.persons.isEmpty()) {
                    EmptyStateView("No persons found", "No faces detected or server hasn't scanned persons yet.")
                } else {
                    val spacing by viewModel.spacing.collectAsState()
                    val spacingDp = spacing.dp
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(itemsPerRow),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(spacingDp),
                        verticalArrangement = Arrangement.spacedBy(spacingDp)
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
            }"""

content = content.replace(persons_grid_old, persons_grid_new)

with open('app/src/main/java/com/example/ui/GalleryScreen.kt', 'w') as f:
    f.write(content)
