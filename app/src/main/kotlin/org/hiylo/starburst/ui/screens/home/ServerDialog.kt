/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ServerDialog.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.hiylo.starburst.R
import org.hiylo.starburst.domain.model.ServerConfig
import org.hiylo.starburst.ui.components.isAmoledTheme
import org.hiylo.starburst.ui.components.AppDialog
import org.hiylo.starburst.ui.components.AppDialogActions

/**
 * 从规范化 url 解析出主机与显式端口（无显式端口返回 null）。
 */
private fun parseHostAndPort(url: String?): Pair<String, Int?> {
    if (url.isNullOrBlank()) return "" to null
    return try {
        val parsed = java.net.URL(url)
        parsed.host to (parsed.port.takeIf { it != -1 })
    } catch (_: Exception) {
        val body = url.substringAfter("://", url)
        val host = body.substringBefore("/").substringBefore(":")
        host to body.substringAfterLast(":").toIntOrNull()
    }
}

/** 由主机与端口推导默认服务器名。 */
private fun deriveServerNameFromHost(host: String, port: Int?): String {
    val cleanHost = host.trim()
    return if (port != null) "$cleanHost:$port" else cleanHost
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerDialog(
    server: ServerConfig?,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String, username: String, password: String, autoConnect: Boolean, sshPort: Int, sshUsername: String, sshPassword: String?, backendToken: String?) -> Unit
) {
    val (initialHost, initialPort) = parseHostAndPort(server?.url)
    var name by remember(server) { mutableStateOf(server?.name ?: "") }
    var host by remember(server) { mutableStateOf(initialHost) }
    var openCodePort by remember(server) { mutableStateOf(initialPort?.toString() ?: "4096") }
    var useHttps by remember(server) { mutableStateOf(server?.url?.startsWith("https://") == true) }
    var username by remember(server) { mutableStateOf(server?.username ?: "opencode") }
    var password by remember(server) { mutableStateOf(server?.password ?: "") }
    var autoConnect by remember(server) { mutableStateOf(server?.autoConnect ?: false) }
    var sshPortText by remember(server) { mutableStateOf((server?.sshPort ?: 22).toString()) }
    var sshUsername by remember(server) { mutableStateOf(server?.sshUsername ?: "") }
    var sshPassword by remember(server) { mutableStateOf(server?.sshPassword ?: "") }
    // 新建服务器时默认填 ocb_default；编辑时如实显示已存 token（null 显示空，不自动兜底），
    // 否则用户清空 token 保存后重进又被 ocb_default 填回，误以为「删除不生效」。
    var backendToken by remember(server) { mutableStateOf(server?.backendToken ?: if (server == null) "ocb_default" else "") }

    var hostError by remember { mutableStateOf<String?>(null) }
    val hostInvalidText = stringResource(R.string.server_invalid_host)

    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    // IME 键盘「下一步」逐字段移动焦点；最后一个用「完成」收起。
    val nextAction = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })

    val isAmoled = isAmoledTheme()
    val switchColors = if (isAmoled) {
        SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.primary,
            checkedTrackColor = Color.Black,
            checkedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedTrackColor = Color.Black,
            uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
        )
    } else {
        SwitchDefaults.colors()
    }

    AppDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (server != null) stringResource(R.string.home_edit) else stringResource(R.string.server_add),
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.server_name)) },
                    placeholder = { Text(stringResource(R.string.server_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = nextAction,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = host,
                    onValueChange = {
                        host = it
                        hostError = null
                    },
                    label = { Text(stringResource(R.string.server_host)) },
                    placeholder = { Text(stringResource(R.string.server_host_hint)) },
                    isError = hostError != null,
                    supportingText = if (hostError != null) {
                        { Text(hostError!!) }
                    } else {
                        { Text(stringResource(R.string.server_host_hint)) }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    keyboardActions = nextAction,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = openCodePort,
                    onValueChange = { openCodePort = it },
                    label = { Text(stringResource(R.string.server_opencode_port)) },
                    placeholder = { Text(stringResource(R.string.server_opencode_port_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = nextAction,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.server_https),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Switch(
                            checked = useHttps,
                            onCheckedChange = { useHttps = it },
                            colors = switchColors
                        )
                    }
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.server_username)) },
                    placeholder = { Text(stringResource(R.string.server_username_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = nextAction,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.server_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    keyboardActions = nextAction,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.server_auto_connect),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = stringResource(R.string.server_auto_connect_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Switch(
                            checked = autoConnect,
                            onCheckedChange = { autoConnect = it },
                            colors = switchColors
                        )
                    }
                }

                // SSH（可选）
                Text(
                    text = stringResource(R.string.server_ssh_section),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.server_ssh_section_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = sshPortText,
                    onValueChange = { sshPortText = it },
                    label = { Text(stringResource(R.string.server_ssh_port)) },
                    placeholder = { Text("22") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = nextAction,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sshUsername,
                    onValueChange = { sshUsername = it },
                    label = { Text(stringResource(R.string.server_ssh_username)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = nextAction,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sshPassword,
                    onValueChange = { sshPassword = it },
                    label = { Text(stringResource(R.string.server_ssh_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    keyboardActions = nextAction,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 后端代理（可选）
                Text(
                    text = stringResource(R.string.server_backend_section),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.server_backend_section_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = backendToken,
                    onValueChange = { backendToken = it },
                    label = { Text(stringResource(R.string.server_backend_token)) },
                    placeholder = { Text(stringResource(R.string.server_backend_token_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AppDialogActions(
                dismissText = stringResource(R.string.server_cancel),
                confirmText = stringResource(R.string.server_save),
                onDismiss = onDismiss,
                onConfirm = {
                    val trimmedHost = host.trim()
                    hostError = when {
                        trimmedHost.isBlank() -> hostInvalidText
                        else -> null
                    }

                    if (hostError == null) {
                        val openCodePortValue = openCodePort.trim().toIntOrNull() ?: 4096
                        val scheme = if (useHttps) "https" else "http"
                        val normalizedUrl = "$scheme://$trimmedHost:$openCodePortValue"
                        val finalName = name.trim().ifBlank {
                            deriveServerNameFromHost(trimmedHost, openCodePortValue)
                        }
                        val sshPortValue = sshPortText.trim().toIntOrNull() ?: 22
                        onSave(
                            finalName,
                            normalizedUrl,
                            username.ifBlank { "opencode" },
                            password,
                            autoConnect,
                            sshPortValue,
                            sshUsername.trim(),
                            sshPassword.ifBlank { null },
                            backendToken.trim(),
                        )
                    }
                },
            )
        }
    }
}
