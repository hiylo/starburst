/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : GlobalSearchScreen.kt
 * Date : 2026/09/09 17:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.sessions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.hiylo.starburst.R
import org.hiylo.starburst.domain.model.ServerConfig
import org.hiylo.starburst.ui.components.SessionCardContent
import org.hiylo.starburst.ui.components.AppCardShape
import org.hiylo.starburst.ui.components.AppSearchShape
import org.hiylo.starburst.ui.components.isAmoledTheme
import org.hiylo.starburst.ui.components.sessionCategoryColor
import org.hiylo.starburst.ui.components.sessionCategoryIcon
import androidx.compose.material3.Card

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    onNavigateBack: () -> Unit,
    onOpenSession: (GlobalSearchItem) -> Unit,
    viewModel: GlobalSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val isAmoled = isAmoledTheme()
    var query by rememberSaveable { mutableStateOf("") }
    var timeFilter by rememberSaveable { mutableStateOf(GlobalSearchTimeFilter.All) }

    val visibleItems = remember(state.items, query, timeFilter) {
        filterGlobalSearchItems(state.items, query, timeFilter)
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(AppSearchShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    if (query.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.global_search_hint),
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                    innerTextField()
                                }
                                if (query.isNotEmpty()) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.close),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(2.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlobalSearchTimeFilter.entries.forEach { filter ->
                    val selected = timeFilter == filter
                    AssistChip(
                        onClick = { timeFilter = filter },
                        label = {
                            Text(
                                stringResource(
                                    when (filter) {
                                        GlobalSearchTimeFilter.All -> R.string.global_search_time_all
                                        GlobalSearchTimeFilter.Today -> R.string.global_search_time_today
                                        GlobalSearchTimeFilter.LastWeek -> R.string.global_search_time_week
                                        GlobalSearchTimeFilter.LastMonth -> R.string.global_search_time_month
                                    },
                                ),
                            )
                        },
                        colors = if (selected) {
                            if (isAmoled) {
                                AssistChipDefaults.assistChipColors(
                                    containerColor = Color.Black,
                                    labelColor = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        } else AssistChipDefaults.assistChipColors(),
                        border = if (selected && isAmoled) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            AssistChipDefaults.assistChipBorder(enabled = true)
                        },
                    )
                }
            }

            Text(
                text = stringResource(
                    R.string.global_search_result_count,
                    visibleItems.size,
                    state.connectedServerCount,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            if (visibleItems.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(40.dp))
                    Text(
                        text = stringResource(R.string.global_search_empty),
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        visibleItems,
                        key = { "${it.server.id}:${it.session.id}" },
                    ) { item ->
                        GlobalSearchResultCard(
                            item = item,
                            onClick = {
                                if (item.isConnected) onOpenSession(item)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalSearchResultCard(
    item: GlobalSearchItem,
    onClick: () -> Unit,
) {
    val isAmoled = isAmoledTheme()
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = if (isAmoled) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
        } else null,
        shape = AppCardShape,
    ) {
        SessionCardContent(
            session = item.session,
            status = item.status,
            isFavorite = false,
            category = item.category,
            contextLabel = item.server.displayName,
            contextDetail = item.projectName.takeIf { it.isNotBlank() }?.let { project ->
                val serverLeaf = item.server.displayName.trimEnd('/').substringAfterLast('/')
                if (project.equals(serverLeaf, ignoreCase = true)) null else project
            },
            isOffline = !item.isConnected,
            trailingContent = {
                item.category?.let { category ->
                    Icon(
                        imageVector = sessionCategoryIcon(category.icon),
                        contentDescription = null,
                        tint = sessionCategoryColor(category.color),
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            },
        )
    }
}