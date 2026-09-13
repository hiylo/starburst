/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : AgentsMdScreen.kt
 * Date : 2026/09/13 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.opencode.ui.screens.files

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import org.hiylo.opencode.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsMdScreen(
    onNavigateBack: () -> Unit,
    viewModel: AgentsMdViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf(false) }

    LaunchedEffect(state.saveState.status) {
        if (state.saveState.status == FileSaveStatus.Saved) {
            editing = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("AGENTS.md") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::detect) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.skills_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(padding)
        ) {
            when {
                state.exists == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                editing -> {
                    EditorPane(
                        state = state,
                        onDraftChange = viewModel::updateDraft,
                        onSave = viewModel::saveDraft,
                        onCancel = { editing = false },
                        onGenerate = viewModel::generate,
                    )
                }
                state.exists == false && state.draft.isBlank() -> {
                    EmptyPane(
                        directory = state.directory,
                        onGenerate = {
                            editing = true
                            viewModel.generate()
                        },
                    )
                }
                else -> {
                    PreviewPane(
                        content = state.draft.ifBlank { state.existingContent },
                        isGenerating = state.isGenerating,
                        generateError = state.generateError,
                        onGenerate = {
                            editing = true
                            viewModel.generate()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPane(directory: String, onGenerate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.agents_md_empty),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = directory,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onGenerate) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.agents_md_generate))
        }
    }
}

@Composable
private fun PreviewPane(
    content: String,
    isGenerating: Boolean,
    generateError: String?,
    onGenerate: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            if (isGenerating) {
                CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onGenerate) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.width(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.agents_md_refine))
                }
            }
        }
        if (generateError != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = generateError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onGenerate) {
                    Text(stringResource(R.string.agents_md_continue), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (content.isBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Markdown(
                content = content,
                colors = markdownColor(text = MaterialTheme.colorScheme.onSurface),
                typography = markdownTypography(
                    h1 = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    h2 = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    h3 = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    h4 = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    h5 = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    h6 = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    text = MaterialTheme.typography.bodyMedium,
                    paragraph = MaterialTheme.typography.bodyMedium,
                    bullet = MaterialTheme.typography.bodyMedium,
                    ordered = MaterialTheme.typography.bodyMedium,
                    list = MaterialTheme.typography.bodyMedium,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun EditorPane(
    state: AgentsMdUiState,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onGenerate: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        when (state.saveState.status) {
            FileSaveStatus.Saving -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.agents_md_saving), style = MaterialTheme.typography.bodySmall)
                }
            }
            FileSaveStatus.Error -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.saveState.message ?: stringResource(R.string.agents_md_save_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onSave) {
                        Text(stringResource(R.string.agents_md_continue), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            else -> Unit
        }
        if (state.isGenerating) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.agents_md_generating), style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            if (state.generateError != null && state.draft.isBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.generateError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onGenerate) {
                        Text(stringResource(R.string.agents_md_continue), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            OutlinedTextField(
                value = state.draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSave, enabled = state.draft.isNotBlank()) {
                    Text(stringResource(R.string.agents_md_save))
                }
            }
        }
    }
}
