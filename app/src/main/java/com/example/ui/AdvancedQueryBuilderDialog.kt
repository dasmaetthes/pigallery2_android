package com.example.ui

import android.app.DatePickerDialog
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class VisualQueryRow(
    val openParen: String = "",       // "", "(", "((", etc.
    val attribute: String = "person", // "person", "keyword", "place", "caption", "file-name", "directory", "date", "rating", "person-count"
    val value: String = "",
    val closeParen: String = "",      // "", ")", "))", etc.
    val connector: String = "AND"      // "AND", "OR"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedQueryBuilderDialog(
    viewModel: GalleryViewModel,
    onDismissRequest: () -> Unit,
    onSearchApplied: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Initialize with one empty row, or try parsing the existing searchQuery back into rows
    val initialQuery = viewModel.searchQuery.value
    var rows by remember {
        mutableStateOf(
            if (initialQuery.isNotEmpty()) {
                // If there's an existing query, let's try to parse it into rows
                tryParseQueryToRows(initialQuery)
            } else {
                listOf(VisualQueryRow())
            }
        )
    }

    val compiledQuery = remember(rows) {
        compileVisualQuery(rows)
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Advanced Search",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismissRequest) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable filters list
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        rows.forEachIndexed { index, row ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    // Row control: Delete button at top-right
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Filter #${index + 1}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (rows.size > 1) {
                                            IconButton(
                                                onClick = {
                                                    rows = rows.toMutableList().apply { removeAt(index) }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Remove Filter",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Main controls flow or row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Open parenthesis selector
                                        Box(modifier = Modifier.width(85.dp)) {
                                            var expanded by remember { mutableStateOf(false) }
                                            OutlinedTextField(
                                                value = row.openParen.ifEmpty { "-" },
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text("(") },
                                                textStyle = MaterialTheme.typography.bodyMedium,
                                                trailingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowDropDown,
                                                        contentDescription = null,
                                                        modifier = Modifier.clickable { expanded = true }
                                                    )
                                                },
                                                modifier = Modifier.clickable { expanded = true }
                                            )
                                            DropdownMenu(
                                                expanded = expanded,
                                                onDismissRequest = { expanded = false }
                                            ) {
                                                listOf("", "(", "((", "(((").forEach { valName ->
                                                    DropdownMenuItem(
                                                        text = { Text(valName.ifEmpty { "-" }) },
                                                        onClick = {
                                                            rows = rows.toMutableList().apply {
                                                                this[index] = this[index].copy(openParen = valName)
                                                            }
                                                            expanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        // Attribute selector
                                        Box(modifier = Modifier.weight(1f)) {
                                            var expanded by remember { mutableStateOf(false) }
                                            val attributeLabels = mapOf(
                                                "person" to "Person",
                                                "keyword" to "Keyword / Tag",
                                                "place" to "Place / Location",
                                                "caption" to "Caption",
                                                "file-name" to "File Name",
                                                "directory" to "Directory",
                                                "date" to "Date",
                                                "rating" to "Rating",
                                                "person-count" to "People Count"
                                            )
                                            OutlinedTextField(
                                                value = attributeLabels[row.attribute] ?: row.attribute,
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text("Attribute") },
                                                textStyle = MaterialTheme.typography.bodyMedium,
                                                trailingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowDropDown,
                                                        contentDescription = null,
                                                        modifier = Modifier.clickable { expanded = true }
                                                    )
                                                },
                                                modifier = Modifier.clickable { expanded = true }
                                            )
                                            DropdownMenu(
                                                expanded = expanded,
                                                onDismissRequest = { expanded = false }
                                            ) {
                                                attributeLabels.forEach { (key, label) ->
                                                    DropdownMenuItem(
                                                        text = { Text(label) },
                                                        onClick = {
                                                            rows = rows.toMutableList().apply {
                                                                this[index] = this[index].copy(attribute = key, value = "")
                                                            }
                                                            expanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        // Close parenthesis selector
                                        Box(modifier = Modifier.width(85.dp)) {
                                            var expanded by remember { mutableStateOf(false) }
                                            OutlinedTextField(
                                                value = row.closeParen.ifEmpty { "-" },
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text(")") },
                                                textStyle = MaterialTheme.typography.bodyMedium,
                                                trailingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowDropDown,
                                                        contentDescription = null,
                                                        modifier = Modifier.clickable { expanded = true }
                                                    )
                                                },
                                                modifier = Modifier.clickable { expanded = true }
                                            )
                                            DropdownMenu(
                                                expanded = expanded,
                                                onDismissRequest = { expanded = false }
                                            ) {
                                                listOf("", ")", "))", ")))").forEach { valName ->
                                                    DropdownMenuItem(
                                                        text = { Text(valName.ifEmpty { "-" }) },
                                                        onClick = {
                                                            rows = rows.toMutableList().apply {
                                                                this[index] = this[index].copy(closeParen = valName)
                                                            }
                                                            expanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Value Input field with Autocomplete/Suggestions & helpers
                                    FilterValueInputSection(
                                        viewModel = viewModel,
                                        row = row,
                                        onValueChange = { newValue ->
                                            rows = rows.toMutableList().apply {
                                                this[index] = this[index].copy(value = newValue)
                                            }
                                        }
                                    )

                                    // Linking Connector choice at the bottom of the card (only shown if not the last card)
                                    if (index < rows.size - 1) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Connector to next filter:",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            FilterChip(
                                                selected = row.connector == "AND",
                                                onClick = {
                                                    rows = rows.toMutableList().apply {
                                                        this[index] = this[index].copy(connector = "AND")
                                                    }
                                                },
                                                label = { Text("AND") }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            FilterChip(
                                                selected = row.connector == "OR",
                                                onClick = {
                                                    rows = rows.toMutableList().apply {
                                                        this[index] = this[index].copy(connector = "OR")
                                                    }
                                                },
                                                label = { Text("OR") }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Add Filter button
                        Button(
                            onClick = {
                                rows = rows + VisualQueryRow(connector = "AND")
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Filter Attribute")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom section: live preview and search actions
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Query Preview:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = compiledQuery.ifEmpty { "(empty query)" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (compiledQuery.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            rows = listOf(VisualQueryRow())
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear All")
                    }

                    Button(
                        onClick = {
                            onSearchApplied(compiledQuery)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Search")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FilterValueInputSection(
    viewModel: GalleryViewModel,
    row: VisualQueryRow,
    onValueChange: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var textInput by remember(row.value) { mutableStateOf(row.value) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isFetching by remember { mutableStateOf(false) }
    var suggestionsExpanded by remember { mutableStateOf(false) }

    // On text change, fetch suggestions if applicable
    LaunchedEffect(textInput) {
        if (textInput.isBlank() || row.attribute == "date" || row.attribute == "rating" || row.attribute == "person-count") {
            suggestions = emptyList()
            suggestionsExpanded = false
            return@LaunchedEffect
        }

        isFetching = true
        val searchType = when (row.attribute) {
            "keyword", "tag" -> 104
            "person" -> 105
            "position", "place" -> 106
            "caption" -> 101
            "file-name", "filename" -> 103
            "directory", "folder" -> 102
            else -> 100
        }

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val result = viewModel.api.getAutocompleteSuggestions(
                    serverUrl = viewModel.prefs.serverUrl,
                    text = textInput,
                    cookies = viewModel.prefs.cookies,
                    apiPrefix = viewModel.prefs.apiPrefix,
                    type = searchType
                )

                val prefixToStrip = when (row.attribute) {
                    "keyword", "tag" -> "keyword:"
                    "person" -> "person:"
                    "position", "place" -> "position:"
                    "caption" -> "caption:"
                    "file-name", "filename" -> "file-name:"
                    "directory", "folder" -> "directory:"
                    else -> ""
                }

                val cleaned = result.map {
                    if (it.startsWith(prefixToStrip, ignoreCase = true)) {
                        it.substring(prefixToStrip.length)
                    } else {
                        it
                    }
                }
                suggestions = cleaned
                suggestionsExpanded = cleaned.isNotEmpty()
            } catch (e: Exception) {
                suggestions = emptyList()
                suggestionsExpanded = false
            } finally {
                isFetching = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = textInput,
                onValueChange = {
                    textInput = it
                    onValueChange(it)
                },
                label = { Text("Filter Value") },
                placeholder = {
                    val placeholderText = when (row.attribute) {
                        "date" -> "e.g., 2023, 2023-05-10, 2023-01..2023-12"
                        "rating" -> "e.g., 4, >=4, 1..3"
                        "person-count" -> "e.g., 1, >2, 0"
                        else -> "Enter value..."
                    }
                    Text(placeholderText)
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (row.attribute == "date") {
                            IconButton(onClick = {
                                val calendar = java.util.Calendar.getInstance()
                                val datePickerDialog = DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val monthStr = (month + 1).toString().padStart(2, '0')
                                        val dayStr = dayOfMonth.toString().padStart(2, '0')
                                        val selectedDate = "$year-$monthStr-$dayStr"
                                        textInput = selectedDate
                                        onValueChange(selectedDate)
                                    },
                                    calendar.get(java.util.Calendar.YEAR),
                                    calendar.get(java.util.Calendar.MONTH),
                                    calendar.get(java.util.Calendar.DAY_OF_MONTH)
                                )
                                datePickerDialog.show()
                            }) {
                                Icon(imageVector = Icons.Default.DateRange, contentDescription = "Pick Date")
                            }
                        }
                        if (textInput.isNotEmpty()) {
                            IconButton(onClick = {
                                textInput = ""
                                onValueChange("")
                            }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Autocomplete suggestions dropdown (for text attributes)
            if (suggestionsExpanded && suggestions.isNotEmpty()) {
                DropdownMenu(
                    expanded = suggestionsExpanded,
                    onDismissRequest = { suggestionsExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    suggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = { Text(suggestion) },
                            onClick = {
                                textInput = suggestion
                                onValueChange(suggestion)
                                suggestionsExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Quick suggestions for specialized attributes
        if (row.attribute == "date" || row.attribute == "rating" || row.attribute == "person-count") {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val helpers = when (row.attribute) {
                    "date" -> listOf(
                        "Today" to java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()),
                        "This Year" to java.text.SimpleDateFormat("yyyy", java.util.Locale.US).format(java.util.Date()),
                        "Last Year" to (java.text.SimpleDateFormat("yyyy", java.util.Locale.US).format(java.util.Date()).toInt() - 1).toString()
                    )
                    "rating" -> listOf(
                        "High (>=4)" to ">=4",
                        "5 Stars" to "5",
                        "Unrated" to "0"
                    )
                    "person-count" -> listOf(
                        "No people (0)" to "0",
                        "Has people (>0)" to ">0",
                        "Group (>=2)" to ">=2"
                    )
                    else -> emptyList()
                }

                helpers.forEach { (label, value) ->
                    SuggestionChip(
                        onClick = {
                            textInput = value
                            onValueChange(value)
                        },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}

fun compileVisualQuery(rows: List<VisualQueryRow>): String {
    val sb = StringBuilder()
    var pendingConnector: String? = null
    rows.forEach { row ->
        val cleanValue = row.value.trim()
        if (cleanValue.isNotEmpty()) {
            if (sb.isNotEmpty() && pendingConnector != null) {
                sb.append(" ").append(pendingConnector).append(" ")
            }
            if (row.openParen.isNotEmpty()) {
                sb.append(row.openParen).append(" ")
            }

            val key = when (row.attribute) {
                "tag" -> "keyword"
                "place" -> "position"
                "folder" -> "directory"
                "filename" -> "file-name"
                else -> row.attribute
            }

            val hasOperator = cleanValue.startsWith(">") || cleanValue.startsWith("<") || cleanValue.startsWith("=") || cleanValue.startsWith(":") || cleanValue.startsWith("<=") || cleanValue.startsWith(">=")
            if (hasOperator) {
                sb.append(key).append(cleanValue)
            } else {
                val hasSpaces = cleanValue.contains(" ")
                val isRange = cleanValue.contains("..")
                if (hasSpaces && !isRange) {
                    sb.append(key).append(":\"").append(cleanValue).append("\"")
                } else {
                    sb.append(key).append(":").append(cleanValue)
                }
            }

            if (row.closeParen.isNotEmpty()) {
                sb.append(" ").append(row.closeParen)
            }
            pendingConnector = row.connector
        }
    }
    return sb.toString().trim()
}

/**
 * Tokenizes a search query specifically for the visual query builder.
 * Splits by spaces, while preserving quotes and treating parentheses as separate tokens.
 */
fun tokenizeForQueryBuilder(query: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < query.length) {
        val c = query[i]
        if (c == '"') {
            inQuotes = !inQuotes
            current.append(c)
        } else if (!inQuotes && (c == '(' || c == ')')) {
            if (current.isNotEmpty()) {
                tokens.add(current.toString())
                current.setLength(0)
            }
            tokens.add(c.toString())
        } else if (!inQuotes && c.isWhitespace()) {
            if (current.isNotEmpty()) {
                tokens.add(current.toString())
                current.setLength(0)
            }
        } else {
            current.append(c)
        }
        i++
    }
    if (current.isNotEmpty()) {
        tokens.add(current.toString())
    }
    return tokens
}

/**
 * Attempts to parse an existing query string back into VisualQueryRow objects.
 * If too complex, falls back to a single row with the text.
 */
fun tryParseQueryToRows(query: String): List<VisualQueryRow> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return listOf(VisualQueryRow())

    val parsedTokens = tokenizeForQueryBuilder(trimmed)
    if (parsedTokens.isEmpty()) return listOf(VisualQueryRow())

    val rows = mutableListOf<VisualQueryRow>()
    var accumulatedOpenParen = ""
    var i = 0
    while (i < parsedTokens.size) {
        val token = parsedTokens[i]

        if (token == "(") {
            accumulatedOpenParen += "("
            i++
            continue
        }

        if (token == ")") {
            if (rows.isNotEmpty()) {
                val lastIdx = rows.size - 1
                rows[lastIdx] = rows[lastIdx].copy(closeParen = rows[lastIdx].closeParen + ")")
            }
            i++
            continue
        }

        if (token.equals("and", ignoreCase = true) || token.equals("or", ignoreCase = true)) {
            if (rows.isNotEmpty()) {
                val lastIdx = rows.size - 1
                rows[lastIdx] = rows[lastIdx].copy(connector = token.uppercase())
            }
            i++
            continue
        }

        // Split attribute and value
        val colonIndex = token.indexOf(':')
        var attr = "person"
        var value = token

        if (colonIndex > 0) {
            val key = token.substring(0, colonIndex).lowercase()
            val rawVal = token.substring(colonIndex + 1)

            attr = when (key) {
                "keyword", "tag" -> "keyword"
                "person" -> "person"
                "position", "place" -> "place"
                "caption" -> "caption"
                "file-name", "filename" -> "file-name"
                "directory", "folder" -> "directory"
                "date" -> "date"
                "rating" -> "rating"
                "person-count" -> "person-count"
                else -> "person"
            }
            value = rawVal
        } else {
            // Check for operators
            val ops = listOf(">=", "<=", ">", "<", "=")
            var foundOp = false
            for (op in ops) {
                val opIdx = token.indexOf(op)
                if (opIdx > 0) {
                    val key = token.substring(0, opIdx).lowercase()
                    attr = when (key) {
                        "date" -> "date"
                        "rating" -> "rating"
                        "person-count" -> "person-count"
                        else -> "person"
                    }
                    value = token.substring(opIdx)
                    foundOp = true
                    break
                }
            }
            if (!foundOp) {
                // If it doesn't match any attribute pattern, treat as person search
                attr = "person"
                value = token
            }
        }

        // Strip quotes
        if (value.startsWith("\"") && value.endsWith("\"") && value.length > 1) {
            value = value.substring(1, value.length - 1)
        }

        rows.add(
            VisualQueryRow(
                openParen = accumulatedOpenParen,
                attribute = attr,
                value = value,
                closeParen = "",
                connector = "AND"
            )
        )
        accumulatedOpenParen = ""
        i++
    }

    return if (rows.isEmpty()) listOf(VisualQueryRow()) else rows
}
