/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : MainActivity.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import org.hiylo.starburst.logging.AppLogger as Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import org.hiylo.starburst.data.repository.SettingsRepository
import org.hiylo.starburst.data.repository.ServerConnectionStateRepository
import org.hiylo.starburst.data.repository.ServerRepository
import org.hiylo.starburst.data.repository.EventReducer
import org.hiylo.starburst.domain.model.ServerConfig
import org.hiylo.starburst.service.StarBurstConnectionService
import org.hiylo.starburst.ui.navigation.NavGraph
import org.hiylo.starburst.ui.theme.StarBurstTheme
import org.hiylo.starburst.widget.StarBurstWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

private const val TAG = "MainActivity"

internal fun findDeepLinkServer(
    servers: List<ServerConfig>,
    serverId: String,
    serverUrl: String,
): ServerConfig? {
    return servers.firstOrNull { serverId.isNotBlank() && it.id == serverId }
        ?: servers.firstOrNull { it.url.trimEnd('/') == serverUrl.trimEnd('/') }
}

/**
 * Pending deep-link info from notification tap.
 * NavGraph picks this up to navigate to WebView with the correct session URL.
 */
data class SessionDeepLink(
    val serverId: String,
    val serverUrl: String,
    val username: String,
    val password: String,
    val serverName: String,
    val sessionPath: String,  // e.g. /L2hvbWUv.../session/abc123
    val sessionId: String = "", // raw session ID (fallback when sessionPath is empty)
    val retry: Boolean = false, // whether to auto-regenerate the last message after opening
)

