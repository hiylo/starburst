/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ServerProvidersScreen.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.server

import org.hiylo.starburst.logging.AppLogger as Log
import android.widget.Toast
import org.hiylo.starburst.BuildConfig
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.AppCardShape
import org.hiylo.starburst.ui.components.AppDialog
import org.hiylo.starburst.ui.components.AppPrimaryButton
import org.hiylo.starburst.ui.components.AppSecondaryButton
import org.hiylo.starburst.ui.components.appAmoledBorder
import org.hiylo.starburst.ui.components.isAmoledTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerProvidersScreen(
    onNavigateBack: () -> Unit,
    viewModel: ServerSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isAmoled = isAmoledTheme()
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val popularProviders = listOf("opencode", "anthropic", "github-copilot", "openai", "google", "openrouter", "vercel")
    val connected = uiState.providers.filter { it.connected && (it.providerId != "opencode" || it.hasPaidModels) }
    val connectedIds = connected.map { it.providerId }.toSet()
    val available = uiState.providers
        .filter { it.providerId !in connectedIds }
        .sortedWith(
            compareBy<ProviderToggle> { popularProviders.indexOf(it.providerId).takeIf { idx -> idx >= 0 } ?: Int.MAX_VALUE }
                .thenBy { it.providerName.lowercase() }
        )
    var connectProvider by remember { mutableStateOf<ProviderToggle?>(null) }
    var apiKeyProvider by remember { mutableStateOf<ProviderToggle?>(null) }
    var apiKey by remember { mutableStateOf("") }
    var oauthCode by remember { mutableStateOf("") }
    var oauthBrowserOpened by remember { mutableStateOf(false) }
    var showAddProviderDialog by remember { mutableStateOf(false) }
    var editingProviderId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearMessage()
    }

    // Close method picker only after OAuth flow actually starts.
    LaunchedEffect(uiState.pendingOauth?.providerId, connectProvider?.providerId) {
        val pendingForCurrent = uiState.pendingOauth?.providerId
        if (pendingForCurrent != null && pendingForCurrent == connectProvider?.providerId) {
            connectProvider = null
        }
    }

    LaunchedEffect(uiState.pendingOauth?.providerId) {
        oauthBrowserOpened = false
    }

    // Auto-close OAuth dialog when provider becomes connected (e.g. after browser auto callback)
    LaunchedEffect(connectedIds, uiState.pendingOauth?.providerId) {
        val pendingId = uiState.pendingOauth?.providerId
        if (pendingId != null && pendingId in connectedIds) {
            viewModel.cancelProviderOauth()
        }
    }

    // If headless method is unavailable and we fell back to browser OAuth,
    // open browser automatically to keep the flow one-tap.
    LaunchedEffect(uiState.pendingOauth?.providerId, uiState.pendingOauth?.fallbackFromHeadless, oauthBrowserOpened) {
        val pending = uiState.pendingOauth ?: return@LaunchedEffect
        if (pending.fallbackFromHeadless && !oauthBrowserOpened && pending.authorization.url.isNotBlank()) {
            oauthBrowserOpened = true
            uriHandler.openUri(pending.authorization.url)
        }
    }

    // When the user returns from the browser, always reload providers.
    // If auth already completed on the server, connectedIds effect closes the dialog.
    // If not, the dialog stays open and the user can continue manually.
    DisposableEffect(lifecycleOwner, uiState.pendingOauth?.providerId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val pending = uiState.pendingOauth
                if (BuildConfig.DEBUG) Log.d("ProvidersScreen", "ON_RESUME: browserOpened=$oauthBrowserOpened, pending=${pending?.providerId}, isSaving=${uiState.isSaving}")
                if (oauthBrowserOpened && pending != null && !uiState.isSaving) {
                    if (pending.authorization.method == "code") {
                        viewModel.loadProviders()
                    } else {
                        viewModel.completeProviderOauth(null)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    connectProvider?.let { provider ->
        val methods = uiState.authMethods[provider.providerId].orEmpty().ifEmpty {
            listOf(org.hiylo.starburst.data.api.ProviderAuthMethod(type = "api", label = stringResource(R.string.server_settings_auth_method_api)))
        }
        AppDialog(onDismissRequest = {
            connectProvider = null
            viewModel.clearError()
        }, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.server_settings_connect_provider, provider.providerName),
                        style = MaterialTheme.typography.titleLarge,
                    )

                    methods.forEachIndexed { idx, method ->
                        AppPrimaryButton(
                            onClick = {
                                if (method.type == "api") {
                                    connectProvider = null
                                    apiKeyProvider = provider
                                } else {
                                    viewModel.startProviderOauth(provider.providerId, idx)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isSaving,
                        ) {
                            Text(method.label)
                        }
                    }

                    if (uiState.oauthProxyHint) {
                        Text(
                            text = stringResource(R.string.server_settings_oauth_proxy_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        AppSecondaryButton(onClick = {
                            connectProvider = null
                            viewModel.clearError()
                        }) { Text(stringResource(R.string.cancel)) }
                    }
                }
        }
    }

    apiKeyProvider?.let { provider ->
        AppDialog(onDismissRequest = {
            apiKeyProvider = null
            apiKey = ""
        }, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.server_settings_api_key_title, provider.providerName), style = MaterialTheme.typography.headlineSmall)
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.server_settings_api_key_placeholder)) },
                        singleLine = true,
                        colors = if (isAmoled) {
                            androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Black,
                                unfocusedContainerColor = Color.Black,
                                disabledContainerColor = Color.Black,
                            )
                        } else androidx.compose.material3.OutlinedTextFieldDefaults.colors()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        AppSecondaryButton(onClick = {
                            apiKeyProvider = null
                            apiKey = ""
                        }) { Text(stringResource(R.string.cancel)) }
                        AppPrimaryButton(
                            onClick = {
                                viewModel.connectProviderApi(provider.providerId, apiKey)
                                apiKeyProvider = null
                                apiKey = ""
                            },
                            enabled = apiKey.isNotBlank() && !uiState.isSaving
                        ) { Text(stringResource(R.string.connect)) }
                    }
                }
        }
    }

    uiState.pendingOauth?.let { pending ->
        val deviceCode = remember(pending.authorization.instructions) {
            extractOAuthDeviceCode(pending.authorization.instructions)
        }
        AppDialog(onDismissRequest = {
            oauthCode = ""
            viewModel.cancelProviderOauth()
        }, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.server_settings_oauth_title, pending.providerName),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (deviceCode != null) {
                        // Show localized hint + prominent code chip
                        Text(
                            text = stringResource(R.string.server_settings_oauth_device_code_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isAmoled) {
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.3f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isAmoled) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    clipboard.setText(AnnotatedString(deviceCode))
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.server_settings_oauth_code_copied),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                .semantics {
                                    role = Role.Button
                                    contentDescription = context.getString(R.string.server_settings_oauth_copy_code)
                                },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = deviceCode,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 2.sp,
                                    ),
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                )
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else if (pending.authorization.method != "code" && pending.authorization.url.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.server_settings_oauth_browser_hint),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else if (pending.authorization.instructions.isNotBlank()) {
                        // No structured data extracted — show raw instructions as fallback
                        Text(
                            text = pending.authorization.instructions,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (pending.fallbackFromHeadless) {
                        Text(
                            text = stringResource(R.string.server_settings_oauth_headless_fallback),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (pending.authorization.url.isNotBlank()) {
                        AppPrimaryButton(
                            onClick = {
                                oauthBrowserOpened = true
                                uriHandler.openUri(pending.authorization.url)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.server_settings_oauth_open_browser))
                        }
                    }
                    if (pending.authorization.method == "code") {
                        OutlinedTextField(
                            value = oauthCode,
                            onValueChange = { oauthCode = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.server_settings_oauth_code_placeholder)) },
                            singleLine = true,
                            colors = if (isAmoled) {
                                androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.Black,
                                    unfocusedContainerColor = Color.Black,
                                    disabledContainerColor = Color.Black,
                                )
                            } else androidx.compose.material3.OutlinedTextFieldDefaults.colors()
                        )
                    }
                    if (!uiState.error.isNullOrBlank()) {
                        Text(
                            text = uiState.error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (uiState.oauthProxyHint) {
                        Text(
                            text = stringResource(R.string.server_settings_oauth_proxy_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        AppSecondaryButton(onClick = {
                            oauthCode = ""
                            viewModel.cancelProviderOauth()
                        }) { Text(stringResource(R.string.cancel)) }
                        if (pending.authorization.method == "code") {
                            AppPrimaryButton(
                                onClick = {
                                    viewModel.completeProviderOauth(oauthCode)
                                    oauthCode = ""
                                },
                                enabled = oauthCode.isNotBlank() && !uiState.isSaving
                            ) { Text(stringResource(R.string.server_settings_oauth_complete)) }
                        }
                    }
                }
        }
    }

    if (showAddProviderDialog) {
        AddProviderDialog(
            isAmoled = isAmoled,
            isSaving = uiState.isSaving,
            onDismiss = { showAddProviderDialog = false },
            onSave = { id, name, baseUrl, models ->
                viewModel.saveProvider(id, name, baseUrl, models)
                showAddProviderDialog = false
            },
        )
    }

    editingProviderId?.let { id ->
        val entry = uiState.customProviders.find { it.providerId == id }
        EditProviderDialog(
            entry = entry ?: ProviderConfigEntry(providerId = id, name = id),
            isAmoled = isAmoled,
            isSaving = uiState.isSaving,
            onDismiss = { editingProviderId = null },
            onSave = { providerId, name, baseUrl, models ->
                viewModel.saveProvider(providerId, name, baseUrl, models)
                editingProviderId = null
            },
            onDelete = { providerId ->
                viewModel.deleteProvider(providerId)
                editingProviderId = null
            },
        )
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.server_settings_providers)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        val editableProviderIds = uiState.customProviders.map { it.providerId }.toSet()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val providerDialogVisible = connectProvider != null || apiKeyProvider != null || uiState.pendingOauth != null || showAddProviderDialog || editingProviderId != null
            if (!providerDialogVisible && !uiState.error.isNullOrBlank()) {
                item {
                    Text(
                        text = uiState.error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    AppPrimaryButton(
                        onClick = {
                            viewModel.clearError()
                            showAddProviderDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.server_settings_add_provider))
                    }
                }
            }

            if (connected.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.server_settings_providers_connected),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(connected, key = { it.providerId }) { provider ->
                    ProviderRow(
                        provider = provider,
                        onConnect = { viewModel.clearError(); connectProvider = provider },
                        onDisconnect = { viewModel.disconnectProvider(provider.providerId) },
                        showConnect = false,
                        canDisconnect = provider.source != "env",
                        isSaving = uiState.isSaving,
                        isAmoled = isAmoled,
                        showSource = true,
                        canEdit = provider.source == "custom" || provider.providerId in editableProviderIds,
                        onEdit = { viewModel.clearError(); editingProviderId = provider.providerId },
                    )
                }
            }

            if (available.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.padding(top = 4.dp))
                    Text(
                        text = stringResource(R.string.server_settings_providers_available),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(available, key = { it.providerId }) { provider ->
                    ProviderRow(
                        provider = provider,
                        onConnect = { viewModel.clearError(); connectProvider = provider },
                        onDisconnect = { viewModel.disconnectProvider(provider.providerId) },
                        showConnect = true,
                        canDisconnect = false,
                        isSaving = uiState.isSaving,
                        isAmoled = isAmoled,
                        showSource = false,
                        canEdit = false,
                        onEdit = {},
                    )
                }
            }
        }
    }
}

