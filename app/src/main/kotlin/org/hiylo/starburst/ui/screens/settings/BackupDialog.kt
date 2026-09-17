/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : BackupDialog.kt
 * Date : 2026/09/17 10:05:36
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.AppDialog
import org.hiylo.starburst.ui.components.AppPrimaryButton
import org.hiylo.starburst.ui.components.AppSecondaryButton

/** 加密备份 / 还原对话框：口令 + SAF 文件选择。 */
@Composable
fun BackupDialog(
    viewModel: BackupViewModel,
    onDismiss: () -> Unit,
) {
    val busy by viewModel.busy.collectAsState()
    val outcome by viewModel.outcome.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var passphrase by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.reset() }

    val canProceed = !busy && passphrase.isNotBlank()

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) viewModel.export(uri, passphrase)
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.import(uri, passphrase)
    }

    AppDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.backup_restore_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.backup_restore_desc),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.backup_note),
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text(stringResource(R.string.backup_passphrase_label)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )

            when {
                busy -> Text(
                    text = stringResource(R.string.backup_working),
                    style = MaterialTheme.typography.bodyMedium,
                )
                outcome == BackupOutcome.EXPORTED -> Text(
                    text = stringResource(R.string.backup_export_success),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                outcome == BackupOutcome.IMPORTED -> Text(
                    text = stringResource(R.string.backup_import_success),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                errorMessage != null -> Text(
                    text = errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            AppPrimaryButton(
                onClick = { createLauncher.launch("starburst-backup.json") },
                enabled = canProceed,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.backup_export))
            }
            AppSecondaryButton(
                onClick = { openLauncher.launch(arrayOf("application/json", "text/plain")) },
                enabled = canProceed,
                outlined = true,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.backup_import))
            }
            AppSecondaryButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.close))
            }
        }
    }
}
