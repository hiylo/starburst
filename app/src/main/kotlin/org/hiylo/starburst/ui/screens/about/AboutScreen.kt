/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : AboutScreen.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.about

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CorporateFare
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.hiylo.starburst.BuildConfig
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.AppCardShape
import org.hiylo.starburst.ui.components.appAmoledBorder
import org.hiylo.starburst.ui.components.cartoonChrome
import org.hiylo.starburst.ui.components.isAmoledTheme
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") viewModel: AboutViewModel = hiltViewModel(),
) {
    val isAmoled = isAmoledTheme()
    val cardColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.about_description),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            OutlinedCard(
                modifier = Modifier.cartoonChrome(AppCardShape).fillMaxWidth(),
                shape = AppCardShape,
                colors = CardDefaults.outlinedCardColors(containerColor = cardColor),
                border = appAmoledBorder() ?: BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                val itemColors = ListItemDefaults.colors(containerColor = Color.Transparent)

                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_company)) },
                    supportingContent = {
                        Text("Clouds Studio Holding Limited", style = MaterialTheme.typography.bodySmall)
                    },
                    leadingContent = { Icon(Icons.Default.CorporateFare, contentDescription = null) },
                    colors = itemColors,
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_contact)) },
                    supportingContent = {
                        Text("hiylo@live.com", style = MaterialTheme.typography.bodySmall)
                    },
                    leadingContent = { Icon(Icons.Default.Mail, contentDescription = null) },
                    colors = itemColors,
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_tech_stack)) },
                    supportingContent = {
                        Text(
                            "Kotlin · Jetpack Compose · Material 3\n" +
                            "Hilt · Ktor · MNN · sherpa-mnn",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Build, contentDescription = null) },
                    colors = itemColors,
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.about_copyright),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
