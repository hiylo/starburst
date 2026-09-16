/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : StarBurstConnectionService.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import org.hiylo.starburst.logging.AppLogger as Log
import androidx.core.app.NotificationCompat
import org.hiylo.starburst.BuildConfig
import org.hiylo.starburst.MainActivity
import org.hiylo.starburst.R
import android.os.Handler
import android.os.Looper
import org.hiylo.starburst.data.api.BackendApi
import org.hiylo.starburst.data.api.BackendStatus
import org.hiylo.starburst.data.api.OpenCodeApi
import org.hiylo.starburst.data.api.OpenCodeGateway
import org.hiylo.starburst.data.api.ServerConnection
import org.hiylo.starburst.data.api.SseClient
import org.hiylo.starburst.data.backend.BackendPushListener
import org.hiylo.starburst.data.backend.PushSessionEvent
import org.hiylo.starburst.data.api.listMessages
import org.hiylo.starburst.data.api.listPendingPermissions
import org.hiylo.starburst.data.api.listPendingQuestions
import org.hiylo.starburst.data.api.listSessions
import org.hiylo.starburst.data.api.listSessionStatuses
import org.hiylo.starburst.data.api.listSessionStatusesForDirectories
import org.hiylo.starburst.data.repository.EventReducer
import org.hiylo.starburst.data.repository.ServerRepository
import org.hiylo.starburst.data.repository.normalizeServerUrl
import org.hiylo.starburst.data.repository.ServerConnectionStateRepository
import org.hiylo.starburst.data.repository.SettingsRepository
import org.hiylo.starburst.domain.model.Message
import org.hiylo.starburst.domain.model.Part
import org.hiylo.starburst.domain.model.ServerConfig
import org.hiylo.starburst.domain.model.Session
import org.hiylo.starburst.domain.model.SessionStatus
import org.hiylo.starburst.domain.model.SseEvent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session as JschSession
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

private const val TAG = "StarBurstService"
private const val NOTIFICATION_CHANNEL_ID = "starburst_connection"
private const val NOTIFICATION_CHANNEL_TASKS_ID = "starburst_tasks"
private const val NOTIFICATION_CHANNEL_TASKS_SILENT_ID = "starburst_tasks_silent"
private const val NOTIFICATION_CHANNEL_PERMISSIONS_ID = "starburst_permissions"
private const val PERSISTENT_NOTIFICATION_ID = 1001
private const val WAKELOCK_TAG = "StarBurst::SSEConnection"

// Reconnect timing
private const val RECONNECT_BASE_DELAY_MS = 1_000L   // 1 second
private const val RECONNECT_MAX_DELAY_MS = 30_000L   // 30 seconds
private const val RECONNECT_BACKOFF_FACTOR = 2.0
private const val RECOVERY_DEBOUNCE_MS = 5_000L
private const val MAX_RECONCILED_MESSAGE_SESSIONS = 20
internal const val FAILED_CONNECTION_TIMEOUT_MS = 15 * 60 * 1000L
private const val DISCONNECTED_SERVERS_PREFS = "explicitly_disconnected_servers"
private const val DISCONNECTED_SERVERS_KEY = "disconnected_server_ids"
private const val SSH_CONNECT_TIMEOUT_MS = 15_000
/** SSE 假死检测阈值：连接建立后超过该时长未收到任何实质事件（消息/状态/step），判定假死。 */
private const val SSE_STALL_TIMEOUT_MS = 30_000L
/** SSE 假死检测的检查间隔。 */
private const val SSE_STALL_CHECK_INTERVAL_MS = 15_000L
/** 会话完成兜底轮询间隔：SSE 假死收不到 session.idle 时，靠轮询 /session/status 检测 busy→idle。 */
private const val COMPLETION_POLL_INTERVAL_MS = 15_000L
/** 后端推送连续失败达到该次数后，把该 server 连接回退到直连 opencode。 */
private const val BACKEND_FALLBACK_THRESHOLD = 3
/** 通知正文结果摘要的最大字符数（约 80 字）。 */
private const val NOTIFICATION_SUMMARY_MAX_CHARS = 80

internal fun hasFailedConnectionTimedOut(failureStartedAt: Long, now: Long): Boolean {
    return now - failureStartedAt >= FAILED_CONNECTION_TIMEOUT_MS
}

internal fun sameServerEndpoint(firstUrl: String, secondUrl: String): Boolean {
    return normalizeServerUrl(firstUrl) == normalizeServerUrl(secondUrl)
}

internal fun sessionsNeedingMessageReconciliation(
    localSessions: Map<String, Session>,
    remoteSessions: List<Session>,
    limit: Int = MAX_RECONCILED_MESSAGE_SESSIONS,
): List<Session> = remoteSessions
    .asSequence()
    .filter { remote ->
        val local = localSessions[remote.id]
        local == null || remote.time.updated > local.time.updated
    }
    .sortedByDescending { it.time.updated }
    .take(limit)
    .toList()

/** 服务器连接的运行状态。 */
enum class ServerConnectionStatus { DISCONNECTED, CONNECTED, RECONNECTING, FAILED }

/** 服务器连接的实时指标（延迟与上次心跳）。 */
data class ServerConnectionMetrics(
    val latencyMs: Long? = null,
    val lastHeartbeatAt: Long? = null,
)

/**
 * Per-server connection state held by the service.
 */
private data class ServerConnectionState(
    val config: ServerConfig,
    val conn: ServerConnection,
    val sseJob: Job,
    val isConnected: Boolean = false,
    val sshSession: JschSession? = null,
    val pushJob: Job? = null,
    val directConn: ServerConnection? = null,
    /** SSH 隧道内后端镜像的本地端口（SSH 模式额外转发 18880；非 SSH 模式为 null）。 */
    val backendLocalPort: Int? = null,
)

/**
 * Foreground Service for maintaining OpenCode SSE connections to multiple servers.
 *
 * This service:
 * - Maintains persistent SSE connections to one or more servers simultaneously
 * - Processes events via EventReducer (with serverId tracking)
 * - Shows notifications for task completion and permission requests
 * - Auto-reconnects with exponential backoff on disconnection/error
 * - Optionally holds a single partial WakeLock while any server is connected
 * - Shows an InboxStyle persistent notification summarising connected servers
 * - Groups event notifications by server
 *
 * The connections stay alive until the user explicitly disconnects each server
 * (or uses "Disconnect All").
 */
@AndroidEntryPoint
class StarBurstConnectionService : Service() {

    override fun attachBaseContext(newBase: Context) {
        val languageCode = SettingsRepository.getStoredLanguage(newBase)
        if (languageCode.isNotEmpty()) {
            val locale = MainActivity.parseLocale(languageCode)
            Locale.setDefault(locale)
            val config = newBase.resources.configuration
            config.setLocale(locale)
            super.attachBaseContext(newBase.createConfigurationContext(config))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    @Inject
    lateinit var api: OpenCodeApi

    @Inject
    lateinit var sseClient: SseClient

    @Inject
    lateinit var eventReducer: EventReducer

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var serverRepository: ServerRepository

    @Inject
    lateinit var serverConnectionStateRepository: ServerConnectionStateRepository

    @Inject
    lateinit var backendApi: BackendApi

    @Inject
    lateinit var backendPushListener: BackendPushListener

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Main-thread handler used to defer stopSelf() until after any queued connect completes. */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Set when a stopSelf() has been requested but not yet executed (so a concurrent connect can cancel it). */
    @Volatile
    private var pendingStopSelf = false

    /** SharedPreferences holding server IDs the user has explicitly disconnected (persisted across process restarts). */
    private val disconnectedServersPrefs by lazy {
        getSharedPreferences(DISCONNECTED_SERVERS_PREFS, MODE_PRIVATE)
    }

    /** All active/pending server connections keyed by serverId. */
    private val connections = ConcurrentHashMap<String, ServerConnectionState>()

    /** Cached reconnect mode ("aggressive" | "normal" | "conservative"), refreshed from DataStore on start. */
    @Volatile
    private var reconnectMode: String = "normal"

    /** Cached do-not-disturb snapshot, refreshed from DataStore on start. */
    @Volatile
    private var dndEnabledSnapshot: Boolean = false

    @Volatile
    private var dndStartSnapshot: String = "22:00"

    @Volatile
    private var dndEndSnapshot: String = "07:00"

    private var autoConnectJob: Job? = null
    @Volatile
    private var recoveryJob: Job? = null
    private val reconciliationJobs = ConcurrentHashMap<String, Job>()
    private val explicitlyDisconnectedServerIds = ConcurrentHashMap.newKeySet<String>()
    @Volatile
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile
    private var backgroundWakeLockEnabled = false
    @Volatile
    private var lastRecoveryAt = 0L
    @Volatile
    private var lastDefaultNetwork: Network? = null
    private lateinit var connectivityManager: ConnectivityManager
    @Volatile
    private var lastPersistentNotificationState: List<Triple<String, String, Boolean>>? = null

    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED) {
                val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                if (powerManager.isDeviceIdleMode) return
            }
            recoverConnectionsWithoutWakeLock("device wake")
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val previous = lastDefaultNetwork
            lastDefaultNetwork = network
            // Recover on the first available network too, not just on network changes.
            if (previous != network) {
                recoverConnectionsWithoutWakeLock("default network available")
            }
        }

