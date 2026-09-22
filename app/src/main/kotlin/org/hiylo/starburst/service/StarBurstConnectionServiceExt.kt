/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : StarBurstConnectionServiceExt.kt
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
import androidx.core.app.RemoteInput
import org.hiylo.starburst.BuildConfig
import org.hiylo.starburst.MainActivity
import org.hiylo.starburst.R
import android.os.Handler
import android.os.Looper
import org.hiylo.starburst.data.api.BackendApi
import org.hiylo.starburst.data.api.BackendStatus
import org.hiylo.starburst.data.api.HardwareAlertEvent
import org.hiylo.starburst.data.api.IntelTestRun
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
import org.hiylo.starburst.data.api.MessageIdGenerator
import org.hiylo.starburst.data.api.PromptPart
import org.hiylo.starburst.data.api.promptAsync
import org.hiylo.starburst.data.api.replyToQuestion
import org.hiylo.starburst.data.repository.EventReducer
import org.hiylo.starburst.data.repository.PendingPromptRecord
import org.hiylo.starburst.data.repository.PendingPromptRepository
import org.hiylo.starburst.data.repository.ServerRepository
import org.hiylo.starburst.data.repository.normalizeServerUrl
import org.hiylo.starburst.data.repository.ServerConnectionStateRepository
import org.hiylo.starburst.data.repository.SettingsRepository
import org.hiylo.starburst.domain.model.Message
import org.hiylo.starburst.domain.model.Part
import org.hiylo.starburst.domain.model.PendingInteraction
import org.hiylo.starburst.domain.model.ServerConfig
import org.hiylo.starburst.domain.model.Session
import org.hiylo.starburst.domain.model.SessionStatus
import org.hiylo.starburst.domain.model.SseEvent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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

private const val NOTIFICATION_CHANNELS_PREFS = "starburst_notification_channels"
private const val NOTIFICATION_CHANNELS_MIGRATED_KEY = "notif_channels_migrated_v1"

/** 通知 ID：掺入 serverId 避免跨服务器冲突；位与 Int.MAX_VALUE 规避 Int.MIN_VALUE 取负。 */
private fun stableNotifId(seed: Int, salt: String): Int = (salt + seed).hashCode() and Int.MAX_VALUE

internal suspend fun StarBurstConnectionService.startReconciliation(server: ServerConfig, conn: ServerConnection) {
    // 先取消并等待旧对账 job 退出，再启动新 job：若只 put 新 job 而不取消旧的，
    // 新旧两轮会并发拉取同一批状态，弱网下互相加重负载；join 保证旧 job 已彻底退出。
    val old = reconciliationJobs[server.id]
    old?.cancel()
    old?.join()
    val job = serviceScope.launch { reconcileServerState(server, conn) }
    reconciliationJobs.put(server.id, job)
    job.invokeOnCompletion { reconciliationJobs.remove(server.id, job) }
}