private fun extractOAuthDeviceCode(instructions: String): String? {
    val codePattern = Regex("\\b[A-Z0-9]{3,}(?:-[A-Z0-9]{3,})+\\b")
    return codePattern.find(instructions)?.value
}

@Composable
private fun ProviderRow(
    provider: ProviderToggle,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    showConnect: Boolean,
    canDisconnect: Boolean,
    isSaving: Boolean,
    isAmoled: Boolean,
    showSource: Boolean,
    canEdit: Boolean,
    onEdit: () -> Unit
) {
    Card(
        shape = AppCardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer
        ),
        border = appAmoledBorder(0.65f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.providerName,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = provider.providerId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
                if (showSource) {
                    provider.source?.let { src ->
                        Text(
                            text = when (src) {
                                "env" -> stringResource(R.string.server_settings_provider_source_env)
                                "api" -> stringResource(R.string.server_settings_provider_source_api)
                                "config" -> stringResource(R.string.server_settings_provider_source_config)
                                "custom" -> stringResource(R.string.server_settings_provider_source_custom)
                                else -> stringResource(R.string.server_settings_provider_source_other)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canEdit) {
                    IconButton(onClick = onEdit, enabled = !isSaving) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.server_settings_edit_provider),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                    if (showConnect) {
                        TextButton(onClick = onConnect, enabled = !isSaving) {
                            Text(stringResource(R.string.connect))
                        }
                    } else if (canDisconnect) {
                        TextButton(onClick = onDisconnect, enabled = !isSaving) {
                            Text(stringResource(R.string.disconnect))
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.server_settings_provider_env_connected),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddProviderDialog(
    isAmoled: Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (providerId: String, name: String, baseUrl: String, models: Map<String, String>) -> Unit,
) {
    var providerId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var modelsText by remember { mutableStateOf("") }

    AppDialog(onDismissRequest = onDismiss, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.server_settings_add_provider), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = providerId,
                onValueChange = { providerId = it },
                label = { Text(stringResource(R.string.server_settings_provider_id)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedTextFieldColors(isAmoled),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.server_settings_provider_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedTextFieldColors(isAmoled),
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text(stringResource(R.string.server_settings_provider_base_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedTextFieldColors(isAmoled),
            )
            OutlinedTextField(
                value = modelsText,
                onValueChange = { modelsText = it },
                label = { Text(stringResource(R.string.server_settings_provider_initial_models)) },
                placeholder = { Text(stringResource(R.string.server_settings_provider_initial_models_hint)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedTextFieldColors(isAmoled),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AppSecondaryButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                AppPrimaryButton(
                    onClick = { onSave(providerId.trim(), name, baseUrl, parseModelsInput(modelsText)) },
                    enabled = providerId.isNotBlank() && !isSaving,
                ) { Text(stringResource(R.string.server_settings_save)) }
            }
        }
    }
}

@Composable
private fun EditProviderDialog(
    entry: ProviderConfigEntry,
    isAmoled: Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (providerId: String, name: String, baseUrl: String, models: Map<String, String>) -> Unit,
    onDelete: (providerId: String) -> Unit,
) {
    var name by remember(entry.providerId) { mutableStateOf(entry.name) }
    var baseUrl by remember(entry.providerId) { mutableStateOf(entry.baseUrl) }
    var models by remember(entry.providerId) {
        mutableStateOf(entry.models.map { (id, n) -> EditableModel(id, n) })
    }

    AppDialog(onDismissRequest = onDismiss, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.server_settings_edit_provider), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = entry.providerId,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.server_settings_basic_info),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.server_settings_provider_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedTextFieldColors(isAmoled),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(stringResource(R.string.server_settings_provider_base_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedTextFieldColors(isAmoled),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.server_settings_provider_models),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                models.forEachIndexed { index, model ->
                    ModelEditorCard(
                        index = index,
                        model = model,
                        isAmoled = isAmoled,
                        onModelIdChange = { v ->
                            models = models.mapIndexed { i, m -> if (i == index) m.copy(modelId = v) else m }
                        },
                        onNameChange = { v ->
                            models = models.mapIndexed { i, m -> if (i == index) m.copy(name = v) else m }
                        },
                        onRemove = { models = models.filterIndexed { i, _ -> i != index } },
                    )
                }
                TextButton(onClick = { models = models + EditableModel("", "") }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.server_settings_add_model))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                AppSecondaryButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                AppPrimaryButton(
                    onClick = {
                        val modelMap = models
                            .filter { it.modelId.isNotBlank() }
                            .associate { it.modelId.trim() to it.name.trim() }
                        onSave(entry.providerId, name, baseUrl, modelMap)
                    },
                    enabled = !isSaving,
                ) { Text(stringResource(R.string.server_settings_save)) }
            }

            AppSecondaryButton(
                onClick = { onDelete(entry.providerId) },
                destructive = true,
                outlined = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
            ) { Text(stringResource(R.string.server_settings_delete_provider)) }
        }
    }
}

@Composable
private fun ModelEditorCard(
    index: Int,
    model: EditableModel,
    isAmoled: Boolean,
    onModelIdChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
        border = if (isAmoled) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.server_settings_provider_model),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.server_settings_remove_model),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            OutlinedTextField(
                value = model.modelId,
                onValueChange = onModelIdChange,
                label = { Text(stringResource(R.string.server_settings_provider_model_id)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedTextFieldColors(isAmoled),
            )
            OutlinedTextField(
                value = model.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.server_settings_provider_model_name)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedTextFieldColors(isAmoled),
            )
        }
    }
}

@Composable
private fun outlinedTextFieldColors(isAmoled: Boolean) =
    if (isAmoled) {
        androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Black,
            unfocusedContainerColor = Color.Black,
            disabledContainerColor = Color.Black,
        )
    } else androidx.compose.material3.OutlinedTextFieldDefaults.colors()

private data class EditableModel(val modelId: String, val name: String)

private fun parseModelsInput(text: String): Map<String, String> {
    return text.lineSequence()
        .mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val idx = trimmed.indexOf('=')
            if (idx > 0) {
                val id = trimmed.substring(0, idx).trim()
                if (id.isEmpty()) return@mapNotNull null
                id to trimmed.substring(idx + 1).trim()
            } else {
                trimmed to trimmed
            }
        }
        .toMap()
}
