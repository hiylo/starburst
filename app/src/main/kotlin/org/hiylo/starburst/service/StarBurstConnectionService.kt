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
import android.net.ConnectivityManager
import android.net.Network
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import org.hiylo.starburst.logging.AppLogger as Log
import androidx.core.app.RemoteInput
import org.hiylo.starburst.BuildConfig
import org.hiylo.starburst.MainActivity
import org.hiylo.starburst.R
import android.os.Handler
import android.os.Looper
import org.hiylo.starburst.data.api.BackendApi
import org.hiylo.starburst.data.api.OpenCodeApi
import org.hiylo.starburst.data.api.ServerConnection
import org.hiylo.starburst.data.api.SseClient
import org.hiylo.starburst.data.backend.BackendPushListener
import org.hiylo.starburst.data.api.listMessages
import org.hiylo.starburst.data.api.listSessionStatusesForDirectories
import org.hiylo.starburst.data.repository.EventReducer
import org.hiylo.starburst.data.repository.PendingPromptRepository
import org.hiylo.starburst.data.repository.ServerRepository
import org.hiylo.starburst.data.repository.normalizeServerUrl
import org.hiylo.starburst.data.repository.ServerConnectionStateRepository
import org.hiylo.starburst.data.repository.SettingsRepository
import org.hiylo.starburst.data.repository.AlertHistoryRepository
import org.hiylo.starburst.domain.model.ServerConfig
import org.hiylo.starburst.domain.model.Session
import org.hiylo.starburst.domain.model.SessionStatus
import org.hiylo.starburst.domain.model.SseEvent
import com.jcraft.jsch.Session as JschSession
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

internal const val TAG = "StarBurstService"
internal const val NOTIFICATION_CHANNEL_ID = "starburst_connection"
internal const val NOTIFICATION_CHANNEL_TASKS_ID = "starburst_tasks"
internal const val NOTIFICATION_CHANNEL_TASKS_SILENT_ID = "starburst_tasks_silent"
internal const val NOTIFICATION_CHANNEL_PERMISSIONS_ID = "starburst_permissions"
internal const val PERSISTENT_NOTIFICATION_ID = 1001
private const val WAKELOCK_TAG = "StarBurst::SSEConnection"

