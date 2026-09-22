/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : TestIntelProjectScreen.kt
 * Date : 2026/09/22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.testintel

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.IntelFeature
import org.hiylo.starburst.data.api.IntelFix
import org.hiylo.starburst.data.api.IntelIssue
import org.hiylo.starburst.data.api.IntelTestResult
import org.hiylo.starburst.data.api.IntelTestRun
import org.hiylo.starburst.data.api.parseDiffJson
import org.hiylo.starburst.ui.components.AppDialog
import org.hiylo.starburst.ui.components.AppPrimaryButton
import org.hiylo.starburst.ui.components.AppSecondaryButton
import org.hiylo.starburst.ui.components.isAmoledTheme

/**
 * 测试智能：项目详情页。
 *
 * 四个区块（功能点 / 单测 / 问题 / 修复建议）由项目 ViewModel 并发加载，互不影响；
 * 每个区块顶部有独立刷新。功能点 AI 对话在独立弹窗（[FeatureChatDialog]）中完成。
 *
 * @author Hsi Chu
 * @since 3.1.0
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestIntelProjectScreen(
    onNavigateBack: () -> Unit,
    viewModel: TestIntelProjectViewModel = hiltViewModel(),
) {
    val features by viewModel.features.collectAsState()
    val runs by viewModel.runs.collectAsState()
    val issues by viewModel.issues.collectAsState()
    val fixes by viewModel.fixes.collectAsState()
    val runResults by viewModel.runResults.collectAsState()
    val startingRun by viewModel.startingRun.collectAsState()
    val featureChat by viewModel.featureChat.collectAsState()
    val oneShot by viewModel.oneShot.collectAsState(null)
    val isAmoled = isAmoledTheme()
    val context = LocalContext.current

    var showFeatureChat by remember { mutableStateOf(false) }
    var linkTargetIssue by remember { mutableStateOf<IntelIssue?>(null) }
    var diffFix by remember { mutableStateOf<IntelFix?>(null) }
    var applyTargetFix by remember { mutableStateOf<IntelFix?>(null) }

    LaunchedEffect(oneShot) {
        oneShot?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.test_intel_project_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::loadAll) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.test_intel_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FeaturesSection(
                state = features,
                issues = issues.items,
                onRefresh = viewModel::refreshFeatures,
                onOpenFeature = { feature ->
                    viewModel.openFeatureChat(feature)
                    showFeatureChat = true
                },
            )

            TestsSection(
                state = runs,
                runResults = runResults,
                startingRun = startingRun,
                onStartRun = viewModel::startRun,
                onRefresh = viewModel::refreshRuns,
                onLoadResults = viewModel::loadRunResults,
            )

            IssuesSection(
                state = issues,
                onRefresh = viewModel::refreshIssues,
                onAck = viewModel::acknowledgeIssue,
                onLink = { linkTargetIssue = it },
            )

            FixesSection(
                state = fixes,
                onRefresh = viewModel::refreshFixes,
                onViewDiff = { diffFix = it },
                onApply = { applyTargetFix = it },
                onRollback = viewModel::rollbackFix,
                onReject = viewModel::rejectFix,
            )
        }
    }

    if (showFeatureChat) {
        FeatureChatDialog(
            chatState = featureChat,
            onQuestionChange = viewModel::setFeatureChatQuestion,
            onSend = viewModel::sendFeatureChat,
            onDismiss = {
                showFeatureChat = false
                viewModel.closeFeatureChat()
            },
        )
    }

    linkTargetIssue?.let { issue ->
        LinkFeatureDialog(
            features = features.items,
            onDismiss = { linkTargetIssue = null },
            onConfirm = { featureId ->
                viewModel.linkIssueToFeature(issue.id, featureId)
                linkTargetIssue = null
            },
        )
    }

    diffFix?.let { fix ->
        FixDiffDialog(
            fix = fix,
            onDismiss = { diffFix = null },
        )
    }

    applyTargetFix?.let { fix ->
        WriteModeDialog(
            fix = fix,
            onDismiss = { applyTargetFix = null },
            onConfirm = { mode ->
                viewModel.applyFix(fix.id, mode)
                applyTargetFix = null
            },
        )
    }
}

// ============ 功能点区块 ============

@Composable
private fun FeaturesSection(
    state: ProjectBlockState<IntelFeature>,
    issues: List<IntelIssue>,
    onRefresh: () -> Unit,
    onOpenFeature: (IntelFeature) -> Unit,
) {
    TestIntelSectionCard {
        BlockHeader(
            title = stringResource(R.string.test_intel_features),
            loading = state.loading,
            onRefresh = onRefresh,
        )
        state.error?.let { BlockError(it) }
        if (state.loading && state.items.isEmpty()) {
            BlockLoading()
        } else if (state.items.isEmpty()) {
            BlockEmpty(stringResource(R.string.test_intel_empty_features))
        } else {
            val highIssueByFeature = issues
                .filter { it.severity.equals("high", ignoreCase = true) && it.featureId != null }
                .groupingBy { it.featureId }
                .eachCount()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.items.forEach { feature ->
                    FeatureItem(
                        feature = feature,
                        securityIssueCount = highIssueByFeature[feature.id] ?: 0,
                        onClick = { onOpenFeature(feature) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureItem(
    feature: IntelFeature,
    securityIssueCount: Int,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (securityIssueCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "!",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onError,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Text(
                    text = feature.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(
                    text = feature.sourceLabel(),
                    color = MaterialTheme.colorScheme.tertiary,
                )
                StatusBadge(
                    text = stringResource(R.string.test_intel_feature_ends, feature.ends().size),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (feature.summary.isNotBlank()) {
                Text(
                    text = feature.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun IntelFeature.sourceLabel(): String = when (source.lowercase()) {
    "manual" -> stringResource(R.string.test_intel_feature_source_manual)
    else -> stringResource(R.string.test_intel_feature_source_auto)
}

// ============ 单测区块 ============

@Composable
private fun TestsSection(
    state: ProjectBlockState<IntelTestRun>,
    runResults: Map<Long, List<IntelTestResult>>,
    startingRun: Boolean,
    onStartRun: () -> Unit,
    onRefresh: () -> Unit,
    onLoadResults: (Long) -> Unit,
) {
    TestIntelSectionCard {
        BlockHeader(
            title = stringResource(R.string.test_intel_tests),
            loading = state.loading,
            onRefresh = onRefresh,
            trailing = {
                AppPrimaryButton(
                    onClick = onStartRun,
                    enabled = !startingRun,
                    modifier = Modifier,
                ) {
                    if (startingRun) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        stringResource(
                            if (startingRun) {
                                R.string.test_intel_starting_run
                            } else {
                                R.string.test_intel_start_run
                            },
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            },
        )
        state.error?.let { BlockError(it) }
        if (state.loading && state.items.isEmpty()) {
            BlockLoading()
        } else if (state.items.isEmpty()) {
            BlockEmpty(stringResource(R.string.test_intel_empty_tests))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.items.forEach { run ->
                    TestRunItem(
                        run = run,
                        results = runResults[run.id].orEmpty(),
                        resultsLoaded = runResults.containsKey(run.id),
                        onLoadResults = { onLoadResults(run.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TestRunItem(
    run: IntelTestRun,
    results: List<IntelTestResult>,
    resultsLoaded: Boolean,
    onLoadResults: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val statusColor = runStatusColor(run.status)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        expanded = !expanded
                        if (expanded && !resultsLoaded) onLoadResults()
                    },
            ) {
                Text(
                    text = formatTimestamp(run.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = run.scope.ifBlank { run.kind },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                run.progress?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge(text = runStatusLabel(run.status), color = statusColor)
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                if (!resultsLoaded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.test_intel_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (results.isEmpty()) {
                    Text(
                        text = stringResource(R.string.test_intel_run_no_results),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.test_intel_run_results),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        results.forEach { result ->
                            TestResultRow(result)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TestResultRow(result: IntelTestResult) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        StatusBadge(
            text = stringResource(
                if (result.passed) R.string.test_intel_result_passed else R.string.test_intel_result_failed,
            ),
            color = if (result.passed) runStatusColor("passed") else runStatusColor("failed"),
        )
        Text(
            text = result.endpoint,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!result.passed && result.failuresJson.isNullOrBlank().not()) {
            Text(
                text = result.failuresJson.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ============ 问题区块 ============

@Composable
private fun IssuesSection(
    state: ProjectBlockState<IntelIssue>,
    onRefresh: () -> Unit,
    onAck: (Long) -> Unit,
    onLink: (IntelIssue) -> Unit,
) {
    TestIntelSectionCard {
        BlockHeader(
            title = stringResource(R.string.test_intel_issues),
            loading = state.loading,
            onRefresh = onRefresh,
        )
        state.error?.let { BlockError(it) }
        if (state.loading && state.items.isEmpty()) {
            BlockLoading()
        } else if (state.items.isEmpty()) {
            BlockEmpty(stringResource(R.string.test_intel_empty_issues))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.items.forEach { issue ->
                    IssueItem(
                        issue = issue,
                        onAck = { onAck(issue.id) },
                        onLink = { onLink(issue) },
                    )
                }
            }
        }
    }
}

@Composable
private fun IssueItem(
    issue: IntelIssue,
    onAck: () -> Unit,
    onLink: () -> Unit,
) {
    val sevColor = severityColor(issue.severity)
    val statColor = issueStatusColor(issue.status)
    val isOpen = issue.status.lowercase() !in setOf("resolved", "fixed", "closed", "acknowledged", "ack")
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusBadge(text = severityLabel(issue.severity), color = sevColor)
                Text(
                    text = issue.key.ifBlank { issue.kind },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(text = issueStatusLabel(issue.status), color = statColor)
            }
            if (issue.location.isNotBlank()) {
                Text(
                    text = issue.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isOpen) {
                    AppSecondaryButton(onClick = onAck) {
                        Text(
                            stringResource(R.string.test_intel_issue_mark_ack),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                AppSecondaryButton(onClick = onLink, outlined = true) {
                    Text(
                        stringResource(R.string.test_intel_link_feature),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

// ============ 修复建议区块 ============

@Composable
private fun FixesSection(
    state: ProjectBlockState<IntelFix>,
    onRefresh: () -> Unit,
    onViewDiff: (IntelFix) -> Unit,
    onApply: (IntelFix) -> Unit,
    onRollback: (Long) -> Unit,
    onReject: (Long) -> Unit,
) {
    TestIntelSectionCard {
        BlockHeader(
            title = stringResource(R.string.test_intel_fixes),
            loading = state.loading,
            onRefresh = onRefresh,
        )
        state.error?.let { BlockError(it) }
        if (state.loading && state.items.isEmpty()) {
            BlockLoading()
        } else if (state.items.isEmpty()) {
            BlockEmpty(stringResource(R.string.test_intel_empty_fixes))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.items.forEach { fix ->
                    FixItem(
                        fix = fix,
                        onViewDiff = { onViewDiff(fix) },
                        onApply = { onApply(fix) },
                        onRollback = { onRollback(fix.id) },
                        onReject = { onReject(fix.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FixItem(
    fix: IntelFix,
    onViewDiff: () -> Unit,
    onApply: () -> Unit,
    onRollback: () -> Unit,
    onReject: () -> Unit,
) {
    val statusColor = fixStatusColor(fix.status)
    val isProposed = fix.status.equals("proposed", ignoreCase = true)
    val isApplied = fix.status.equals("applied", ignoreCase = true)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = fix.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(text = fixStatusLabel(fix.status), color = statusColor)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = fix.writeMode ?: "-",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatTimestamp(fix.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppSecondaryButton(onClick = onViewDiff, outlined = true) {
                    Text(
                        stringResource(R.string.test_intel_fix_view_diff),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (isProposed) {
                    AppPrimaryButton(onClick = onApply) {
                        Text(
                            stringResource(R.string.test_intel_fix_apply),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    AppSecondaryButton(onClick = onReject, outlined = true) {
                        Text(
                            stringResource(R.string.test_intel_fix_reject),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                } else if (isApplied) {
                    AppSecondaryButton(onClick = onRollback, outlined = true) {
                        Text(
                            stringResource(R.string.test_intel_fix_rollback),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

// ============ 通用区块控件 ============

@Composable
private fun BlockHeader(
    title: String,
    loading: Boolean,
    onRefresh: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            trailing()
            IconButton(onClick = onRefresh, enabled = !loading, modifier = Modifier.size(32.dp)) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.test_intel_refresh),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockLoading() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(R.string.test_intel_loading),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BlockEmpty(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BlockError(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

// ============ 弹窗：挂功能点 / diff / 写入方式 ============

@Composable
private fun LinkFeatureDialog(
    features: List<IntelFeature>,
    onDismiss: () -> Unit,
    onConfirm: (featureId: Long) -> Unit,
) {
    AppDialog(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.test_intel_issue_link_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp),
        )
        if (features.isEmpty()) {
            Text(
                text = stringResource(R.string.test_intel_issue_link_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                AppSecondaryButton(onClick = onDismiss, outlined = true) {
                    Text(stringResource(R.string.cancel))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                features.forEach { feature ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfirm(feature.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = feature.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.test_intel_feature_ends, feature.ends().size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FixDiffDialog(
    fix: IntelFix,
    onDismiss: () -> Unit,
) {
    val suggestions = remember(fix.diffJson) { parseDiffJson(fix.diffJson) }
    AppDialog(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.test_intel_fix_diff_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp),
        )
        if (suggestions.isEmpty()) {
            Text(
                text = stringResource(R.string.test_intel_fix_diff_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                suggestions.forEach { suggestion ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "${suggestion.file}:${suggestion.line}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (suggestion.oldText.isNotBlank()) {
                            Text(
                                text = suggestion.oldText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (suggestion.newText.isNotBlank()) {
                            Text(
                                text = suggestion.newText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            AppSecondaryButton(onClick = onDismiss, outlined = true) {
                Text(stringResource(R.string.close))
            }
        }
    }
}

@Composable
private fun WriteModeDialog(
    fix: IntelFix,
    onDismiss: () -> Unit,
    onConfirm: (mode: String) -> Unit,
) {
    AppDialog(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.test_intel_fix_write_mode),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(
                "file" to stringResource(R.string.test_intel_fix_write_mode_file),
                "patch" to stringResource(R.string.test_intel_fix_write_mode_patch),
                "branch" to stringResource(R.string.test_intel_fix_write_mode_branch),
            ).forEach { (mode, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onConfirm(mode) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    if (fix.writeMode == mode) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            AppSecondaryButton(onClick = onDismiss, outlined = true) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}