        override fun onLost(network: Network) {
            if (lastDefaultNetwork == network) {
                lastDefaultNetwork = null
            }
            // Let the SSE heartbeats/backoff handle reconnection; nothing else to force here,
            // but clearing lastDefaultNetwork ensures the next onAvailable triggers recovery.
        }
    }

    private lateinit var notificationManager: NotificationManager
    private var foregroundStarted: Boolean = false

    /** Observable set of server IDs that are actually connected (SSE stream active). */
    private val _connectedServerIds = MutableStateFlow<Set<String>>(emptySet())
    val connectedServerIds: StateFlow<Set<String>> = _connectedServerIds.asStateFlow()

    /** Observable set of server IDs that are attempting to connect (SSE not yet established or reconnecting). */
    private val _connectingServerIds = MutableStateFlow<Set<String>>(emptySet())
    val connectingServerIds: StateFlow<Set<String>> = _connectingServerIds.asStateFlow()

    /** Per-server connection errors surfaced to the UI (e.g. authentication failures during SSE handshake). */
    private val _connectionErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val connectionErrors: StateFlow<Map<String, String>> = _connectionErrors.asStateFlow()

    /** Per-server connection metrics (latency, last heartbeat) surfaced to the UI. */
    private val _serverMetrics = MutableStateFlow<Map<String, ServerConnectionMetrics>>(emptyMap())
    val serverMetrics: StateFlow<Map<String, ServerConnectionMetrics>> = _serverMetrics.asStateFlow()

    /** Tracks when each server's latest connect attempt started (monotonic clock) to measure latency. */
    private val connectStartedAt = ConcurrentHashMap<String, Long>()

    /** Dedup response-ready notifications per session by last assistant message ID. */
    private val lastNotifiedAssistantMessageBySession = ConcurrentHashMap<String, String>()

    /** 上次轮询时各 server 处于 busy 的会话集合，用于检测 busy→idle 完成转场（SSE 假死兜底）。 */
    private val lastBusySessions = ConcurrentHashMap<String, Set<String>>()

    inner class LocalBinder : Binder() {
        fun getService(): StarBurstConnectionService = this@StarBurstConnectionService
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Log.d(TAG, "Service created")

        // Restore the explicit-disconnect set before anything can auto-connect.
        loadPersistedDisconnectedServers()

        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        createNotificationChannels()

        val wakeFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wakeReceiver, wakeFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(wakeReceiver, wakeFilter)
        }
        connectivityManager.registerDefaultNetworkCallback(networkCallback)

        autoConnectJob = serviceScope.launch {
            autoConnectConfiguredServers()
        }
        serviceScope.launch {
            settingsRepository.reconnectMode.collect { mode ->
                reconnectMode = mode
            }
        }
        serviceScope.launch {
            settingsRepository.dndEnabled.collect { dndEnabledSnapshot = it }
        }
        serviceScope.launch {
            settingsRepository.dndStart.collect { dndStartSnapshot = it }
        }
        serviceScope.launch {
            settingsRepository.dndEnd.collect { dndEndSnapshot = it }
        }
        serviceScope.launch {
            settingsRepository.backgroundWakeLock.collect { enabled ->
                if (backgroundWakeLockEnabled == enabled) return@collect
                backgroundWakeLockEnabled = enabled
                if (enabled && connections.isNotEmpty()) {
                    acquireWakeLock()
                } else if (!enabled) {
                    releaseWakeLock()
                }
                Log.i(TAG, "Background WakeLock ${if (enabled) "enabled" else "disabled"}")
            }
        }
        serviceScope.launch {
            connectedServerIds.collect(serverConnectionStateRepository::updateConnectedServerIds)
        }
        // 周期性检测 SSE 假死（连接正常但长时间无实质事件，同时有会话 busy），并主动重连。
        serviceScope.launch {
            while (isActive) {
                delay(SSE_STALL_CHECK_INTERVAL_MS)
                detectAndRecoverStalledConnections()
            }
        }
        // 会话完成兜底轮询：SSE 假死时靠轮询 /session/status 检测 busy→idle 并推送完成通知。
        serviceScope.launch {
            while (isActive) {
                delay(COMPLETION_POLL_INTERVAL_MS)
                pollSessionCompletions()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (BuildConfig.DEBUG) Log.d(TAG, "Service started, action=${intent?.action}")

        when (intent?.action) {
            ACTION_EXIT -> {
                Log.i(TAG, "Exit requested via notification")
                notificationManager.cancelAll()
                sendBroadcast(Intent(ACTION_APP_EXIT).setPackage(packageName))
                disconnectAll()
                return START_NOT_STICKY
            }
            ACTION_DISCONNECT -> {
                val serverId = intent.getStringExtra("server_id")
                if (serverId != null) {
                    Log.i(TAG, "Disconnect requested for server $serverId")
                    disconnect(serverId)
                }
                return START_NOT_STICKY
            }
        }

        // Read server details from intent and connect
        val serverId = intent?.getStringExtra("server_id")
        val serverUrl = intent?.getStringExtra("server_url")
        if (serverId != null && serverUrl != null) {
            val config = ServerConfig(
                id = serverId,
                url = serverUrl,
                username = intent.getStringExtra("server_username") ?: "opencode",
                password = intent.getStringExtra("server_password"),
                name = intent.getStringExtra("server_name"),
                sshPort = intent.getIntExtra("server_ssh_port", 22),
                sshUsername = intent.getStringExtra("server_ssh_username") ?: "",
                sshPassword = intent.getStringExtra("server_ssh_password"),
            )
            // SSH tunnel establishment is blocking; run off the main thread.
            serviceScope.launch { connect(config) }
            return START_NOT_STICKY
        }

        // A sticky restart has no server extras. Give auto-connect a chance, then remove any orphan notification.
        ensureForegroundStarted()
        serviceScope.launch {
            autoConnectJob?.join()
            if (connections.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                notificationManager.cancel(PERSISTENT_NOTIFICATION_ID)
                foregroundStarted = false
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        serverConnectionStateRepository.updateConnectedServerIds(emptySet())
        unregisterReceiver(wakeReceiver)
        connectivityManager.unregisterNetworkCallback(networkCallback)
        super.onDestroy()
        if (BuildConfig.DEBUG) Log.d(TAG, "Service destroyed")
        disconnectAllInternal(stopService = false)
        serviceScope.cancel()
    }

    // ============ Public API ============

    /**
     * Connect to an OpenCode server. If already connected to this server, no-op.
     * Multiple servers can be connected simultaneously.
     */
    @Synchronized
    fun connect(server: ServerConfig) {
        explicitlyDisconnectedServerIds.remove(server.id)
        persistDisconnectedServers()
        connectInternal(server)
    }

    @Synchronized
    private fun connectInternal(server: ServerConfig) {
        // Re-check inside the lock: a concurrent disconnect() may have marked this
        // server as explicitly disconnected while autoConnect waited for the lock.
        if (server.id in explicitlyDisconnectedServerIds) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Server ${server.id} explicitly disconnected, skipping auto-connect")
            return
        }
        val activeEndpointConnection = connections.values.firstOrNull { state ->
            sameServerEndpoint(state.config.url, server.url) && !state.sseJob.isCompleted
        }
        if (activeEndpointConnection != null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Already connected to endpoint for server ${activeEndpointConnection.config.id}, skipping ${server.id}")
            }
            return
        }
        var replacement: ServerConnectionState? = null
        var replaced: ServerConnectionState? = null
        val resolved = try {
            resolveConnection(server)
        } catch (e: Exception) {
            Log.e(TAG, "[${server.displayName}] Failed to resolve connection", e)
            _connectionErrors.update { it + (server.id to (e.message ?: getString(R.string.home_server_not_responding))) }
            return
        }
        connections.compute(server.id) { _, existing ->
            if (existing != null && !existing.sseJob.isCompleted) return@compute existing
            replaced = existing
            val baseConn = ServerConnection.from(resolved.baseUrl, server.username, server.password)
            val conn = buildGatewayConn(server, baseConn, resolved.backendLocalPort)
            val sseJob = startSseConnection(server, conn)
            val pushJob = startBackendPushJob(server, conn, resolved.backendLocalPort)
            // SSE 结束（正常/异常/被 cancel）时取消「当前」push job。用 state 实时取值，
            // 避免替换 pushJob（如 replaceSshSession 后）仍取消旧 job 导致新推送停不掉。
            sseJob.invokeOnCompletion { connections[server.id]?.pushJob?.cancel() }
            ServerConnectionState(
                config = server,
                conn = conn,
                sseJob = sseJob,
                isConnected = false,
                sshSession = resolved.sshSession,
                pushJob = pushJob,
                directConn = baseConn,
                backendLocalPort = resolved.backendLocalPort,
            ).also { replacement = it }
        }
        val state = replacement
        if (state == null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Already connected to server ${server.id}, skipping")
            return
        }
        replaced?.sseJob?.cancel()
        closeSshSession(replaced?.sshSession)

        if (BuildConfig.DEBUG) Log.d(TAG, "Connecting to configured server")

        ensureForegroundStarted()
        acquireWakeLock()
        _connectingServerIds.update { it + server.id }
        _connectionErrors.update { it - server.id }
        connectStartedAt[server.id] = SystemClock.elapsedRealtime()
        _serverMetrics.update { it - server.id }
        updatePersistentNotification()
        state.sseJob.start()
    }

    /**
     * Disconnect from a single server.
     */
    @Synchronized
    fun disconnect(serverId: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Disconnecting server $serverId")

        explicitlyDisconnectedServerIds.add(serverId)
        persistDisconnectedServers()
        val state = connections.remove(serverId) ?: return
        state.sseJob.cancel()
        state.pushJob?.cancel()
        closeSshSession(state.sshSession)
        reconciliationJobs.remove(serverId)?.cancel()

        _connectedServerIds.update { it - serverId }
        _connectingServerIds.update { it - serverId }
        _connectionErrors.update { it - serverId }
        clearServerMetrics(serverId)

        eventReducer.clearForServer(serverId)

        if (connections.isEmpty()) {
            stopServiceIfIdle()
        } else {
            updatePersistentNotification()
        }
    }

    /**
     * Disconnect from all servers and stop the service.
     */
    fun disconnectAll() {
        disconnectAllInternal(stopService = true)
    }

    /**
     * Requests that the service stop itself once connections are empty.
     * Deferred to the main thread and re-checked so a connect that queued
     * behind this call cannot be killed by a stale stopSelf().
     */
    private fun stopServiceIfIdle() {
        if (pendingStopSelf) return
        pendingStopSelf = true
        mainHandler.post {
            pendingStopSelf = false
            synchronized(this) {
                if (connections.isEmpty()) {
                    releaseWakeLock()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    lastPersistentNotificationState = null
                    foregroundStarted = false
                    stopSelf()
                }
            }
        }
    }

    @Synchronized
    private fun disconnectAllInternal(stopService: Boolean) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Disconnecting all servers")
        if (stopService) autoConnectJob?.cancel()

        for ((_, state) in connections) {
            state.sseJob.cancel()
            state.pushJob?.cancel()
            closeSshSession(state.sshSession)
        }
        reconciliationJobs.values.forEach { it.cancel() }
        reconciliationJobs.clear()
        val serverIds = connections.keys.toList()
        connections.clear()

        // Remember the disconnect intent so auto-connect doesn't reconnect these servers after a process restart.
        if (stopService) {
            explicitlyDisconnectedServerIds.addAll(serverIds)
            persistDisconnectedServers()
        }

        _connectedServerIds.value = emptySet()
        _connectingServerIds.value = emptySet()
        _serverMetrics.value = emptyMap()
        connectStartedAt.clear()

        for (serverId in serverIds) {
            eventReducer.clearForServer(serverId)
        }

        releaseWakeLock()

        if (stopService) {
            stopServiceIfIdle()
        }
    }

    /** Persists the set of explicitly-disconnected server IDs so it survives process restarts. */
    private fun persistDisconnectedServers() {
        val ids = explicitlyDisconnectedServerIds.toList()
        disconnectedServersPrefs.edit().putStringSet(DISCONNECTED_SERVERS_KEY, ids.toSet()).apply()
    }

    /** Loads persisted disconnected server IDs after process restart. */
    private fun loadPersistedDisconnectedServers() {
        explicitlyDisconnectedServerIds.addAll(
            disconnectedServersPrefs.getStringSet(DISCONNECTED_SERVERS_KEY, emptySet()).orEmpty(),
        )
    }

    private suspend fun autoConnectConfiguredServers() {
        try {
            val autoConnectServers = serverRepository.servers.first().filter { it.autoConnect }
            if (autoConnectServers.isEmpty()) return
            Log.i(TAG, "Auto-connecting ${autoConnectServers.size} server(s)")
            autoConnectServers.forEach { server ->
                if (server.id !in explicitlyDisconnectedServerIds) connectInternal(server)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to auto-connect servers", e)
        }
    }

    @Synchronized
    private fun ensureForegroundStarted() {
        if (foregroundStarted) return
        startForeground(PERSISTENT_NOTIFICATION_ID, createPersistentNotification())
        lastPersistentNotificationState = connections.values
            .map { Triple(it.config.id, it.config.displayName, it.isConnected) }
            .sortedBy { it.first }
        foregroundStarted = true
    }

    /**
     * Check if a specific server is connected.
     */
    fun isConnected(serverId: String): Boolean {
        return connections[serverId]?.sseJob?.isActive == true
    }

    /**
     * 连接所用的 baseUrl、可选 SSH 会话（本地端口转发建立后返回 127.0.0.1:localPort），
     * 以及后端镜像在隧道内的本地端口（SSH 模式下额外转发 host:18880，否则为 null）。
     */
    private data class ResolvedConnection(
        val baseUrl: String,
        val sshSession: JschSession?,
        val backendLocalPort: Int? = null,
    )

    /**
     * 解析连接 baseUrl：未配置 SSH 时直连 [ServerConfig.url]；配置了 SSH 时通过
     * JSch 建立 `host:sshPort` 的会话并做本地端口转发，返回 127.0.0.1:localPort。
     * 额外把后端端口（默认 18880）也转发到本地，保证 SSH 隧道模式下后端推送/镜像可用。
     */
    private fun resolveConnection(server: ServerConfig): ResolvedConnection {
        if (!server.useSsh) return ResolvedConnection(server.url, null)
        val host = server.host
        val openCodePort = server.openCodePort
        var session: JschSession? = null
        return try {
            val jsch = JSch()
            session = jsch.getSession(server.sshUsername, host, server.sshPort)
            session.setPassword(server.sshPassword ?: "")
            session.setConfig("StrictHostKeyChecking", "no")
            // SSH 保活：防止会话因空闲/网络波动被中间设备掐断，降低隧道断连概率。
            session.setServerAliveInterval(15_000)
            session.setServerAliveCountMax(3)
            session.connect(SSH_CONNECT_TIMEOUT_MS)
            val localPort = session.setPortForwardingL(0, host, openCodePort)
            Log.i(TAG, "[${server.displayName}] SSH tunnel established: 127.0.0.1:$localPort -> $host:$openCodePort")
            // 后端镜像端口：显式 backendUrl 的端口优先，否则默认 18880。
            val backendRemotePort = try {
                java.net.URL(server.backendResolvedUrl).port.takeIf { it != -1 } ?: 18880
            } catch (_: Exception) {
                18880
            }
            val backendLocalPort = try {
                session.setPortForwardingL(0, host, backendRemotePort)
            } catch (_: Exception) {
                Log.w(TAG, "[${server.displayName}] Backend port $backendRemotePort not reachable over SSH, pushing disabled")
                null
            }
            if (backendLocalPort != null) {
                Log.i(TAG, "[${server.displayName}] Backend tunnel established: 127.0.0.1:$backendLocalPort -> $host:$backendRemotePort")
            }
            ResolvedConnection("http://127.0.0.1:$localPort", session, backendLocalPort)
        } catch (e: Exception) {
            // 端口转发失败等场景：会话已 connect 出但未妥善释放，必须主动断开避免 TCP/守护线程泄漏。
            session?.disconnect()
            Log.e(TAG, "[${server.displayName}] Failed to establish SSH tunnel", e)
            throw e
        }
    }

    /** 静默断开 SSH 会话。 */
    private fun closeSshSession(session: JschSession?) {
        try { session?.disconnect() } catch (_: Exception) { }
    }

    /** 用重建后的 SSH 隧道替换某 server 的连接信息（关闭旧会话），并同步后端本地端口、重启推送 job。 */
    private fun replaceSshSession(serverId: String, newConn: ServerConnection, newSsh: JschSession?, newBackendLocalPort: Int?) {
        val oldState = connections[serverId] ?: return
        val oldSsh = oldState.sshSession
        val newPush = startBackendPushJob(oldState.config, newConn, newBackendLocalPort)
        val applied = connections.compute(serverId) { _, state ->
            if (state == null || state.sseJob !== oldState.sseJob) return@compute state
            state.copy(
                conn = newConn,
                sshSession = newSsh,
                pushJob = newPush,
                backendLocalPort = newBackendLocalPort,
            )
        }
        if (applied !== oldState) {
            // 期间 state 已被并发替换（forceReconnect / 恢复流程），本次替换未生效：
            // 关闭刚建立的隧道与新 push job，避免泄漏/重复订阅。
            newPush?.cancel()
            closeSshSession(newSsh)
            return
        }
        oldState.pushJob?.cancel()
        closeSshSession(oldSsh)
    }

    // ============ WakeLock ============

    @Synchronized
    private fun acquireWakeLock() {
        if (!backgroundWakeLockEnabled) return
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            acquire()
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "WakeLock acquired")
    }

    @Synchronized
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                if (BuildConfig.DEBUG) Log.d(TAG, "WakeLock released")
            }
        }
        wakeLock = null
    }

    /**
     * Without a WakeLock Android may suspend a healthy-looking socket during Doze. Replacing each SSE job after
     * wake/network recovery forces a fresh stream and the normal server-state reconciliation.
     */
    @Synchronized
    private fun recoverConnectionsWithoutWakeLock(reason: String) {
        if (backgroundWakeLockEnabled || connections.isEmpty()) return
        if (recoveryJob?.isActive == true) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRecoveryAt < RECOVERY_DEBOUNCE_MS) return
        lastRecoveryAt = now

        recoveryJob = serviceScope.launch {
            val states = connections.values.toList()
            if (states.isEmpty() || backgroundWakeLockEnabled) return@launch
            Log.i(TAG, "Recovering ${states.size} connection(s) after $reason")
            for (state in states) {
                val job = startSseConnection(state.config, state.conn, preload = false)
                val newPush = startBackendPushJob(state.config, state.conn, state.backendLocalPort)
                job.invokeOnCompletion { connections[state.config.id]?.pushJob?.cancel() }
                val replacement = state.copy(sseJob = job, isConnected = false, pushJob = newPush)
                if (!connections.replace(state.config.id, state, replacement)) {
                    job.cancel()
                    newPush?.cancel()
                    continue
                }
                state.sseJob.cancel()
                reconciliationJobs.remove(state.config.id)?.cancel()
                _connectedServerIds.update { it - state.config.id }
                _connectingServerIds.update { it + state.config.id }
                connectStartedAt[state.config.id] = SystemClock.elapsedRealtime()
                _serverMetrics.update { it - state.config.id }
                job.start()
            }
            updatePersistentNotification()
        }
    }

    /**
     * 检测并恢复 SSE 假死。
     *
     * SSE 偶发会进入「假死」状态：TCP 连接仍在、服务端也持续发 server.heartbeat 心跳，
     * 但不再推送消息/状态等实质事件。此时 `readUTF8Line` 因心跳而不会超时，重连逻辑不会被触发，
     * 导致网页端已实时刷新、而 App 一直卡着。这里在「连接已建立 + 有会话 busy + 长时间无实质事件」
     * 时主动重建连接。
     */
    private fun detectAndRecoverStalledConnections() {
        val now = System.currentTimeMillis()
        val statuses = eventReducer.sessionStatuses.value
        val serverSessions = eventReducer.serverSessions.value
        for ((serverId, state) in connections) {
            if (!state.isConnected) continue
            val lastHeartbeat = _serverMetrics.value[serverId]?.lastHeartbeatAt ?: continue
            if (now - lastHeartbeat < SSE_STALL_TIMEOUT_MS) continue
            // 该 server 下是否有 busy 会话；否则长时间无事件是正常的 idle，不应重连。
            val hasBusySession = serverSessions[serverId].orEmpty().any { sessionId ->
                statuses[sessionId] is SessionStatus.Busy
            }
            if (!hasBusySession) continue
            Log.w(
                TAG,
                "[${state.config.displayName}] SSE stream stalled (no events for ${now - lastHeartbeat}ms) while a session is busy; forcing reconnect",
            )
            forceReconnect(serverId)
        }
    }

    /** 强制重建某个 server 的 SSE 连接（用于假死恢复）。 */
    private fun forceReconnect(serverId: String) {
        val state = connections[serverId] ?: return
        val job = startSseConnection(state.config, state.conn, preload = false)
        val newPush = startBackendPushJob(state.config, state.conn, state.backendLocalPort)
        job.invokeOnCompletion { connections[serverId]?.pushJob?.cancel() }
        val replacement = state.copy(sseJob = job, isConnected = false, pushJob = newPush)
        if (!connections.replace(serverId, state, replacement)) {
            job.cancel()
            newPush?.cancel()
            return
        }
        state.sseJob.cancel()
        reconciliationJobs.remove(serverId)?.cancel()
        _connectedServerIds.update { it - serverId }
        _connectingServerIds.update { it + serverId }
        connectStartedAt[serverId] = SystemClock.elapsedRealtime()
        _serverMetrics.update { it - serverId }
        job.start()
        updatePersistentNotification()
    }

    /**
     * 会话完成兜底轮询。
     *
     * SSE 假死时会收不到 session.idle 事件，导致「会话完成」通知丢失。这里定期拉取
     * /session/status（仅返回 busy/retry 会话），对比上一轮的 busy 集合，检测 busy→idle
     * 转场；对完成会话先拉一次最新消息（补齐假死期间漏掉的内容），再复用 notifySessionComplete
     * 推送通知。SSE 正常时该轮询因去重而不会重复推送。
     */
    private suspend fun pollSessionCompletions() {
        for ((serverId, state) in connections) {
            if (!state.isConnected) continue
            val localStatuses = eventReducer.sessionStatuses.value
            val serverSessionIds = eventReducer.serverSessions.value[serverId].orEmpty()
            // 耗电优化：本地 SSE 状态无任何 busy/retry 会话、且上一轮也未发现活跃会话时，
            // 直接跳过网络轮询（/session/status 每 15s 无条件请求在空闲时白耗电）。
            // SSE 假死时会话停留在旧 busy 状态，lastBusy 兜底保证仍会继续轮询，不会丢完成通知。
            val hasLocalActive = serverSessionIds.any { sid ->
                localStatuses[sid] is SessionStatus.Busy || localStatuses[sid] is SessionStatus.Retry
            }
            if (!hasLocalActive && lastBusySessions[serverId].orEmpty().isEmpty()) continue
            val directories = eventReducer.sessions.value.asSequence()
                .filter { it.id in serverSessionIds }
                .map { it.directory }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()
            val statuses = try {
                api.listSessionStatusesForDirectories(state.conn, directories)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                continue
            }
            val currentBusy = statuses.filterValues { it is SessionStatus.Busy }.keys
            val previous = lastBusySessions[serverId].orEmpty()
            lastBusySessions[serverId] = currentBusy
            // 仍在活动的会话 = busy + retry（/session/status 只返回这两类）；
            // 上次 busy 但这次彻底不在 = 已 idle 完成，避免把 busy→retry 误判为完成。
            val completed = previous - statuses.keys
            for (sessionId in completed) {
                if (isChildSession(sessionId)) continue
                // 补齐假死期间漏掉的消息，否则 latestNotifiableAssistantMessageId 找不到最新 assistant 消息。
                try {
                    val messages = api.listMessages(state.conn, sessionId, limit = 50)
                    eventReducer.mergeMessages(sessionId, messages, serverId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "[${state.config.displayName}] Failed to fetch messages for completed session $sessionId: ${e.message}")
                }
                notifySessionComplete(state.config, sessionId)
            }
        }
    }

    // ============ SSE Connection with Auto-Reconnect ============

    private fun startSseConnection(
        server: ServerConfig,
        conn: ServerConnection,
        preload: Boolean = true,
    ): Job {
        return serviceScope.launch(start = CoroutineStart.LAZY) {
            val currentJob = coroutineContext[Job]!!
            var attempt = 0
            var failureStartedAt: Long? = SystemClock.elapsedRealtime()
            var preloaded = !preload
            var currentConn = conn

            while (isActive) {
                failureStartedAt?.let { failedSince ->
                    if (hasFailedConnectionTimedOut(failedSince, SystemClock.elapsedRealtime())) {
                        Log.w(TAG, "[${server.displayName}] Stopping reconnect after 15 minutes without a connection")
                        _connectionErrors.update { it - server.id }
                        cleanupTerminatedConnection(server.id, currentJob)
                        return@launch
                    }
                }
                attempt++
                if (attempt <= 3 || attempt % 10 == 0) {
                    Log.i(TAG, "[${server.displayName}] SSE connection attempt #$attempt")
                } else if (BuildConfig.DEBUG) {
                    Log.d(TAG, "[${server.displayName}] SSE connection attempt #$attempt")
                }

                // SSH 隧道断开后需重建：首次连接由 connectInternal 建好隧道；之后每次重连前
                // 重建一次，避免隧道已断（SSH 会话超时/网络波动）却仍用失效的
                // 127.0.0.1:localPort 反复 ECONNREFUSED 而永远连不上。
                if (server.useSsh && attempt > 1) {
                    try {
                        val resolved = resolveConnection(server)
                        val baseConn = ServerConnection.from(resolved.baseUrl, server.username, server.password)
                        val newConn = buildGatewayConn(server, baseConn, resolved.backendLocalPort)
                        replaceSshSession(server.id, newConn, resolved.sshSession, resolved.backendLocalPort)
                        currentConn = newConn
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "[${server.displayName}] Failed to re-establish SSH tunnel: ${e.message}")
                    }
                }

                if (!preloaded) {
                    preloaded = true
                    // Pre-load once. Reconciliation refreshes state after a successful reconnect.
                    try {
                        val sessions = api.listSessions(currentConn)
                        eventReducer.setSessions(server.id, sessions)
                        Log.i(TAG, "[${server.displayName}] Pre-loaded ${sessions.size} sessions")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "[${server.displayName}] Failed to pre-load sessions: ${e.message}")
                    }
                }

                try {
                    sseClient.connectToGlobalEvents(
                        conn = currentConn,
                        onOpen = {
                            if (connections[server.id]?.sseJob === currentJob) {
                                updateServerConnected(server.id, true, currentJob)
                                attempt = 0
                                failureStartedAt = null
                            }
                        },
                    )
                        .catch { error ->
                            Log.e(TAG, "[${server.displayName}] SSE stream error", error)
                            updateServerConnected(server.id, false, currentJob)
                            throw error
                        }
                        .collect { scoped ->
                            if (connections[server.id]?.sseJob !== currentJob) return@collect
                            val event = scoped.event
                            if (connections[server.id]?.isConnected != true) {
                                updateServerConnected(server.id, true, currentJob)
                                attempt = 0
                                failureStartedAt = null
                            }
                            processEvent(server, event, scoped.directory, scoped.workspaceId)
                            if (event is SseEvent.ServerConnected) {
                                startReconciliation(server, conn)
                            }
                        }

                    // Flow completed normally (server closed connection)
                    Log.w(TAG, "[${server.displayName}] SSE stream completed")
                    updateServerConnected(server.id, false, currentJob)
                } catch (e: CancellationException) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "[${server.displayName}] SSE job cancelled, not reconnecting")
                    throw e
                } catch (e: org.hiylo.starburst.data.api.SseAuthException) {
                    Log.e(TAG, "[${server.displayName}] Authentication failed; automatic reconnect stopped", e)
                    _connectionErrors.update { it + (server.id to getString(R.string.home_server_auth_failed)) }
                    updateServerConnected(server.id, false, currentJob)
                    cleanupTerminatedConnection(server.id, currentJob)
                    break
                } catch (e: org.hiylo.starburst.data.api.SseConnectionException) {
                    Log.e(TAG, "[${server.displayName}] SSE connection failed: ${e.message}")
                    updateServerConnected(server.id, false, currentJob)
                    if (!e.retryable) {
                        _connectionErrors.update { it + (server.id to getString(R.string.home_server_not_responding)) }
                        cleanupTerminatedConnection(server.id, currentJob)
                        break
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[${server.displayName}] SSE connection failed: ${e.message}")
                    updateServerConnected(server.id, false, currentJob)
                }

                // If this server was removed from connections, stop the loop
                if (!connections.containsKey(server.id)) break

                val now = SystemClock.elapsedRealtime()
                val failedSince = failureStartedAt ?: now.also { failureStartedAt = it }
                val delayMs = calculateBackoff(attempt)
                if (attempt <= 3 || attempt % 10 == 0) {
                    Log.i(TAG, "[${server.displayName}] Reconnecting in ${delayMs}ms (attempt #$attempt)")
                } else if (BuildConfig.DEBUG) {
                    Log.d(TAG, "[${server.displayName}] Reconnecting in ${delayMs}ms (attempt #$attempt)")
                }
                val remainingMs = FAILED_CONNECTION_TIMEOUT_MS - (now - failedSince)
                delay(minOf(delayMs, remainingMs.coerceAtLeast(1L)))
            }
        }
    }

    private fun startReconciliation(server: ServerConfig, conn: ServerConnection) {
        val job = serviceScope.launch { reconcileServerState(server, conn) }
        reconciliationJobs.put(server.id, job)?.cancel()
        job.invokeOnCompletion { reconciliationJobs.remove(server.id, job) }
    }

    private suspend fun reconcileServerState(server: ServerConfig, conn: ServerConnection) {
        val revision = eventReducer.pendingSnapshotRevision()
        val permissions = mutableListOf<SseEvent.PermissionAsked>()
        val questions = mutableListOf<SseEvent.QuestionAsked>()
        var complete = true
        try {
            val localSessions = eventReducer.sessions.value.associateBy { it.id }
            val sessions = api.listSessions(conn)
            val changedSessions = sessionsNeedingMessageReconciliation(localSessions, sessions)
            eventReducer.setSessions(server.id, sessions)
            changedSessions.forEach { session ->
                try {
                    eventReducer.mergeMessages(
                        session.id,
                        api.listMessages(conn, session.id, limit = 50, directory = session.directory),
                        server.id,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "[${server.displayName}] Message reconciliation failed for ${session.id}", e)
                }
            }
            val serverSessionIds = eventReducer.serverSessions.value[server.id].orEmpty()
            val sessionIds = eventReducer.sessions.value.asSequence()
                .filter { it.id in serverSessionIds }
                .map { it.id }
                .toSet()
            // /session/status、/permission、/question 都按 query 参数 directory 过滤（不认 header），
            // 且 /session/status 不支持无 directory 全量查询，必须按会话目录分组聚合。
            val directories = sessions.asSequence()
                .map { it.directory }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()
            val statuses = api.listSessionStatusesForDirectories(conn, directories)
            // 重连对账是完整的状态同步：/session/status 快照是服务端当前真实状态，
            // 省略的会话即为已空闲，必须纠正掉断线期间残留的 Busy（connected=false 语义）。
            eventReducer.replaceSessionStatuses(server.id, sessionIds, statuses, connected = false)
            for (dir in directories) {
                try {
                    permissions += api.listPendingPermissions(conn, directory = dir).map { request ->
                        SseEvent.PermissionAsked(
                            id = request.id,
                            sessionId = request.sessionId,
                            permission = request.permission,
                            patterns = request.patterns,
                            always = request.always,
                            metadata = request.metadata,
                            tool = request.tool,
                        )
                    }
                    questions += api.listPendingQuestions(conn, directory = dir).map { request ->
                        SseEvent.QuestionAsked(
                            id = request.id,
                            sessionId = request.sessionId,
                            questions = request.questions.map { question ->
                                SseEvent.QuestionAsked.Question(
                                    header = question.header,
                                    question = question.question,
                                    multiple = question.multiple,
                                    custom = question.custom,
                                    options = question.options.map { option ->
                                        SseEvent.QuestionAsked.Option(option.label, option.description)
                                    },
                                )
                            },
                            tool = request.tool,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "[${server.displayName}] Pending reconciliation failed for directory $dir", e)
                    complete = false
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            complete = false
            Log.w(TAG, "[${server.displayName}] Reconciliation failed", e)
        }
        if (complete) {
            eventReducer.replacePendingRequests(server.id, permissions, questions, revision)
        }
    }

    @Synchronized
    private fun cleanupTerminatedConnection(serverId: String, job: Job) {
        val state = connections[serverId] ?: return
        if (state.sseJob !== job || !connections.remove(serverId, state)) return
        state.pushJob?.cancel()
        closeSshSession(state.sshSession)
        reconciliationJobs.remove(serverId)?.cancel()

        _connectedServerIds.update { it - serverId }
        _connectingServerIds.update { it - serverId }
        eventReducer.clearTransientForServer(serverId)
        clearServerMetrics(serverId)

        if (connections.isEmpty()) {
            stopServiceIfIdle()
        } else {
            updatePersistentNotification()
        }
    }

    private fun updateServerConnected(serverId: String, connected: Boolean, expectedJob: Job) {
        var changed = false
        connections.computeIfPresent(serverId) { _, state ->
            if (state.sseJob !== expectedJob) return@computeIfPresent state
            if (state.isConnected == connected) {
                state
            } else {
                changed = true
                state.copy(isConnected = connected)
            }
        }
        if (!changed) return
        if (connected) {
            _connectingServerIds.update { it - serverId }
            _connectedServerIds.update { it + serverId }
            val latencyMs = connectStartedAt.remove(serverId)?.let { SystemClock.elapsedRealtime() - it }
            recordServerHeartbeat(serverId)
            if (latencyMs != null) recordServerLatency(serverId, latencyMs)
        } else {
            _connectedServerIds.update { it - serverId }
            _connectingServerIds.update { it + serverId }
        }
        updatePersistentNotification()
    }

    private fun recordServerHeartbeat(serverId: String) {
        _serverMetrics.update { current ->
            val existing = current[serverId] ?: ServerConnectionMetrics()
            current + (serverId to existing.copy(lastHeartbeatAt = System.currentTimeMillis()))
        }
    }

    private fun recordServerLatency(serverId: String, latencyMs: Long) {
        _serverMetrics.update { current ->
            val existing = current[serverId] ?: ServerConnectionMetrics()
            current + (serverId to existing.copy(latencyMs = latencyMs))
        }
    }

    private fun clearServerMetrics(serverId: String) {
        _serverMetrics.update { it - serverId }
        connectStartedAt.remove(serverId)
    }

    private fun calculateBackoff(attempt: Int): Long {
        val maxDelay = when (reconnectMode) {
            "aggressive" -> 5_000L
            "conservative" -> 60_000L
            else -> RECONNECT_MAX_DELAY_MS // normal: 30s
        }
        val exponential = (RECONNECT_BASE_DELAY_MS * Math.pow(RECONNECT_BACKOFF_FACTOR, (attempt - 1).coerceAtLeast(0).toDouble())).toLong()
        val capped = exponential.coerceAtMost(maxDelay)
        // Randomize by ±25% so multiple servers on the same network don't reconnect in a synchronized "thundering herd".
        val jitterFactor = 0.75 + Random.nextDouble() * 0.5
        return (capped * jitterFactor).toLong().coerceAtLeast(1L)
    }

    // ============ Event Processing ============

    /**
     * Check if a session is a child/sub-agent session (has parentID set).
     * Child sessions should not trigger user-facing notifications,
     * matching the behavior of the official opencode WebUI and TUI.
     */
    private fun isChildSession(sessionId: String): Boolean {
        val session = eventReducer.sessions.value.find { it.id == sessionId }
        return session?.parentId != null
    }

    private fun processEvent(server: ServerConfig, event: SseEvent, directory: String?, workspaceId: String?) {
        eventReducer.processEvent(event, server.id, directory, workspaceId)
        recordServerHeartbeat(server.id)

        when (event) {
            is SseEvent.SessionIdle -> {
                if (isChildSession(event.sessionId)) {
                    Log.i(TAG, "[${server.displayName}] Session idle for child session, skip notification: ${event.sessionId}")
                    return
                }
                serviceScope.launch {
                    // Give reducer a brief moment to receive trailing message/part events.
                    delay(250)
                    notifySessionComplete(server, event.sessionId)
                }
            }
            is SseEvent.PermissionAsked -> {
                if (isChildSession(event.sessionId)) return
                Log.i(TAG, "[${server.displayName}] Permission asked: ${event.permission}")
                showPermissionNotification(server, event.sessionId, event.permission)
            }
            is SseEvent.QuestionAsked -> {
                if (isChildSession(event.sessionId)) return
                Log.i(TAG, "[${server.displayName}] Question asked for session ${event.sessionId}")
                val questionText = event.questions.firstOrNull()?.question ?: getString(R.string.notification_has_question, getString(R.string.notification_new_session))
                showQuestionNotification(server, event.sessionId, questionText)
            }
            is SseEvent.SessionError -> {
                if (event.sessionId != null && isChildSession(event.sessionId)) return
                Log.i(TAG, "[${server.displayName}] Session error: ${event.error.message}")
                showErrorNotification(server, event.sessionId, event.error.message)
            }
            else -> { }
        }
    }

    // ============ Helpers ============

    /**
     * 会话完成后推送「回复就绪」通知（供 SSE session.idle 与轮询兜底共用）。
     * 内部做通知开关检查 + 按最后一条 assistant 消息去重，避免 SSE 与轮询重复推送。
     */
    private suspend fun notifySessionComplete(server: ServerConfig, sessionId: String) {
        if (!settingsRepository.notificationsEnabled.first()) {
            Log.i(TAG, "[${server.displayName}] Session idle but notifications disabled: $sessionId")
            return
        }
        if (!connections.containsKey(server.id)) {
            Log.i(TAG, "[${server.displayName}] Session idle but server disconnected, skip: $sessionId")
            return
        }
        val assistantMessageId = latestNotifiableAssistantMessageId(sessionId)
        if (assistantMessageId == null) {
            Log.i(TAG, "[${server.displayName}] Skip response-ready: no notifiable assistant message ($sessionId)")
            return
        }
        val previousNotified = lastNotifiedAssistantMessageBySession.put(sessionId, assistantMessageId)
        if (previousNotified == assistantMessageId) {
            Log.i(TAG, "[${server.displayName}] Skip duplicate response-ready ($sessionId, msg=$assistantMessageId)")
            return
        }
        Log.i(TAG, "[${server.displayName}] Session idle -> Response ready for $sessionId")
        showTaskCompleteNotification(server, sessionId)
    }

    private fun getServerConnection(server: ServerConfig): ServerConnection? {
        return connections[server.id]?.conn
    }

    /**
     * 双通道网关：服务器配置了 starburst-backend 且后端存活时，把连接切到
     * `${backendUrl}/api/opencode` 镜像（Bearer 后端 token）；否则原样直连 opencode。
     * 探测有 2.5s 总超时；主线程或后端不可达时安全回退到直连。
     */
    private fun buildGatewayConn(server: ServerConfig, baseConn: ServerConnection, backendLocalPort: Int?): ServerConnection {
        val backendUrl = resolveBackendUrl(server, backendLocalPort).trim().trimEnd('/').takeIf { it.isNotBlank() } ?: return baseConn
        val backendToken = server.backendResolvedToken.trim().takeIf { it.isNotBlank() } ?: return baseConn
        if (Looper.myLooper() == Looper.getMainLooper()) return baseConn
        return runCatching {
            runBlocking {
                withTimeoutOrNull(2_500L) {
                    if (!backendApi.isHealthy(backendUrl)) return@withTimeoutOrNull baseConn
                    val info = backendApi.getSystemInfo(backendUrl, backendToken)
                    OpenCodeGateway.resolve(
                        baseConn,
                        BackendStatus(
                            backendUrl = backendUrl,
                            backendToken = backendToken,
                            backendAvailable = true,
                            backendVersion = info?.version,
                        ),
                    )
                } ?: baseConn
            }
        }.getOrDefault(baseConn)
    }

    /**
     * 解析后端镜像地址：SSH 隧道模式下用隧道内后端本地端口（否则 127.0.0.1:18880 不可达），
     * 非 SSH 模式优先显式 [server.backendUrl]，否则推导为 opencode 同主机 18880。
     */
    private fun resolveBackendUrl(server: ServerConfig, backendLocalPort: Int?): String {
        if (server.useSsh) {
            backendLocalPort?.let { return "http://127.0.0.1:$it" }
            // 没有显式 backendUrl 时不推导（隧道未转发后端端口，18880 不可达）。
            if (server.backendUrl.isNullOrBlank()) return ""
        }
        return server.backendResolvedUrl
    }

    private fun getSessionInfo(sessionId: String): Pair<String?, String?> {
        val session = eventReducer.sessions.value.find { it.id == sessionId }
        return Pair(session?.title, session?.directory)
    }

    /**
     * 需求 2 后端主动推送：当连接已切到后端镜像（后端可用）时，订阅后端 `/api/ws` 的
     * `session.event`，把会话完成 / 提问 / 出错 / 授权事件转成带声音震动的通知。
     * 断线指数退避重连；服务端断开该 server 后自动退出。
     */
    private fun startBackendPushJob(server: ServerConfig, conn: ServerConnection, backendLocalPort: Int?): Job? {
        val backendUrl = resolveBackendUrl(server, backendLocalPort).trim().trimEnd('/').takeIf { it.isNotBlank() } ?: return null
        if (!conn.baseUrl.startsWith("$backendUrl${OpenCodeGateway.BACKEND_API_PREFIX}")) return null
        val token = server.backendResolvedToken.trim().takeIf { it.isNotBlank() } ?: return null
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "[${server.displayName}] Backend gateway active, listening /api/ws pushes")
        }
        return serviceScope.launch {
            var backoffMs = 2_000L
            var consecutiveFailures = 0
            while (isActive) {
                if (!connections.containsKey(server.id)) return@launch
                try {
                    backendPushListener.eventFlow(backendUrl, token).collect { ev ->
                        handleBackendPushEvent(server, ev)
                    }
                    // 正常断开（收集器结束/WS 被网关按 idle 掐断）不算故障：
                    // 重置失败计数，避免健康后端仅因空闲断流被误判回退直连。
                    consecutiveFailures = 0
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    consecutiveFailures++
                    Log.w(TAG, "[${server.displayName}] Backend push stream stopped: ${e.message}")
                }
                // 后端连续不可用：回退到直连，保证聊天与会话列表仍然可用。
                if (consecutiveFailures >= BACKEND_FALLBACK_THRESHOLD) {
                    Log.w(TAG, "[${server.displayName}] Backend push failing repeatedly, falling back to direct opencode")
                    fallbackToDirectConn(server)
                    return@launch
                }
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            }
        }
    }

    /** 后端不可用时把该 server 的连接切回直连 opencode（重建 SSE；push 镜像关闭）。 */
    private fun fallbackToDirectConn(server: ServerConfig) {
        val state = connections[server.id] ?: return
        val directConn = state.directConn ?: return
        if (state.conn === directConn) return
        val job = startSseConnection(state.config, directConn, preload = false)
        val replacement = state.copy(conn = directConn, sseJob = job, isConnected = false, pushJob = null)
        if (!connections.replace(server.id, state, replacement)) {
            job.cancel()
            return
        }
        state.sseJob.cancel()
        reconciliationJobs.remove(server.id)?.cancel()
        _connectedServerIds.update { it - server.id }
        _connectingServerIds.update { it + server.id }
        connectStartedAt[server.id] = SystemClock.elapsedRealtime()
        _serverMetrics.update { it - server.id }
        job.start()
        if (BuildConfig.DEBUG) Log.d(TAG, "[${server.displayName}] Fell back to direct opencode")
    }

    private fun handleBackendPushEvent(server: ServerConfig, ev: PushSessionEvent) {
        when (ev.eventType) {
            "session.idle" -> {
                if (isChildSession(ev.sessionId)) return
                // 状态准确：推送驱动，把该会话立即置为空闲（不依赖 SSE/轮询）。
                eventReducer.updateSessionStatus(ev.sessionId, SessionStatus.Idle)
                serviceScope.launch {
                    delay(250)
                    notifySessionComplete(server, ev.sessionId)
                }
            }
            "session.status", "session.updated" -> {
                if (ev.sessionId.isNotBlank() && isChildSession(ev.sessionId)) return
                // 状态准确：以推送事件的 status 为准（/session/status 快照可能不全），
                // 立即写入 eventReducer，聊天与会话列表实时反映。
                val status = ev.status()
                if (status != null) {
                    eventReducer.updateSessionStatus(ev.sessionId, status)
                }
                refreshSessionStatusesSoon(server)
            }
            "question.asked", "question.updated" -> {
                if (isChildSession(ev.sessionId)) return
                val questionText = ev.questionText()
                    ?: getString(R.string.notification_has_question, getString(R.string.notification_new_session))
                showQuestionNotification(server, ev.sessionId, questionText)
            }
            "question.replied", "question.rejected" -> {
                // web 端用 opencode 通道选中/拒绝问题后，App 走推送通道也要同步清除 pending，
                // 否则会话列表/工作台一直挂着「待回答问题」无法取消。
                if (ev.sessionId.isNotBlank() && isChildSession(ev.sessionId)) return
                val requestId = ev.questionId().orEmpty()
                if (requestId.isBlank()) {
                    // payload 缺失请求 id 时按会话兜底清空，避免题永久滞留。
                    eventReducer.clearPendingForSession(ev.sessionId)
                } else {
                    eventReducer.removeQuestion(ev.sessionId, requestId)
                }
            }
            "permission.asked", "permission.updated" -> {
                if (isChildSession(ev.sessionId)) return
                val permission = ev.permission() ?: return
                showPermissionNotification(server, ev.sessionId, permission)
            }
            "permission.replied", "permission.denied", "permission.granted" -> {
                // web 端已授权/拒绝后，App 走推送通道同步清除 pending 授权，避免一直挂着无法取消。
                if (ev.sessionId.isNotBlank() && isChildSession(ev.sessionId)) return
                val requestId = ev.permissionId().orEmpty()
                if (requestId.isBlank()) {
                    eventReducer.clearPendingForSession(ev.sessionId)
                } else {
                    eventReducer.removePermission(ev.sessionId, requestId)
                }
            }
            "session.error", "session.failed" -> {
                if (ev.sessionId.isNotBlank() && isChildSession(ev.sessionId)) return
                showErrorNotification(server, ev.sessionId.ifBlank { null }, ev.errorMessage()
                    ?: getString(R.string.error_unknown))
            }
            else -> {}
        }
    }

    /** 状态类推送的 2s 去抖：合并突发事件，避免频繁全量拉状态。 */
    private val lastStatusRefreshAtByServer = ConcurrentHashMap<String, Long>()
    private fun refreshSessionStatusesSoon(server: ServerConfig) {
        val now = System.currentTimeMillis()
        val last = lastStatusRefreshAtByServer[server.id] ?: 0L
        if (now - last < 2_000L) return
        lastStatusRefreshAtByServer[server.id] = now
        serviceScope.launch { refreshSessionStatuses(server) }
    }

    /** 从权威接口拉一次全量状态并写入 eventReducer（busy/retry 立即反映；其余由 SSE/轮询兜底）。 */
    private suspend fun refreshSessionStatuses(server: ServerConfig) {
        val state = connections[server.id] ?: return
        try {
            val serverSessionIds = eventReducer.serverSessions.value[server.id].orEmpty()
            val directories = eventReducer.sessions.value.asSequence()
                .filter { it.id in serverSessionIds }
                .map { it.directory }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()
            val statuses = api.listSessionStatusesForDirectories(state.conn, directories)
            statuses.forEach { (sessionId, status) ->
                eventReducer.updateSessionStatus(sessionId, status)
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "[${server.displayName}] Push-triggered status refresh: ${statuses.size} active sessions")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "[${server.displayName}] Status refresh failed: ${e.message}")
        }
    }

    private fun latestNotifiableAssistantMessageId(sessionId: String): String? {
        val sessionMessages = eventReducer.messages.value[sessionId] ?: return null
        val latestAssistant = sessionMessages
            .asReversed()
            .firstOrNull { it is Message.Assistant } as? Message.Assistant ?: return null

        if (!latestAssistant.error?.message.isNullOrBlank()) return latestAssistant.id

        val parts = eventReducer.parts.value[latestAssistant.id] ?: return null
        val hasTextOutput = parts.any { part ->
            when (part) {
                is Part.Text -> part.text.isNotBlank()
                is Part.Reasoning -> part.text.isNotBlank()
                else -> false
            }
        }
        return if (hasTextOutput) latestAssistant.id else null
    }

    /**
     * 取会话最后一条 assistant 消息的文本摘要（仅 Part.Text），用于通知正文。
     * 截断到 [NOTIFICATION_SUMMARY_MAX_CHARS] 字；无文本时返回 null。
     */
    private fun buildAssistantMessageSummary(sessionId: String): String? {
        val sessionMessages = eventReducer.messages.value[sessionId] ?: return null
        val latestAssistant = sessionMessages
            .asReversed()
            .firstOrNull { it is Message.Assistant } as? Message.Assistant ?: return null
        val parts = eventReducer.parts.value[latestAssistant.id] ?: return null
        val text = parts.filterIsInstance<Part.Text>()
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        if (text.isEmpty()) return null
        return text.take(NOTIFICATION_SUMMARY_MAX_CHARS)
    }

    private fun getProjectName(directory: String?): String? {
        if (directory.isNullOrBlank()) return null
        return directory.trimEnd('/').substringAfterLast('/')
    }

    private fun base64UrlEncode(value: String): String {
        val encoded = android.util.Base64.encodeToString(
            value.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        return encoded
            .replace('+', '-')
            .replace('/', '_')
            .replace("=", "")
    }

    private fun buildSessionPath(sessionId: String): String? {
        val session = eventReducer.sessions.value.find { it.id == sessionId }
        if (session == null) {
            Log.w(TAG, "buildSessionPath: session $sessionId not found")
            return null
        }
        val encodedDir = base64UrlEncode(session.directory)
        return "/$encodedDir/session/$sessionId"
    }

    private fun createSessionPendingIntent(server: ServerConfig, sessionId: String?, requestCode: Int): PendingIntent {
        val sessionPath = sessionId?.let { buildSessionPath(it) }

        val intent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_SESSION
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_SERVER_URL, server.url)
            putExtra(EXTRA_SERVER_USERNAME, server.username)
            putExtra(EXTRA_SERVER_PASSWORD, server.password ?: "")
            putExtra(EXTRA_SERVER_NAME, server.displayName)
            putExtra(EXTRA_SERVER_ID, server.id)
            sessionPath?.let { putExtra(EXTRA_SESSION_PATH, it) }
            sessionId?.let { putExtra(EXTRA_SESSION_ID, it) }
        }

        return PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * 构造「重试」按钮的广播 PendingIntent：携带 serverId/sessionId，由后续的
     * BroadcastReceiver 监听 [ACTION_RETRY_SESSION] 触发对应会话重试。
     */
    private fun createRetrySessionPendingIntent(
        server: ServerConfig,
        sessionId: String?,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(ACTION_RETRY_SESSION).apply {
            setPackage(packageName)
            putExtra(EXTRA_SERVER_ID, server.id)
            sessionId?.let { putExtra(EXTRA_SESSION_ID, it) }
        }
        return PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        const val ACTION_OPEN_SESSION = "org.hiylo.starburst.OPEN_SESSION"
        const val ACTION_DISCONNECT = "org.hiylo.starburst.DISCONNECT"
        const val ACTION_EXIT = "org.hiylo.starburst.EXIT"
        const val ACTION_APP_EXIT = "org.hiylo.starburst.APP_EXIT"
        /** 通知「重试」按钮触发的广播 action，由后续的 BroadcastReceiver 负责实际重试逻辑。 */
        const val ACTION_RETRY_SESSION = "org.hiylo.starburst.RETRY_SESSION"
        const val EXTRA_SERVER_URL = "server_url"
        const val EXTRA_SERVER_USERNAME = "server_username"
        const val EXTRA_SERVER_PASSWORD = "server_password"
        const val EXTRA_SERVER_NAME = "server_name"
        const val EXTRA_SERVER_ID = "server_id"
        const val EXTRA_SESSION_PATH = "session_path"
        const val EXTRA_SESSION_ID = "sessionId"
    }

    // ============ Notification Channels ============

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // The tasks channel previously shipped without an explicit sound. Android never
            // updates an already-created channel's sound settings, so force it by deleting
            // and recreating the channel once on this upgrade.
            if (BuildConfig.VERSION_CODE >= 1 && notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_TASKS_ID) != null) {
                notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_TASKS_ID)
            }
            // 权限/提问通知此前无声（MIUI 上 HIGH channel 不 setSound 即静音），重建一次以生效。
            if (BuildConfig.VERSION_CODE >= 1 && notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_PERMISSIONS_ID) != null) {
                notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_PERMISSIONS_ID)
            }

            val connectionChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_connection),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_connection_desc)
                setShowBadge(false)
            }

            val tasksChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_TASKS_ID,
                getString(R.string.notification_channel_tasks),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_tasks_desc)
                setShowBadge(true)
                enableVibration(true)
                enableLights(true)
                // Explicitly use the system default notification sound. On some devices
                // (MIUI etc.) a HIGH channel without setSound still fires silently.
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }

            val tasksSilentChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_TASKS_SILENT_ID,
                getString(R.string.notification_channel_tasks_silent),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_tasks_silent_desc)
                setShowBadge(true)
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
            }

            val permissionsChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_PERMISSIONS_ID,
                getString(R.string.notification_channel_permissions),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_permissions_desc)
                setShowBadge(true)
                enableVibration(true)
                enableLights(true)
                // 同 tasks channel：显式默认通知铃声，避免 MIUI 等系统静音。
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }

            notificationManager.createNotificationChannel(connectionChannel)
            notificationManager.createNotificationChannel(tasksChannel)
            notificationManager.createNotificationChannel(tasksSilentChannel)
            notificationManager.createNotificationChannel(permissionsChannel)
        }
    }

    // ============ Persistent Notification (InboxStyle, multi-server) ============

    private fun createPersistentNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val tapPendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val exitIntent = Intent(this, StarBurstConnectionService::class.java).apply {
            action = ACTION_EXIT
        }
        val exitPendingIntent = PendingIntent.getService(
            this, 1, exitIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val visibleConnections = connections.values.sortedBy { it.config.id }
        val serverCount = visibleConnections.size
        val connectedCount = visibleConnections.count { it.isConnected }

        val title = if (serverCount == 0) {
            getString(R.string.app_name)
        } else if (serverCount == 1) {
            val server = visibleConnections.first()
            if (server.isConnected) getString(R.string.notification_connected, server.config.displayName)
            else getString(R.string.notification_connecting, server.config.displayName)
        } else {
            getString(R.string.notification_connected_count, connectedCount, serverCount)
        }

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(title)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(tapPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (serverCount > 0) {
            builder.addAction(
                R.mipmap.ic_launcher,
                getString(R.string.notification_exit),
                exitPendingIntent,
            )
        }

        // InboxStyle when multiple servers
        if (serverCount > 1) {
            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle(getString(R.string.notification_inbox_title, connectedCount, serverCount))
            for (state in visibleConnections) {
                val status = if (state.isConnected) getString(R.string.notification_status_connected) else getString(R.string.notification_status_connecting)
                inboxStyle.addLine("${state.config.displayName}: $status")
            }
            builder.setStyle(inboxStyle)
        }

        return builder.build()
    }

    @Synchronized
    private fun updatePersistentNotification() {
        if (connections.isEmpty()) {
            if (foregroundStarted) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                foregroundStarted = false
            }
            notificationManager.cancel(PERSISTENT_NOTIFICATION_ID)
            lastPersistentNotificationState = null
            return
        }
        val state = connections.values
            .map { Triple(it.config.id, it.config.displayName, it.isConnected) }
            .sortedBy { it.first }
        if (state == lastPersistentNotificationState) return
        lastPersistentNotificationState = state
        val notification = createPersistentNotification()
        notificationManager.notify(PERSISTENT_NOTIFICATION_ID, notification)
    }

    // ============ Event Notifications (grouped by server) ============

    private suspend fun showTaskCompleteNotification(server: ServerConfig, sessionId: String) {
        val (sessionTitle, _) = getSessionInfo(sessionId)
        // 标题用会话名，正文用最后一条 assistant 消息的结果摘要；摘要为空时回退到默认文案。
        val title = sessionTitle?.takeIf { it.isNotBlank() } ?: getString(R.string.notification_new_session)
        val body = buildAssistantMessageSummary(sessionId) ?: getString(R.string.notification_new_session)

        val pendingIntent = createSessionPendingIntent(server, sessionId, sessionId.hashCode())

        val silent = settingsRepository.silentNotifications.first()
        val channelId = if (silent) NOTIFICATION_CHANNEL_TASKS_SILENT_ID else NOTIFICATION_CHANNEL_TASKS_ID

        val notifId = eventNotificationId(server.id, sessionId, 0)
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSubText(server.displayName)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(if (silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setGroup("server_${server.id}")
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        builder.addAction(android.R.drawable.ic_menu_view, getString(R.string.notification_action_view), pendingIntent)

        if (!silent) {
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVibrate(longArrayOf(0, 500, 200, 500))
                // Android 13+ 高优渠道默认横幅（heads-up）；此渠道已配置声音+震动。
                // Android 14 默认拒绝全屏通知（FSI_REQUESTED_BUT_DENIED），故不用 setFullScreenIntent。
        }

        postEventNotification(server, sessionId, notifId, builder.build())
    }

    private fun showPermissionNotification(server: ServerConfig, sessionId: String, permission: String) {
        val (sessionTitle, directory) = getSessionInfo(sessionId)
        val displayTitle = sessionTitle ?: getString(R.string.notification_new_session)
        val projectName = getProjectName(directory)
        val body = if (projectName != null) {
            getString(R.string.notification_needs_permission_project, displayTitle, projectName)
        } else {
            getString(R.string.notification_needs_permission, displayTitle)
        }

        val notifId = eventNotificationId(server.id, sessionId, 1000)
        val pendingIntent = createSessionPendingIntent(server, sessionId, notifId)

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_PERMISSIONS_ID)
            .setContentTitle(getString(R.string.notification_permission_required))
            .setContentText(body)
            .setSubText(server.displayName)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 300, 100, 300))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroup("server_${server.id}")
            .build()

        postEventNotification(server, sessionId, notifId, notification)
    }

    private fun showQuestionNotification(server: ServerConfig, sessionId: String, questionText: String) {
        val (sessionTitle, directory) = getSessionInfo(sessionId)
        val displayTitle = sessionTitle ?: getString(R.string.notification_new_session)
        val projectName = getProjectName(directory)
        val body = if (projectName != null) {
            getString(R.string.notification_has_question_project, displayTitle, projectName)
        } else {
            getString(R.string.notification_has_question, displayTitle)
        }

        val notifId = eventNotificationId(server.id, sessionId, 2000)
        val pendingIntent = createSessionPendingIntent(server, sessionId, notifId)

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_PERMISSIONS_ID)
            .setContentTitle(getString(R.string.notification_question))
            .setContentText(body)
            .setSubText(server.displayName)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 300, 100, 300))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroup("server_${server.id}")
            .build()

        postEventNotification(server, sessionId, notifId, notification)
    }

    private fun showErrorNotification(server: ServerConfig, sessionId: String?, error: String) {
        // 正文直接显示错误摘要
        val body = error.ifBlank { getString(R.string.error_unknown) }

        val notifId = eventNotificationId(server.id, sessionId ?: "error", 3000)
        val viewPendingIntent = createSessionPendingIntent(server, sessionId, notifId)

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_TASKS_ID)
            .setContentTitle(getString(R.string.notification_session_error))
            .setContentText(body)
            .setSubText(server.displayName)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(viewPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroup("server_${server.id}")

        builder.addAction(android.R.drawable.ic_menu_view, getString(R.string.notification_action_view), viewPendingIntent)
        if (sessionId != null) {
            val retryPendingIntent = createRetrySessionPendingIntent(server, sessionId, notifId + 1)
            builder.addAction(android.R.drawable.ic_menu_revert, getString(R.string.notification_action_retry), retryPendingIntent)
        }

        postEventNotification(server, sessionId, notifId, builder.build())
    }

    private fun postEventNotification(
        server: ServerConfig,
        sessionId: String?,
        notificationId: Int,
        notification: Notification,
    ) {
        // 完整推送：即使正在前台查看该会话也弹通知（含声音震动），不再经 postUnlessActive 抑制。
        val finalNotification = if (isNowInDndWindow()) {
            // 免扰时段内降级为静默（无声无震动），但仍投递通知。
            rebuildSilentNotification(notification)
        } else {
            notification
        }
        notificationManager.notify(notificationId, finalNotification)
        showServerGroupSummary(server)
    }

    /** 当前时间是否落在免扰时段内（跨午夜窗口正确环绕）。 */
    private fun isNowInDndWindow(): Boolean {
        if (!dndEnabledSnapshot) return false
        val now = java.util.Calendar.getInstance()
        val currentMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        val startMinutes = parseHhMmMinutes(dndStartSnapshot)
        val endMinutes = parseHhMmMinutes(dndEndSnapshot)
        if (startMinutes == endMinutes) return true
        return if (startMinutes < endMinutes) {
            currentMinutes in startMinutes until endMinutes
        } else {
            // 跨午夜：start <= now 或 now < end
            currentMinutes >= startMinutes || currentMinutes < endMinutes
        }
    }

    private fun parseHhMmMinutes(value: String): Int {
        val parts = value.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return hour * 60 + minute
    }

    /** 用静默渠道重建通知（保留内容/分组/意图，去掉声音与震动）。 */
    private fun rebuildSilentNotification(original: Notification): Notification {
        val recovered = Notification.Builder.recoverBuilder(this, original)
        recovered.setChannelId(NOTIFICATION_CHANNEL_TASKS_SILENT_ID)
            .setSound(null, null)
            .setVibrate(null)
            .setDefaults(0)
            .setPriority(Notification.PRIORITY_LOW)
        return recovered.build()
    }

    /**
     * Post a group summary notification for a server so Android bundles
     * event notifications from the same server together.
     */
    private fun showServerGroupSummary(server: ServerConfig) {
        val summaryId = serverGroupSummaryNotificationId(server.id)
        val summary = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_TASKS_SILENT_ID)
            .setContentTitle(server.displayName)
            .setContentText(getString(R.string.notification_group_summary))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setGroup("server_${server.id}")
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(summaryId, summary)
    }
}