internal suspend fun StarBurstConnectionService.reconcileServerState(server: ServerConfig, conn: ServerConnection) {
    val revision = eventReducer.pendingSnapshotRevision()
    val permissions = mutableListOf<SseEvent.PermissionAsked>()
    val questions = mutableListOf<SseEvent.QuestionAsked>()
    var complete = true
    try {
        val localSessions = eventReducer.sessions.value.associateBy { it.id }
        val sessions = api.listSessions(conn)
        val changedSessions = sessionsNeedingMessageReconciliation(localSessions, sessions)
        eventReducer.setSessions(server.id, sessions)
        // 断线对账的消息补拉：并发执行（弱网下串行 20×limit50 会造成重连请求风暴）。
        coroutineScope {
            changedSessions.map { session ->
                async {
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
            }.awaitAll()
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

internal fun StarBurstConnectionService.calculateBackoff(attempt: Int): Long {
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

/**
 * Check if a session is a child/sub-agent session (has parentID set).
 * Child sessions should not trigger user-facing notifications,
 * matching the behavior of the official opencode WebUI and TUI.
 */
internal fun StarBurstConnectionService.isChildSession(sessionId: String): Boolean {
    val session = eventReducer.sessions.value.find { it.id == sessionId }
    return session?.parentId != null
}

/**
 * 解析后端镜像地址：
 * - SSH 隧道且隧道映射了后端端口 → 用隧道内本地端口（127.0.0.1:端口）；
 * - SSH 隧道未映射后端端口但仍能直连后端主机（如 VPN 同网段）→ 退回显式或
 *   推断地址（http://<opencode主机>:18880）；探测失败仍由 buildGatewayConn 弹回直连；
 * - 非 SSH 模式 → 显式 [server.backendUrl]，否则推断为 opencode 同主机 18880。
 */
internal fun StarBurstConnectionService.resolveBackendUrl(server: ServerConfig, backendLocalPort: Int?): String {
    if (server.useSsh) {
        backendLocalPort?.let { return "http://127.0.0.1:$it" }
    }
    return server.backendResolvedUrl
}

internal fun StarBurstConnectionService.getSessionInfo(sessionId: String): Pair<String?, String?> {
    val session = eventReducer.sessions.value.find { it.id == sessionId }
    return Pair(session?.title, session?.directory)
}

internal fun StarBurstConnectionService.latestNotifiableAssistantMessageId(sessionId: String): String? {
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
internal fun StarBurstConnectionService.buildAssistantMessageSummary(sessionId: String): String? {
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

internal fun StarBurstConnectionService.getProjectName(directory: String?): String? {
    if (directory.isNullOrBlank()) return null
    return directory.trimEnd('/').substringAfterLast('/')
}

internal fun StarBurstConnectionService.base64UrlEncode(value: String): String {
    val encoded = android.util.Base64.encodeToString(
        value.toByteArray(Charsets.UTF_8),
        android.util.Base64.NO_WRAP
    )
    return encoded
        .replace('+', '-')
        .replace('/', '_')
        .replace("=", "")
}

internal fun StarBurstConnectionService.buildSessionPath(sessionId: String): String? {
    val session = eventReducer.sessions.value.find { it.id == sessionId }
    if (session == null) {
        Log.w(TAG, "buildSessionPath: session $sessionId not found")
        return null
    }
    val encodedDir = base64UrlEncode(session.directory)
    return "/$encodedDir/session/$sessionId"
}

internal fun StarBurstConnectionService.createSessionPendingIntent(server: ServerConfig, sessionId: String?, requestCode: Int): PendingIntent {
    val sessionPath = sessionId?.let { buildSessionPath(it) }

    val intent = Intent(this, MainActivity::class.java).apply {
        action = StarBurstConnectionService.ACTION_OPEN_SESSION
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(StarBurstConnectionService.EXTRA_SERVER_URL, server.url)
        putExtra(StarBurstConnectionService.EXTRA_SERVER_USERNAME, server.username)
        putExtra(StarBurstConnectionService.EXTRA_SERVER_NAME, server.displayName)
        putExtra(StarBurstConnectionService.EXTRA_SERVER_ID, server.id)
        sessionPath?.let { putExtra(StarBurstConnectionService.EXTRA_SESSION_PATH, it) }
        sessionId?.let { putExtra(StarBurstConnectionService.EXTRA_SESSION_ID, it) }
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
 * BroadcastReceiver 监听 [StarBurstConnectionService.ACTION_RETRY_SESSION] 触发对应会话重试。
 */
internal fun StarBurstConnectionService.createRetrySessionPendingIntent(
    server: ServerConfig,
    sessionId: String?,
    requestCode: Int,
): PendingIntent {
    val intent = Intent(StarBurstConnectionService.ACTION_RETRY_SESSION).apply {
        setPackage(packageName)
        putExtra(StarBurstConnectionService.EXTRA_SERVER_ID, server.id)
        sessionId?.let { putExtra(StarBurstConnectionService.EXTRA_SESSION_ID, it) }
    }
    return PendingIntent.getBroadcast(
        this,
        requestCode,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

/**
 * 构造通知内嵌「回复」RemoteInput 动作：携带 serverId/sessionId/通知 ID/回复类型等非敏感 extras，
 * 由 [NotificationReplyReceiver] 接收用户输入的文本并转发给本服务处理。
 */
internal fun StarBurstConnectionService.buildReplyAction(
    server: ServerConfig,
    sessionId: String,
    notifId: Int,
    kind: String,
): NotificationCompat.Action {
    val remoteInput = RemoteInput.Builder(StarBurstConnectionService.KEY_NOTIFICATION_REPLY)
        .setLabel(getString(R.string.notification_reply_label))
        .build()
    val replyIntent = Intent(StarBurstConnectionService.ACTION_NOTIFICATION_REPLY).apply {
        setPackage(packageName)
        putExtra(StarBurstConnectionService.EXTRA_SERVER_ID, server.id)
        putExtra(StarBurstConnectionService.EXTRA_SESSION_ID, sessionId)
        putExtra(StarBurstConnectionService.EXTRA_REPLY_NOTIFICATION_ID, notifId)
        putExtra(StarBurstConnectionService.EXTRA_REPLY_KIND, kind)
    }
    val replyPendingIntent = PendingIntent.getBroadcast(
        this,
        notifId,
        replyIntent,
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    return NotificationCompat.Action.Builder(
        android.R.drawable.ic_menu_send,
        getString(R.string.notification_reply_label),
        replyPendingIntent
    ).addRemoteInput(remoteInput).build()
}

/**
 * 处理来自通知内嵌回复的文本：回答问题或给会话发送后续 prompt。
 * 仅通过非敏感 extras（serverId/sessionId）路由，绝不携带密码。
 */
internal suspend fun StarBurstConnectionService.handleNotificationReply(serverId: String, sessionId: String, replyText: String, kind: String?) {
    val state = connections[serverId]
    if (state == null) {
        Log.w(TAG, "Notification reply ignored: server $serverId not connected (session=$sessionId)")
        return
    }
    Log.d(TAG, "Notification reply: server=$serverId session=$sessionId kind=$kind textLen=${replyText.length}")
    if (BuildConfig.DEBUG) {
        Log.d(TAG, "Notification reply text='${replyText.take(80)}'")
    }
    when (kind) {
        StarBurstConnectionService.REPLY_KIND_QUESTION -> answerQuestionFromReply(state, sessionId, replyText)
        StarBurstConnectionService.REPLY_KIND_COMPLETION -> sendFollowUpPrompt(state, sessionId, replyText)
        else -> storeReplyAsPendingPrompt(state, sessionId, replyText)
    }
}

/**
 * 复用既有问题回答路径：查会话待决问题并以回复文本作为答案提交；
 * 找不到待决问题或提交失败时，降级为把回复存为待发 prompt，保证文本不丢失。
 */
internal suspend fun StarBurstConnectionService.answerQuestionFromReply(state: ServerConnectionState, sessionId: String, replyText: String) {
    val question = eventReducer.pendingInteractions.value
        .filterIsInstance<PendingInteraction.Question>()
        .firstOrNull { it.sessionId == sessionId }
    if (question == null) {
        Log.w(TAG, "No pending question for session $sessionId; storing reply as pending prompt")
        storeReplyAsPendingPrompt(state, sessionId, replyText)
        return
    }
    val directory = sessionDirectoryOf(sessionId)
    val ok = try {
        api.replyToQuestion(state.conn, question.id, listOf(listOf(replyText)), directory)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Failed to reply to question ${question.id}", e)
        false
    }
    if (ok) {
        eventReducer.removeQuestion(sessionId, question.id)
        Log.i(TAG, "Question ${question.id} answered via notification reply")
    } else {
        storeReplyAsPendingPrompt(state, sessionId, replyText)
    }
}

/** 给会话发送后续 prompt（继续会话），失败时降级为待发 prompt。 */
internal suspend fun StarBurstConnectionService.sendFollowUpPrompt(state: ServerConnectionState, sessionId: String, replyText: String) {
    val directory = sessionDirectoryOf(sessionId)
    val messageId = MessageIdGenerator.next()
    val parts = listOf(PromptPart(type = "text", text = replyText))
    try {
        api.promptAsync(
            conn = state.conn,
            sessionId = sessionId,
            messageId = messageId,
            parts = parts,
            directory = directory,
        )
        eventReducer.updateSessionStatus(sessionId, SessionStatus.Busy)
        Log.i(TAG, "Follow-up prompt sent to session $sessionId via notification reply")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Failed to send follow-up prompt to session $sessionId", e)
        storeReplyAsPendingPrompt(state, sessionId, replyText)
    }
}

/** 回复无法立即投递时，存为待发 prompt，由会话界面读取并展示。 */
internal fun StarBurstConnectionService.storeReplyAsPendingPrompt(state: ServerConnectionState, sessionId: String, replyText: String) {
    pendingPromptRepository.save(
        PendingPromptRecord(
            messageId = MessageIdGenerator.next(),
            sessionId = sessionId,
            parts = listOf(PromptPart(type = "text", text = replyText)),
            directory = sessionDirectoryOf(sessionId),
            createdAt = System.currentTimeMillis(),
        )
    )
    Log.i(TAG, "Reply stored as pending prompt for session $sessionId (server=${state.config.id})")
}

/** 取会话工作目录（供回答问题/发送 prompt 的 directory 参数使用）。 */
internal fun StarBurstConnectionService.sessionDirectoryOf(sessionId: String): String? =
    eventReducer.sessions.value.firstOrNull { it.id == sessionId }
        ?.directory
        ?.takeIf { it.isNotBlank() }

// ============ Notification Channels ============

internal fun StarBurstConnectionService.createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // 仅首次升级重建一次，避免每次启动 delete+recreate 重置用户渠道定制。
        val migratedPrefs = getSharedPreferences(NOTIFICATION_CHANNELS_PREFS, Context.MODE_PRIVATE)
        if (!migratedPrefs.getBoolean(NOTIFICATION_CHANNELS_MIGRATED_KEY, false)) {
            // The tasks channel previously shipped without an explicit sound. Android never
            // updates an already-created channel's sound settings, so force it by deleting
            // and recreating the channel once on this upgrade.
            if (notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_TASKS_ID) != null) {
                notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_TASKS_ID)
            }
            // 权限/提问通知此前无声（MIUI 上 HIGH channel 不 setSound 即静音），重建一次以生效。
            if (notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_PERMISSIONS_ID) != null) {
                notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_PERMISSIONS_ID)
            }
            migratedPrefs.edit().putBoolean(NOTIFICATION_CHANNELS_MIGRATED_KEY, true).apply()
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

internal fun StarBurstConnectionService.createPersistentNotification(): Notification {
    val tapIntent = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    val tapPendingIntent = PendingIntent.getActivity(
        this, 0, tapIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val exitIntent = Intent(this, StarBurstConnectionService::class.java).apply {
        action = StarBurstConnectionService.ACTION_EXIT
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

internal fun StarBurstConnectionService.updatePersistentNotification() {
    synchronized(this) {
        if (connections.isEmpty()) {
            if (foregroundStarted) {
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
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
}

// ============ Event Notifications (grouped by server) ============

internal suspend fun StarBurstConnectionService.showTaskCompleteNotification(server: ServerConfig, sessionId: String) {
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
    builder.addAction(buildReplyAction(server, sessionId, notifId, StarBurstConnectionService.REPLY_KIND_COMPLETION))

    if (!silent) {
        builder.setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            // Android 13+ 高优渠道默认横幅（heads-up）；此渠道已配置声音+震动。
            // Android 14 默认拒绝全屏通知（FSI_REQUESTED_BUT_DENIED），故不用 setFullScreenIntent。
    }

    postEventNotification(server, sessionId, notifId, builder.build())
}

internal fun StarBurstConnectionService.showPermissionNotification(server: ServerConfig, sessionId: String, permission: String) {
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

internal fun StarBurstConnectionService.showQuestionNotification(server: ServerConfig, sessionId: String, questionText: String) {
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

    val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_PERMISSIONS_ID)
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

    builder.addAction(buildReplyAction(server, sessionId, notifId, StarBurstConnectionService.REPLY_KIND_QUESTION))

    postEventNotification(server, sessionId, notifId, builder.build())
}

internal fun StarBurstConnectionService.showErrorNotification(server: ServerConfig, sessionId: String?, error: String) {
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

/** 百分比数值去零显示：90.0 -> 90，92.3 -> 92.3。 */
private fun trimPercent(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

/** Intel 测试运行终态通知（仅 passed / failed 触发；queued / running 不打扰）。 */
internal suspend fun StarBurstConnectionService.showIntelRunNotification(server: ServerConfig, run: IntelTestRun) {
    if (!settingsRepository.notificationsEnabled.first()) return
    if (run.status != "passed" && run.status != "failed") return
    val statusText = if (run.status == "passed") {
        getString(R.string.test_intel_run_status_passed)
    } else {
        getString(R.string.test_intel_run_status_failed)
    }
    val subject = run.scope.takeIf { it.isNotBlank() }
        ?: run.moduleId.takeIf { it > 0 }?.toString()
    val title = if (subject != null) "${server.displayName} · $subject" else server.displayName
    val progress = run.progress?.takeIf { it.isNotBlank() } ?: "-"
    val body = getString(R.string.notify_intel_run_body, statusText, progress)
    val notifId = stableNotifId(run.id.toInt(), "intel_run_${server.id}")
    val silent = settingsRepository.silentNotifications.first()
    val channelId = if (silent) NOTIFICATION_CHANNEL_TASKS_SILENT_ID else NOTIFICATION_CHANNEL_TASKS_ID
    val pendingIntent = PendingIntent.getActivity(
        this,
        notifId,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val builder = NotificationCompat.Builder(this, channelId)
        .setContentTitle(title)
        .setContentText(body)
        .setSubText(server.displayName)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setPriority(if (silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setGroup("server_${server.id}")
    if (!silent) {
        builder.setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 500, 200, 500))
    }
    postEventNotification(server, null, notifId, builder.build())
}

/** 硬件资源告警 / 恢复通知（metric：cpu / mem / disk）。 */
internal suspend fun StarBurstConnectionService.showHardwareAlertNotification(server: ServerConfig, event: HardwareAlertEvent) {
    if (!settingsRepository.notificationsEnabled.first()) return
    val metricLabel = when (event.metric) {
        "cpu" -> getString(R.string.alert_cpu)
        "mem" -> getString(R.string.alert_memory)
        "disk" -> getString(R.string.alert_disk)
        else -> event.metric
    }
    val title = getString(R.string.notify_alert_hardware)
    val body = if (event.state == "ok" || event.state == "recover") {
        getString(R.string.alert_history_state_ok)
    } else {
        getString(
            R.string.notify_alert_hardware_threshold,
            metricLabel,
            trimPercent(event.value),
            trimPercent(event.threshold),
        )
    }
    val notifId = stableNotifId(event.metric.hashCode(), "hw_alert_${server.id}")
    val silent = settingsRepository.silentNotifications.first()
    val channelId = if (silent) NOTIFICATION_CHANNEL_TASKS_SILENT_ID else NOTIFICATION_CHANNEL_TASKS_ID
    val pendingIntent = PendingIntent.getActivity(
        this,
        notifId,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val builder = NotificationCompat.Builder(this, channelId)
        .setContentTitle(title)
        .setContentText(body)
        .setSubText(server.displayName)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setPriority(if (silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setGroup("server_${server.id}")
    if (!silent) {
        builder.setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 500, 200, 500))
    }
    postEventNotification(server, null, notifId, builder.build())
}

/** 安全 / 合规审计发现通知（仅 high / critical 触发）。 */
internal suspend fun StarBurstConnectionService.showAuditFindingNotification(server: ServerConfig, summary: String, severity: String) {
    if (!settingsRepository.notificationsEnabled.first()) return
    val title = getString(R.string.notify_audit_finding)
    val body = summary.take(120)
    val notifId = stableNotifId(summary.hashCode(), "audit_${server.id}")
    val silent = settingsRepository.silentNotifications.first()
    val channelId = if (silent) NOTIFICATION_CHANNEL_TASKS_SILENT_ID else NOTIFICATION_CHANNEL_TASKS_ID
    val pendingIntent = PendingIntent.getActivity(
        this,
        notifId,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val builder = NotificationCompat.Builder(this, channelId)
        .setContentTitle(title)
        .setContentText(body)
        .setSubText("$severity · ${server.displayName}")
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setPriority(if (silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setGroup("server_${server.id}")
    if (!silent) {
        builder.setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 500, 200, 500))
    }
    postEventNotification(server, null, notifId, builder.build())
}

/** 测试门禁阻断通知（`intel.gate.blocked`）：展示阻断原因与缺失前置。 */
internal suspend fun StarBurstConnectionService.showGateBlockedNotification(
    server: ServerConfig,
    reason: String,
    detail: String?,
    missing: List<String>,
) {
    if (!settingsRepository.notificationsEnabled.first()) return
    val missingText = missing.joinToString(", ").ifBlank { "-" }
    val body = getString(R.string.notify_intel_gate_blocked_body, reason, missingText)
        .let { if (detail.isNullOrBlank()) it else "$it\n$detail" }
    postIntelEventNotification(
        server = server,
        title = getString(R.string.notify_intel_gate_blocked),
        body = body,
        notifId = stableNotifId(("gate" + reason).hashCode(), "intel_evt_${server.id}"),
    )
}

/** 自动修复建议通知（`intel.fix.suggested`）。 */
internal suspend fun StarBurstConnectionService.showFixSuggestedNotification(server: ServerConfig, title: String) {
    if (!settingsRepository.notificationsEnabled.first()) return
    postIntelEventNotification(
        server = server,
        title = getString(R.string.notify_intel_fix_suggested),
        body = title.ifBlank { server.displayName },
        notifId = stableNotifId(("fix" + title).hashCode(), "intel_evt_${server.id}"),
    )
}

/** 自动修复已应用通知（`intel.fix.applied`）。 */
internal suspend fun StarBurstConnectionService.showFixAppliedNotification(server: ServerConfig, writeMode: String) {
    if (!settingsRepository.notificationsEnabled.first()) return
    postIntelEventNotification(
        server = server,
        title = getString(R.string.notify_intel_fix_applied),
        body = writeMode,
        notifId = stableNotifId(("fix_applied" + writeMode).hashCode(), "intel_evt_${server.id}"),
    )
}

/** 功能点问答完成通知（`intel.feature.chat.answer`）。 */
internal suspend fun StarBurstConnectionService.showChatAnswerNotification(server: ServerConfig, mode: String) {
    if (!settingsRepository.notificationsEnabled.first()) return
    postIntelEventNotification(
        server = server,
        title = getString(R.string.notify_intel_chat_answer),
        body = mode,
        notifId = stableNotifId(("chat" + mode).hashCode(), "intel_evt_${server.id}"),
    )
}

/** 项目环境就绪通知（`intel.env.ready`）。 */
internal suspend fun StarBurstConnectionService.showEnvReadyNotification(server: ServerConfig, projectId: Long) {
    if (!settingsRepository.notificationsEnabled.first()) return
    postIntelEventNotification(
        server = server,
        title = getString(R.string.notify_intel_env_ready),
        body = projectId.toString(),
        notifId = stableNotifId(("env" + projectId).hashCode(), "intel_evt_${server.id}"),
    )
}

/** 通用 Intel 事件通知：与 run 终态通知同款样式（分组 / DND / 静默渠道一致）。 */
private suspend fun StarBurstConnectionService.postIntelEventNotification(
    server: ServerConfig,
    title: String,
    body: String,
    notifId: Int,
) {
    val silent = settingsRepository.silentNotifications.first()
    val channelId = if (silent) NOTIFICATION_CHANNEL_TASKS_SILENT_ID else NOTIFICATION_CHANNEL_TASKS_ID
    val pendingIntent = PendingIntent.getActivity(
        this,
        notifId,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val builder = NotificationCompat.Builder(this, channelId)
        .setContentTitle(title)
        .setContentText(body)
        .setSubText(server.displayName)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setPriority(if (silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setGroup("server_${server.id}")
    if (!silent) {
        builder.setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 500, 200, 500))
    }
    postEventNotification(server, null, notifId, builder.build())
}

internal fun StarBurstConnectionService.postEventNotification(
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
internal fun StarBurstConnectionService.isNowInDndWindow(): Boolean {
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

internal fun StarBurstConnectionService.parseHhMmMinutes(value: String): Int {
    val parts = value.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 0
    val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    return hour * 60 + minute
}

/** 用静默渠道重建通知（保留内容/分组/意图，去掉声音与震动）。 */
internal fun StarBurstConnectionService.rebuildSilentNotification(original: Notification): Notification {
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
internal fun StarBurstConnectionService.showServerGroupSummary(server: ServerConfig) {
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