/**
 * Main Activity - Single Activity architecture with Jetpack Compose
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appExitReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == StarBurstConnectionService.ACTION_APP_EXIT) {
                finishAndRemoveTask()
            }
        }
    }

    private val retrySessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != StarBurstConnectionService.ACTION_RETRY_SESSION) return
            val serverId = intent.getStringExtra(StarBurstConnectionService.EXTRA_SERVER_ID) ?: ""
            val sessionId = intent.getStringExtra(StarBurstConnectionService.EXTRA_SESSION_ID) ?: ""
            if (serverId.isBlank() || sessionId.isBlank()) return
            lifecycleScope.launch {
                val savedServer = serverRepository.servers.first().firstOrNull { it.id == serverId } ?: return@launch
                _deepLinkFlow.emit(
                    SessionDeepLink(
                        serverId = savedServer.id,
                        serverUrl = savedServer.url,
                        username = savedServer.username,
                        password = savedServer.password ?: "",
                        serverName = savedServer.displayName,
                        sessionPath = "",
                        sessionId = sessionId,
                        retry = true,
                    )
                )
            }
        }
    }
    
    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var serverRepository: ServerRepository

    @Inject
    lateinit var eventReducer: EventReducer

    @Inject
    lateinit var serverConnectionStateRepository: ServerConnectionStateRepository

    @Inject
    lateinit var widgetSnapshotWriter: org.hiylo.starburst.widget.WidgetSnapshotWriter

    @Inject
    lateinit var widgetSnapshotStore: org.hiylo.starburst.widget.WidgetSnapshotStore
    
    /**
     * Shared flow for deep-link events from notification taps.
     * NavGraph subscribes and navigates to the target session when a value is emitted.
     * Uses replay=1 so a cold-start deep-link is not lost before NavGraph starts collecting.
     */
    private val _deepLinkFlow = MutableSharedFlow<SessionDeepLink>(replay = 1)

    /**
     * Shared flow for Widget / App Shortcut actions (e.g. "shortcut_search", "widget_new_session").
     * NavGraph subscribes and navigates accordingly.
     */
    private val _navActionFlow = MutableSharedFlow<String>(replay = 1)

    /**
     * Shared flow for attachments received via ACTION_SEND / ACTION_SEND_MULTIPLE.
     * NavGraph / ChatScreen consumes these to pre-populate attachments.
     * Uses replay=1 so a late subscriber (ChatScreen opened after share) still gets the URIs.
     */
    private val _sharedAttachmentsFlow = MutableSharedFlow<List<Uri>>(replay = 1)
    val sharedAttachmentsFlow = _sharedAttachmentsFlow.asSharedFlow()

    /** Language code applied via attachBaseContext for this Activity instance. */
    private var appliedLanguage: String = ""

    // Optional key interceptor used by terminal screen (e.g., virtual CTRL/FN via volume keys).
    private var terminalKeyInterceptor: ((KeyEvent) -> Boolean)? = null

    fun setTerminalKeyInterceptor(interceptor: ((KeyEvent) -> Boolean)?) {
        terminalKeyInterceptor = interceptor
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (terminalKeyInterceptor?.invoke(event) == true) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun attachBaseContext(newBase: Context) {
        // Read stored language synchronously from SharedPreferences (no Hilt needed).
        val languageCode = SettingsRepository.getStoredLanguage(newBase)
        appliedLanguage = languageCode

        if (languageCode.isNotEmpty()) {
            val locale = parseLocale(languageCode)
            Locale.setDefault(locale)
            val config = newBase.resources.configuration
            config.setLocale(locale)
            super.attachBaseContext(newBase.createConfigurationContext(config))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ContextCompat.registerReceiver(
            this,
            appExitReceiver,
            IntentFilter(StarBurstConnectionService.ACTION_APP_EXIT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        ContextCompat.registerReceiver(
            this,
            retrySessionReceiver,
            IntentFilter(StarBurstConnectionService.ACTION_RETRY_SESSION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        // Watch for language changes AFTER initial value — drop(1) skips the
        // current value that attachBaseContext already applied, so we only
        // recreate when the user actually switches language in Settings.
        lifecycleScope.launch {
            settingsRepository.appLanguage.drop(1).collect { languageCode ->
                if (languageCode != appliedLanguage) {
                    recreate()
                }
            }
        }
        
        // Handle notification tap that launched the activity
        handleSessionIntent(intent)
        // Handle attachments shared into the activity
        handleShareIntent(intent)
        // Handle Widget / App Shortcut entry actions
        handleWidgetAction(intent)

        // 持续同步桌面 Widget 所需的服务器/会话/任务快照
        widgetSnapshotWriter.start()
        setContent {
            // Collect theme preference
            val appTheme by settingsRepository.appTheme.collectAsState(initial = "system")
            val dynamicColor by settingsRepository.dynamicColor.collectAsState(
                initial = SettingsRepository.DEFAULT_DYNAMIC_COLOR,
            )
            val amoledDark by settingsRepository.amoledDark.collectAsState(initial = false)
            val accentColor by settingsRepository.accentColor.collectAsState(
                initial = SettingsRepository.DEFAULT_ACCENT_COLOR,
            )
            val themeScheme by settingsRepository.themeScheme.collectAsState(
                initial = SettingsRepository.DEFAULT_THEME_SCHEME,
            )
            val connectedServerIds by serverConnectionStateRepository.connectedServerIds.collectAsState()
            
            // Determine if dark theme should be used
            val systemDarkTheme = isSystemInDarkTheme()
            val darkTheme = when (appTheme) {
                "light" -> false
                "dark" -> true
                else -> systemDarkTheme
            }
            
            StarBurstTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor,
                amoledDark = amoledDark,
                accentColor = accentColor,
                themeScheme = themeScheme,
            ) {
                
                // Set status bar color based on theme
                SideEffect {
                    val window = this.window
                    window.statusBarColor = Color.Transparent.toArgb()
                    window.navigationBarColor = Color.Transparent.toArgb()
                    
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = !darkTheme
                    insetsController.isAppearanceLightNavigationBars = !darkTheme
                }
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph(
                        deepLinkFlow = _deepLinkFlow,
                        navActionFlow = _navActionFlow,
                        sharedAttachmentsFlow = sharedAttachmentsFlow,
                        settingsRepository = settingsRepository,
                        serverRepository = serverRepository,
                        eventReducer = eventReducer,
                        connectedServerIds = connectedServerIds,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(appExitReceiver)
        unregisterReceiver(retrySessionReceiver)
        super.onDestroy()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle notification tap when activity is already running
        handleSessionIntent(intent)
        // Handle attachments shared while the activity is already running
        handleShareIntent(intent)
        // Handle Widget / App Shortcut entry actions when the activity is already running
        handleWidgetAction(intent)
    }
    
    private fun handleWidgetAction(intent: Intent?) {
        val action = intent?.getStringExtra(StarBurstWidgetProvider.EXTRA_ACTION)
            ?.takeIf { it.isNotBlank() }
            ?: return
        when (action) {
            StarBurstWidgetProvider.ACTION_WIDGET_OPEN_SESSION -> handleWidgetOpenSession(intent)
            StarBurstWidgetProvider.ACTION_WIDGET_CONNECT_SERVER -> handleWidgetConnectServer(intent)
            else -> _navActionFlow.tryEmit(action)
        }
    }

    /** Widget 服务器行：一键直连（复用通知深链的服务启动参数格式）。 */
    private fun handleWidgetConnectServer(intent: Intent) {
        val serverId = intent.getStringExtra(StarBurstWidgetProvider.EXTRA_SERVER_ID) ?: ""
        val serverName = intent.getStringExtra(StarBurstWidgetProvider.EXTRA_SERVER_NAME) ?: ""
        val serverUrl = intent.getStringExtra(StarBurstWidgetProvider.EXTRA_SERVER_URL) ?: ""
        if (serverId.isBlank()) return
        lifecycleScope.launch {
            val savedServer = findDeepLinkServer(serverRepository.servers.first(), serverId, serverUrl)
            val resolvedServerId = savedServer?.id ?: serverId
            if (savedServer != null) {
                val serviceIntent = Intent(this@MainActivity, StarBurstConnectionService::class.java).apply {
                    putExtra("server_id", savedServer.id)
                    putExtra("server_name", savedServer.name)
                    putExtra("server_url", savedServer.url)
                    putExtra("server_username", savedServer.username)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                Log.i(TAG, "Widget connect server: ${savedServer.displayName} (serverId=$resolvedServerId)")
                _deepLinkFlow.emit(
                    SessionDeepLink(
                        serverId = resolvedServerId,
                        serverUrl = savedServer.url,
                        username = savedServer.username,
                        password = savedServer.password ?: "",
                        serverName = savedServer.displayName,
                        sessionPath = "",
                    )
                )
            } else {
                Log.w(TAG, "Widget connect server: not configured: $serverName")
            }
        }
    }

    /** Widget 最近会话行 → 复用通知深链机制直达对应会话。 */
    private fun handleWidgetOpenSession(intent: Intent) {
        val serverId = intent.getStringExtra(StarBurstWidgetProvider.EXTRA_SERVER_ID) ?: ""
        val serverName = intent.getStringExtra(StarBurstWidgetProvider.EXTRA_SERVER_NAME) ?: ""
        val sessionId = intent.getStringExtra(StarBurstWidgetProvider.EXTRA_SESSION_ID) ?: return
        val directory = intent.getStringExtra(StarBurstWidgetProvider.EXTRA_SESSION_DIRECTORY) ?: ""

        lifecycleScope.launch {
            val savedServer = findDeepLinkServer(serverRepository.servers.first(), serverId, "")
            val resolvedServerId = savedServer?.id ?: serverId
            val sessionPath = if (directory.isNotBlank()) {
                "/${base64UrlEncode(directory)}/session/$sessionId"
            } else {
                ""
            }
            Log.i(TAG, "Widget open session deep-link: ${savedServer?.url ?: serverName}$sessionPath")
            _deepLinkFlow.emit(
                SessionDeepLink(
                    serverId = resolvedServerId,
                    serverUrl = savedServer?.url ?: serverName,
                    username = savedServer?.username ?: "opencode",
                    password = savedServer?.password ?: "",
                    serverName = savedServer?.displayName ?: serverName,
                    sessionPath = sessionPath,
                    sessionId = sessionId,
                )
            )
        }
    }

    private fun base64UrlEncode(value: String): String {
        val encoded = android.util.Base64.encodeToString(
            value.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP,
        )
        return encoded.replace('+', '-').replace('/', '_').replace("=", "")
    }
    
    private fun handleSessionIntent(intent: Intent?) {
        if (intent?.action != StarBurstConnectionService.ACTION_OPEN_SESSION) return

        val serverId = intent.getStringExtra(StarBurstConnectionService.EXTRA_SERVER_ID) ?: ""
        val serverUrl = intent.getStringExtra(StarBurstConnectionService.EXTRA_SERVER_URL) ?: return
        val username = intent.getStringExtra(StarBurstConnectionService.EXTRA_SERVER_USERNAME) ?: ""
        val serverName = intent.getStringExtra(StarBurstConnectionService.EXTRA_SERVER_NAME) ?: serverUrl
        val sessionPath = intent.getStringExtra(StarBurstConnectionService.EXTRA_SESSION_PATH) ?: ""
        val sessionId = intent.getStringExtra(StarBurstConnectionService.EXTRA_SESSION_ID) ?: ""
        
        lifecycleScope.launch {
            val savedServer = findDeepLinkServer(serverRepository.servers.first(), serverId, serverUrl)
            val resolvedServerId = savedServer?.id ?: serverId
            if (savedServer != null) {
                val serviceIntent = Intent(this@MainActivity, StarBurstConnectionService::class.java).apply {
                    putExtra("server_id", savedServer.id)
                    putExtra("server_name", savedServer.name)
                    putExtra("server_url", savedServer.url)
                    putExtra("server_username", savedServer.username)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } else {
                Log.w(TAG, "Deep-link server is not configured: $serverUrl")
            }

            Log.i(TAG, "Session deep-link: $serverUrl$sessionPath (serverId=$resolvedServerId, sessionId=$sessionId)")
            _deepLinkFlow.emit(
                SessionDeepLink(
                    serverId = resolvedServerId,
                    serverUrl = savedServer?.url ?: serverUrl,
                    username = savedServer?.username ?: username,
                    password = savedServer?.password ?: "",
                    serverName = savedServer?.displayName ?: serverName,
                    sessionPath = sessionPath,
                    sessionId = sessionId,
                )
            )
        }
    }

    /**
     * Handle ACTION_SEND and ACTION_SEND_MULTIPLE with stream content.
     * Attachment type and size validation happens when ChatScreen reads each URI.
     * The URIs are content:// URIs that remain readable while the Activity is alive.
     */
    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return

        val uris = mutableListOf<Uri>()

        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                uri?.let { uris.add(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                list?.let { uris.addAll(it) }
            }
            else -> return
        }

        // Some share providers put streams only in ClipData.
        intent.clipData?.let { clipData ->
            for (index in 0 until clipData.itemCount) {
                clipData.getItemAt(index).uri?.let { uri ->
                    if (uri !in uris) uris.add(uri)
                }
            }
        }

        if (uris.isEmpty()) {
            // Pure-text share (browser link, note, etc.): materialize to a cache file
            // so the attachment pipeline can consume it.
            val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)
            val clipText = intent.clipData?.let { clip ->
                if (clip.itemCount > 0) {
                    clip.getItemAt(0).text?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
            val sharedText = extraText?.takeIf { it.isNotBlank() } ?: clipText?.toString()
            if (!sharedText.isNullOrBlank()) {
                materializeSharedText(sharedText)?.let { uris.add(it) }
            }
        }

        if (uris.isNotEmpty()) {
            // Take persistable read permission so URIs survive configuration changes
            for (uri in uris) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    // Not all providers support persistable permissions — that's OK,
                    // the temporary grant from the share intent is still valid.
                }
            }
            Log.i(TAG, "Received ${uris.size} shared attachment(s)")
            _sharedAttachmentsFlow.tryEmit(uris)
        }
    }

    /** Writes shared text into a cache file and returns its FileProvider URI. */
    private fun materializeSharedText(text: String): Uri? {
        return try {
            val dir = java.io.File(cacheDir, "shared_text").apply { mkdirs() }
            val file = java.io.File(dir, "shared_${System.currentTimeMillis()}.txt")
            file.writeText(text, Charsets.UTF_8)
            androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to materialize shared text", e)
            null
        }
    }

    companion object {
        /** Parse BCP 47 tag (e.g. "pt-BR", "zh-CN", "en") into a [Locale]. */
        fun parseLocale(tag: String): Locale {
            val parts = tag.split("-")
            return if (parts.size >= 2) {
                Locale(parts[0], parts[1].uppercase())
            } else {
                Locale(parts[0])
            }
        }
    }
}
