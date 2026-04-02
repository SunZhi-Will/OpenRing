package com.openring.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openring.R
import com.openring.data.PromptNoteRepository
import com.openring.data.model.PromptNoteEntity
import com.openring.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptNotesScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { PromptNoteRepository(context) }
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<PromptNoteEntity>>(emptyList()) }

    var editorOpen by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var draftKind by remember { mutableStateOf(PromptNoteRepository.KIND_PROMPT) }
    var draftTitle by remember { mutableStateOf("") }
    var draftDescription by remember { mutableStateOf("") }
    var draftBody by remember { mutableStateOf("") }

    var pendingDelete by remember { mutableStateOf<PromptNoteEntity?>(null) }

    fun reload() {
        scope.launch(Dispatchers.IO) {
            val next = repo.listAllOrdered()
            withContext(Dispatchers.Main) { items = next }
        }
    }

    LaunchedEffect(Unit) { reload() }

    fun openCreate() {
        editingId = null
        draftKind = PromptNoteRepository.KIND_PROMPT
        draftTitle = ""
        draftDescription = ""
        draftBody = ""
        editorOpen = true
    }

    fun openEdit(note: PromptNoteEntity) {
        editingId = note.id
        draftKind = note.kind
        draftTitle = note.title
        draftDescription = note.description
        draftBody = note.body
        editorOpen = true
    }

    fun saveDraft() {
        val title = draftTitle.trim()
        val body = draftBody.trim()
        if (title.isEmpty() || body.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            repo.upsert(
                id = editingId,
                kind = draftKind,
                title = title,
                description = draftDescription,
                body = body
            )
            withContext(Dispatchers.Main) {
                editorOpen = false
                reload()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.prompt_notes_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { openCreate() }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.prompt_notes_add))
            }
        }
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.prompt_notes_empty_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    stringResource(R.string.prompt_notes_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                item { Spacer(Modifier.height(Spacing.xs)) }
                items(items, key = { it.id }) { note ->
                    Card(
                        onClick = { openEdit(note) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                                ) {
                                    Text(
                                        note.title.ifBlank { "—" },
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Text(
                                        if (note.kind == PromptNoteRepository.KIND_SKILL) {
                                            stringResource(R.string.prompt_notes_kind_skill)
                                        } else {
                                            stringResource(R.string.prompt_notes_kind_prompt)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (note.description.isNotBlank()) {
                                    Text(
                                        note.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    note.body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { pendingDelete = note }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete)
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    if (editorOpen) {
        AlertDialog(
            onDismissRequest = { editorOpen = false },
            title = {
                Text(
                    if (editingId == null) {
                        stringResource(R.string.prompt_notes_dialog_create_title)
                    } else {
                        stringResource(R.string.prompt_notes_dialog_edit_title)
                    }
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FilterChip(
                            selected = draftKind == PromptNoteRepository.KIND_PROMPT,
                            onClick = { draftKind = PromptNoteRepository.KIND_PROMPT },
                            label = { Text(stringResource(R.string.prompt_notes_kind_prompt)) }
                        )
                        FilterChip(
                            selected = draftKind == PromptNoteRepository.KIND_SKILL,
                            onClick = { draftKind = PromptNoteRepository.KIND_SKILL },
                            label = { Text(stringResource(R.string.prompt_notes_kind_skill)) }
                        )
                    }
                    OutlinedTextField(
                        value = draftTitle,
                        onValueChange = { draftTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = {
                            Text(
                                if (draftKind == PromptNoteRepository.KIND_SKILL) {
                                    stringResource(R.string.prompt_notes_field_name_skill)
                                } else {
                                    stringResource(R.string.prompt_notes_field_title_prompt)
                                }
                            )
                        }
                    )
                    OutlinedTextField(
                        value = draftDescription,
                        onValueChange = { draftDescription = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        label = { Text(stringResource(R.string.prompt_notes_field_description)) }
                    )
                    OutlinedTextField(
                        value = draftBody,
                        onValueChange = { draftBody = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6,
                        label = {
                            Text(
                                if (draftKind == PromptNoteRepository.KIND_SKILL) {
                                    stringResource(R.string.prompt_notes_field_prompt_skill)
                                } else {
                                    stringResource(R.string.prompt_notes_field_body_prompt)
                                }
                            )
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { saveDraft() },
                    enabled = draftTitle.trim().isNotEmpty() && draftBody.trim().isNotEmpty()
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { editorOpen = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    pendingDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.prompt_notes_delete_title)) },
            text = {
                Text(
                    stringResource(R.string.prompt_notes_delete_confirm, note.title.ifBlank { note.id })
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = note.id
                        pendingDelete = null
                        scope.launch(Dispatchers.IO) {
                            repo.delete(id)
                            withContext(Dispatchers.Main) { reload() }
                        }
                    }
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