// Reconnect timing
internal const val RECONNECT_BASE_DELAY_MS = 1_000L   // 1 second
internal const val RECONNECT_MAX_DELAY_MS = 30_000L   // 30 seconds
internal const val RECONNECT_BACKOFF_FACTOR = 2.0
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
internal const val BACKEND_FALLBACK_THRESHOLD = 3
/** 通知正文结果摘要的最大字符数（约 80 字）。 */
internal const val NOTIFICATION_SUMMARY_MAX_CHARS = 80

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
internal data class ServerConnectionState(
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
    internal lateinit var api: OpenCodeApi

    @Inject
    lateinit var sseClient: SseClient

    @Inject
    internal lateinit var eventReducer: EventReducer

    @Inject
    internal lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var serverRepository: ServerRepository

    @Inject
    lateinit var serverConnectionStateRepository: ServerConnectionStateRepository

    @Inject
    internal lateinit var pendingPromptRepository: PendingPromptRepository

    @Inject
    lateinit var backendApi: BackendApi

    @Inject
    lateinit var backendPushListener: BackendPushListener

    @Inject
    internal lateinit var alertHistoryRepository: AlertHistoryRepository

    private val binder = LocalBinder()
    internal val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
    internal val connections = ConcurrentHashMap<String, ServerConnectionState>()

    /** Cached reconnect mode ("aggressive" | "normal" | "conservative"), refreshed from DataStore on start. */
    @Volatile
    internal var reconnectMode: String = "normal"

    /** Cached do-not-disturb snapshot, refreshed from DataStore on start. */
    @Volatile
    internal var dndEnabledSnapshot: Boolean = false

    @Volatile
    internal var dndStartSnapshot: String = "22:00"

    @Volatile
    internal var dndEndSnapshot: String = "07:00"

    private var autoConnectJob: Job? = null
    @Volatile
    private var recoveryJob: Job? = null
    internal val reconciliationJobs = ConcurrentHashMap<String, Job>()
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
    internal var lastPersistentNotificationState: List<Triple<String, String, Boolean>>? = null

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
                recoverConnectionsAfterNetworkChange("default network available")
            }
        }

        override fun onLost(network: Network) {
            val wasDefault = lastDefaultNetwork == network
            if (wasDefault) {
                lastDefaultNetwork = null
            }
            // WiFi 断开→流量/VPN 接管属于网络拓扑变化，会物理打断既有 socket。
            // 不能只在 onLost 里等下一个 onAvailable（VPN 重绑可能不触发可用回调），
            // 也不能依赖后台保活关闭才有的恢复——网络变更与 Doze 是两码事。
            // 这里主动触发一次（带 debounce+去重，短暂抖动只会合并成一次重建）。
            if (wasDefault) {
                recoverConnectionsAfterNetworkChange("default network lost")
            }
        }
    }

    internal lateinit var notificationManager: NotificationManager
    @Volatile
    internal var foregroundStarted: Boolean = false

    /** Observable set of server IDs that are actually connected (SSE stream active). */
    internal val _connectedServerIds = MutableStateFlow<Set<String>>(emptySet())
    val connectedServerIds: StateFlow<Set<String>> = _connectedServerIds.asStateFlow()

    /** Observable set of server IDs that are attempting to connect (SSE not yet established or reconnecting). */
    internal val _connectingServerIds = MutableStateFlow<Set<String>>(emptySet())
    val connectingServerIds: StateFlow<Set<String>> = _connectingServerIds.asStateFlow()

    /** Per-server connection errors surfaced to the UI (e.g. authentication failures during SSE handshake). */
    internal val _connectionErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val connectionErrors: StateFlow<Map<String, String>> = _connectionErrors.asStateFlow()

    /** Per-server connection metrics (latency, last heartbeat) surfaced to the UI. */
    internal val _serverMetrics = MutableStateFlow<Map<String, ServerConnectionMetrics>>(emptyMap())
    val serverMetrics: StateFlow<Map<String, ServerConnectionMetrics>> = _serverMetrics.asStateFlow()

    /** Tracks when each server's latest connect attempt started (monotonic clock) to measure latency. */
    internal val connectStartedAt = ConcurrentHashMap<String, Long>()

    /** Dedup response-ready notifications per session by last assistant message ID. */
    private val lastNotifiedAssistantMessageBySession = ConcurrentHashMap<String, String>()

    /** 上次轮询时各 server 处于 busy 的会话集合，用于检测 busy→idle 完成转场（SSE 假死兜底）。 */
    private val lastBusySessions = ConcurrentHashMap<String, Set<String>>()

    /** 正在执行「后端→直连」回退的 server 集合（并发去重，防止 SSE 循环与推送 job 同时回退互相拆隧道）。 */
    internal val fallbackInFlight = ConcurrentHashMap.newKeySet<String>()

    /** 状态类推送的 2s 去抖：合并突发事件，避免频繁全量拉状态。 */
    internal val lastStatusRefreshAtByServer = ConcurrentHashMap<String, Long>()

    /** 各 server 的 Intel 推送订阅 job（生命周期独立于镜像 session 推送）。 */
    internal val intelPushJobs = ConcurrentHashMap<String, Job>()

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
                    updateWakeLockForActiveSessions()
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
                updateWakeLockForActiveSessions()
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
            ACTION_HANDLE_REPLY -> {
                val serverId = intent.getStringExtra(EXTRA_SERVER_ID)
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                val replyText = intent.getStringExtra(EXTRA_REPLY_TEXT)
                val kind = intent.getStringExtra(EXTRA_REPLY_KIND)
                if (!serverId.isNullOrBlank() && !sessionId.isNullOrBlank() && !replyText.isNullOrBlank()) {
                    ensureForegroundStarted()
                    serviceScope.launch {
                        handleNotificationReply(serverId, sessionId, replyText, kind)
                    }
                } else {
                    Log.w(TAG, "Reply ignored: missing server/session/reply extras")
                }
                return START_NOT_STICKY
            }
        }

        // Read server details from intent and connect. Prefer resolving the full config
        // (including credentials) from the repository by serverId, so passwords are never
        // carried in Intent extras. Fall back to intent extras only for legacy callers.
        val serverId = intent?.getStringExtra("server_id")
        if (serverId != null) {
            serviceScope.launch {
                val config = serverRepository.getServer(serverId) ?: buildConfigFromIntent(intent)
                if (config != null) connect(config)
            }
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

    /** 从 Intent extras 兜底构建 [ServerConfig]（仅供兼容仍传完整参数的旧调用方）。 */
    private fun buildConfigFromIntent(intent: Intent?): ServerConfig? {
        val serverId = intent?.getStringExtra("server_id") ?: return null
        val serverUrl = intent.getStringExtra("server_url") ?: return null
        return ServerConfig(
            id = serverId,
            url = serverUrl,
            username = intent.getStringExtra("server_username") ?: "opencode",
            password = intent.getStringExtra("server_password"),
            name = intent.getStringExtra("server_name"),
            sshPort = intent.getIntExtra("server_ssh_port", 22),
            sshUsername = intent.getStringExtra("server_ssh_username") ?: "",
            sshPassword = intent.getStringExtra("server_ssh_password"),
        )
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
     *
     * 注意：不加 @Synchronized——阻塞的 SSH 建连/网关探测放在 [connectInternal] 的锁外执行，
     * 否则某台服务器 TCP 卡住会长时间持有 this 监视器，导致 disconnect 无法进入（停不下）。
     */
    fun connect(server: ServerConfig) {
        explicitlyDisconnectedServerIds.remove(server.id)
        persistDisconnectedServers()
        connectInternal(server)
    }

    private fun connectInternal(server: ServerConfig) {
        // 快速判定（精确判定在下方锁内二次确认）。
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

        // 阻塞的 SSH 隧道建立（最长 15s+）与网关探测（最长 2.5s）放到锁外，避免卡住其它连接的 connect/disconnect。
        val resolved = try {
            resolveConnection(server)
        } catch (e: Exception) {
            Log.e(TAG, "[${server.displayName}] Failed to resolve connection", e)
            _connectionErrors.update { it + (server.id to (e.message ?: getString(R.string.home_server_not_responding))) }
            return
        }
        val baseConn = ServerConnection.from(resolved.baseUrl, server.username, server.password)
        val conn = buildGatewayConn(server, baseConn, resolved.backendLocalPort)

        // 仅对共享状态变更加锁，保持 connect/disconnect 语义不变。
        val state = synchronized(this) {
            // 建连期间用户可能已 disconnect：此时关闭刚建立的隧道并放弃。
            if (server.id in explicitlyDisconnectedServerIds) {
                closeSshSession(resolved.sshSession)
                return@synchronized null
            }
            var replacedState: ServerConnectionState? = null
            var created: ServerConnectionState? = null
            connections.compute(server.id) { _, existing ->
                if (existing != null && !existing.sseJob.isCompleted) return@compute existing
                replacedState = existing
                val sseJob = startSseConnection(server, conn)
                val pushJob = startBackendPushJob(server, conn, resolved.backendLocalPort)
                startIntelPushJob(server, resolveBackendUrl(server, resolved.backendLocalPort), server.backendResolvedToken)
                // SSE 结束（正常/异常/被 cancel）时取消「本次」push job。闭包捕获创建时的
                // 局部引用而非实时读 connections[server.id]，避免替换 state 后误取消新装的 job。
                val capturedPushJob = pushJob
                sseJob.invokeOnCompletion { capturedPushJob?.cancel() }
                ServerConnectionState(
                    config = server,
                    conn = conn,
                    sseJob = sseJob,
                    isConnected = false,
                    sshSession = resolved.sshSession,
                    pushJob = pushJob,
                    directConn = baseConn,
                    backendLocalPort = resolved.backendLocalPort,
                ).also { created = it }
            }
            // 未创建新连接（已存在活跃连接）：关闭本次刚建立的隧道，避免泄漏。
            if (created == null) {
                closeSshSession(resolved.sshSession)
            } else {
                replacedState?.let {
                    // 替换连接时回收旧推送订阅，避免僵尸 WS 累积导致连接泄漏/重复订阅。
                    it.sseJob.cancel()
                    it.pushJob?.cancel()
                    closeSshSession(it.sshSession)
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "Connecting to configured server")
                ensureForegroundStarted()
                updateWakeLockForActiveSessions()
                // 连接中状态写入也放进锁内，与 disconnect 串行，避免「disconnect 后仍显示 connecting」。
                _connectingServerIds.update { it + server.id }
                _connectionErrors.update { it - server.id }
                connectStartedAt[server.id] = SystemClock.elapsedRealtime()
                _serverMetrics.update { it - server.id }
                updatePersistentNotification()
            }
            created
        }
        if (state == null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Already connected to server ${server.id}, skipping")
            return
        }
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
        intelPushJobs.remove(serverId)?.cancel()
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
    internal fun stopServiceIfIdle() {
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
            intelPushJobs.remove(state.config.id)?.cancel()
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
    internal data class ResolvedConnection(
        val baseUrl: String,
        val sshSession: JschSession?,
        val backendLocalPort: Int? = null,
    )

    /**
     * 解析连接 baseUrl：未配置 SSH 时直连 [ServerConfig.url]；配置了 SSH 时通过
     * JSch 建立 `host:sshPort` 的会话并做本地端口转发，返回 127.0.0.1:localPort。
     * 额外把后端端口（默认 18880）也转发到本地，保证 SSH 隧道模式下后端推送/镜像可用。
     */
    internal fun resolveConnection(server: ServerConfig): ResolvedConnection {
        if (!server.useSsh) return ResolvedConnection(server.url, null)
        val host = server.host
        val openCodePort = server.openCodePort
        var session: JschSession? = null
        return try {
            session = SshRunner.buildSession(server)
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
    internal fun closeSshSession(session: JschSession?) {
        try { session?.disconnect() } catch (_: Exception) { }
    }

    /** 用重建后的 SSH 隧道替换某 server 的连接信息（关闭旧会话），并同步后端本地端口、重启推送 job。
     * [newDirectConn] 指向重建后隧道上的直连 opencode 连接，用于后端镜像失败时回退直连。 */
    internal fun replaceSshSession(serverId: String, newConn: ServerConnection, newSsh: JschSession?, newBackendLocalPort: Int?, newDirectConn: ServerConnection) {
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
                directConn = newDirectConn,
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
     * 按是否有活跃会话（busy/retry）决定 WakeLock 的持有与释放。
     *
     * 耗电优化：旧逻辑在「任一服务器连接」期间无条件持有 WakeLock，导致后台挂机时 CPU 也不睡。
     * 改为仅在存在 busy/retry 会话（真正有任务在跑）时持有、空闲即释放；由 15s 兜底轮询驱动，
     * 状态变化最多延迟一轮（约 15s）生效。用户显式关闭后台保活时始终不持有。
     */
    @Synchronized
    private fun updateWakeLockForActiveSessions() {
        if (!backgroundWakeLockEnabled || connections.isEmpty()) {
            releaseWakeLock()
            return
        }
        val hasActiveSession = eventReducer.sessionStatuses.value.values.any {
            it is SessionStatus.Busy || it is SessionStatus.Retry
        }
        if (hasActiveSession) acquireWakeLock() else releaseWakeLock()
    }

    /**
     * Without a WakeLock Android may suspend a healthy-looking socket during Doze. Replacing each SSE job after
     * wake/network recovery forces a fresh stream and the normal server-state reconciliation.
     */
    @Synchronized
    private fun recoverConnectionsWithoutWakeLock(reason: String) {
        if (backgroundWakeLockEnabled || connections.isEmpty()) return
        scheduleConnectionRecovery(reason)
    }

    /**
     * 网络切换（WiFi→流量/VPN、VPN 重绑等）后的连接恢复。
     *
     * 与 [recoverConnectionsWithoutWakeLock] 不同：网络拓扑变化会物理打断既有 socket，
     * 与 Doze 挂起无关，因此**不受后台保活（WakeLock）开关限制**——否则开着保活时
     * 切网后连接会永久掉线，只能靠用户手动断开重连。由 debounce + recoveryJob 去重，
     * 短暂抖动只会合并成一次重建。
     */
    @Synchronized
    private fun recoverConnectionsAfterNetworkChange(reason: String) {
        if (connections.isEmpty()) return
        scheduleConnectionRecovery(reason)
    }

    private fun scheduleConnectionRecovery(reason: String) {
        if (recoveryJob?.isActive == true) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRecoveryAt < RECOVERY_DEBOUNCE_MS) return
        lastRecoveryAt = now

        // 注意：网络切换场景（recoverConnectionsAfterNetworkChange）即使开着后台保活
        // 也必须重建——拓扑变化会打断 socket，与 Doze 挂起无关。因此这里不再检查
        // backgroundWakeLockEnabled，由调用方各自决定是否进来。
        recoveryJob = serviceScope.launch {
            val states = connections.values.toList()
            if (states.isEmpty()) return@launch
            Log.i(TAG, "Recovering ${states.size} connection(s) after $reason")
            for (state in states) {
                // SSH 隧道服务器重建新鲜隧道，避免新 SSE job 用已失效的旧 127.0.0.1:localPort。
                rebuildConnection(state.config.id, preload = false)
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

    /**
     * 重建某 server 的 SSE 连接，修复「重连竞态导致卡在 connecting」：
     * SSH 隧道服务器在锁外重建**新鲜隧道**，新 SSE job 用它（而不是可能已被并发
     * [replaceSshSession] 关闭的旧 `state.conn` 本地端口，否则 SSE 连到
     * `127.0.0.1:localPort` 会立即 ECONNREFUSED 而永远连不上），再锁内替换连接状态、
     * 回收旧 job/隧道/推送订阅。与 [connectInternal] 的建连路径保持一致。
     */
    private fun rebuildConnection(serverId: String, preload: Boolean = false) {
        val state = connections[serverId] ?: return
        val config = state.config
        // 阻塞的 SSH 隧道建立放到锁外，避免卡住其它连接的 connect/disconnect。
        val resolved = if (config.useSsh) {
            try {
                resolveConnection(config)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "[${config.displayName}] SSH tunnel rebuild failed, reusing existing connection", e)
                null
            }
        } else {
            null
        }
        val baseConn = if (resolved != null) {
            ServerConnection.from(resolved.baseUrl, config.username, config.password)
        } else {
            state.directConn ?: state.conn
        }
        // 网络切换后重跑网关探测：旧镜像（backend mirror）端点可能随路由变化失效，
        // 探测失败会自动回退直连 opencode，避免复用死掉的镜像连接永远连不上。
        val conn = if (config.useSsh) {
            if (resolved != null) buildGatewayConn(config, baseConn, resolved.backendLocalPort) else state.conn
        } else {
            buildGatewayConn(config, baseConn, resolved?.backendLocalPort)
        }

        synchronized(this) {
            val current = connections[serverId] ?: run {
                // 建隧道期间该 server 已被断开：关闭刚建立的隧道避免泄漏。
                resolved?.let { closeSshSession(it.sshSession) }
                return
            }
            val job = startSseConnection(config, conn, preload = preload)
            // 始终新建 pushJob（不复用 current.pushJob）：旧 sseJob 的 invokeOnCompletion 会取消其
            // 创建时捕获的 pushJob（connectInternal 里注册），复用旧 job 会被连带 cancel 且不重启。
            // startBackendPushJob 内部有守卫：非镜像直连时返回 null，无需担心误建僵尸订阅。
            val newPush = startBackendPushJob(config, conn, resolved?.backendLocalPort)
            val capturedPushJob = newPush
            job.invokeOnCompletion { capturedPushJob?.cancel() }
            val replacement = current.copy(
                conn = conn,
                sseJob = job,
                isConnected = false,
                pushJob = newPush,
                sshSession = resolved?.sshSession ?: current.sshSession,
                backendLocalPort = resolved?.backendLocalPort ?: current.backendLocalPort,
                directConn = baseConn,
            )
            if (!connections.replace(serverId, current, replacement)) {
                job.cancel()
                newPush?.cancel()
                resolved?.let { closeSshSession(it.sshSession) }
                return
            }
            // 回收旧连接资源：旧 SSE job、旧推送订阅、旧 SSH 隧道。
            current.sseJob.cancel()
            current.pushJob?.cancel()
            if (resolved != null) closeSshSession(current.sshSession)
            reconciliationJobs.remove(serverId)?.cancel()
            _connectedServerIds.update { it - serverId }
            _connectingServerIds.update { it + serverId }
            connectStartedAt[serverId] = SystemClock.elapsedRealtime()
            _serverMetrics.update { it - serverId }
            job.start()
            updatePersistentNotification()
        }
    }

    /** 强制重建某个 server 的 SSE 连接（用于假死恢复）。 */
    private fun forceReconnect(serverId: String) {
        rebuildConnection(serverId)
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
            // lastBusySessions 必须存 busy + retry 全集：只存 Busy 时，busy→retry 转场
            // 会让 retry 会话从「上次集合」消失，被误判成已 idle 完成并重复推送通知。
            val currentActive = statuses.filterValues { it is SessionStatus.Busy || it is SessionStatus.Retry }.keys
            val previous = lastBusySessions[serverId].orEmpty()
            lastBusySessions[serverId] = currentActive
            // 仍在活动的会话 = busy + retry（/session/status 只返回这两类）；
            // 上次活跃但这次彻底不在 = 已 idle 完成，避免把 busy→retry 误判为完成。
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

    // ============ Event Processing ============
    // SSE 重连循环、connected/connecting 标记与心跳指标见 StarBurstConnectionServiceSseExt.kt。

    internal fun processEvent(server: ServerConfig, event: SseEvent, directory: String?, workspaceId: String?) {
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
    internal suspend fun notifySessionComplete(server: ServerConfig, sessionId: String) {
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

    // getServerConnection / buildGatewayConn / startBackendPushJob / fallbackToDirectConn /
    // handleBackendPushEvent / refreshSessionStatuses* 见 StarBurstConnectionServiceGatewayExt.kt。

    companion object {
        const val ACTION_OPEN_SESSION = "org.hiylo.starburst.OPEN_SESSION"
        const val ACTION_DISCONNECT = "org.hiylo.starburst.DISCONNECT"
        const val ACTION_EXIT = "org.hiylo.starburst.EXIT"
        const val ACTION_APP_EXIT = "org.hiylo.starburst.APP_EXIT"
        /** 通知「重试」按钮触发的广播 action，由后续的 BroadcastReceiver 负责实际重试逻辑。 */
        const val ACTION_RETRY_SESSION = "org.hiylo.starburst.RETRY_SESSION"
        /** 通知内嵌「回复」按钮触发的广播 action，由 [NotificationReplyReceiver] 接收并转发给本服务处理。 */
        const val ACTION_NOTIFICATION_REPLY = "org.hiylo.starburst.NOTIFICATION_REPLY"
        /** 转发到本服务的「处理回复」action。 */
        const val ACTION_HANDLE_REPLY = "org.hiylo.starburst.HANDLE_REPLY"
        /** RemoteInput 结果键。 */
        const val KEY_NOTIFICATION_REPLY = "notification_reply_text"
        /** 回复正文 extra。 */
        const val EXTRA_REPLY_TEXT = "reply_text"
        /** 回复类型 extra（question / completion）。 */
        const val EXTRA_REPLY_KIND = "reply_kind"
        /** 待取消的通知 ID extra。 */
        const val EXTRA_REPLY_NOTIFICATION_ID = "reply_notification_id"
        /** 回复类型常量：回答问题。 */
        const val REPLY_KIND_QUESTION = "question"
        /** 回复类型常量：继续会话。 */
        const val REPLY_KIND_COMPLETION = "completion"
        const val EXTRA_SERVER_URL = "server_url"
        const val EXTRA_SERVER_USERNAME = "server_username"
        const val EXTRA_SERVER_PASSWORD = "server_password"
        const val EXTRA_SERVER_NAME = "server_name"
        const val EXTRA_SERVER_ID = "server_id"
        const val EXTRA_SESSION_PATH = "session_path"
        const val EXTRA_SESSION_ID = "sessionId"
    }

}
