/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : OpenCodeWidgetProvider.kt
 * Date : 2026/09/11 09:30:14
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.3.0
 */
package org.hiylo.opencode.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import org.hiylo.opencode.MainActivity
import org.hiylo.opencode.R

/**
 * 桌面 Widget 提供者：展示服务器连接状态、快捷操作（新建会话/全局搜索/任务中心）、
 * 任务统计与最近会话列表。
 *
 * 数据来自应用进程持续写入的 [WidgetSnapshotStore]，`onUpdate` 时同步读取渲染。
 * 快捷按钮通过 `action` extra 返回 [MainActivity]；最近会话行通过
 * [Intent.ACTION_VIEW] + 深链参数直达对应会话。
 *
 * @author Hsi Chu
 * @since V1.3.0
 */
class OpenCodeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val snapshot = WidgetSnapshotStore.read(context)
        for (appWidgetId in appWidgetIds) {
            appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context, snapshot))
        }
    }

    /**
     * 依据快照构造 Widget 的远程视图并绑定按钮/会话行的点击跳转。
     */
    private fun buildRemoteViews(context: Context, snapshot: WidgetSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_opencode)

        // ---- 标题 ----
        views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))

        // ---- 常用服务器 + 状态 ----
        val servers = snapshot.servers
        val primary = servers.maxByOrNull { it.connected }
        if (primary != null) {
            val statusText = if (primary.connected) {
                context.getString(R.string.widget_connected, primary.name)
            } else {
                context.getString(R.string.widget_disconnected, primary.name)
            }
            views.setTextViewText(R.id.widget_server_status, statusText)
        }
        bindServers(context, views, servers)

        // ---- 快捷按钮 ----
        views.setOnClickPendingIntent(R.id.widget_new_session_button, actionPendingIntent(context, ACTION_WIDGET_NEW_SESSION, REQUEST_CODE_NEW_SESSION))
        views.setOnClickPendingIntent(R.id.widget_search_button, actionPendingIntent(context, ACTION_WIDGET_SEARCH, REQUEST_CODE_SEARCH))
        views.setOnClickPendingIntent(R.id.widget_task_button, actionPendingIntent(context, ACTION_WIDGET_TASK_CENTER, REQUEST_CODE_TASKS))

        // ---- 任务统计（点任务按钮相同的目标）----
        if (primary != null) {
            val counts = snapshot.taskCounts[primary.id]
            if (counts != null && (counts.active > 0 || counts.failed > 0)) {
                views.setTextViewText(
                    R.id.widget_task_summary,
                    context.getString(
                        R.string.widget_task_summary,
                        counts.running,
                        counts.queued,
                        counts.failed,
                    ),
                )
                views.setOnClickPendingIntent(R.id.widget_task_summary, actionPendingIntent(context, ACTION_WIDGET_TASK_CENTER, REQUEST_CODE_TASKS))
                views.setViewVisibility(R.id.widget_task_summary, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_task_summary, View.GONE)
            }
        } else {
            views.setViewVisibility(R.id.widget_task_summary, View.GONE)
        }

        // ---- 有新消息/活动的会话 ----
        bindActiveSessions(context, views, snapshot)
        return views
    }

    /** 常用服务器行：每个服务器可点击一键直连（action 带 serverId）。 */
    private fun bindServers(context: Context, views: RemoteViews, servers: List<WidgetServerInfo>) {
        val slots = listOf(
            R.id.widget_server_1 to 1,
            R.id.widget_server_2 to 2,
            R.id.widget_server_3 to 3,
        )
        val shown = minOf(servers.size, slots.size)
        if (shown > 0) {
            views.setTextViewText(R.id.widget_server_title, context.getString(R.string.widget_servers))
            views.setViewVisibility(R.id.widget_server_title, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_server_title, View.GONE)
        }

        for (index in slots.indices) {
            val (viewId, _) = slots[index]
            if (index < shown) {
                val server = servers[index]
                val label = if (server.connected) {
                    context.getString(R.string.widget_server_connected, server.name)
                } else {
                    context.getString(R.string.widget_server_offline, server.name)
                }
                views.setTextViewText(viewId, label)
                views.setOnClickPendingIntent(viewId, connectServerPendingIntent(context, server))
                views.setViewVisibility(viewId, View.VISIBLE)
            } else {
                views.setViewVisibility(viewId, View.GONE)
            }
        }
    }

    private fun bindActiveSessions(context: Context, views: RemoteViews, snapshot: WidgetSnapshot) {
        val sessions = snapshot.activeSessions
        val slots = listOf(
            R.id.widget_session_1 to 1,
            R.id.widget_session_2 to 2,
            R.id.widget_session_3 to 3,
            R.id.widget_session_4 to 4,
            R.id.widget_session_5 to 5,
        )

        val maxSessions = minOf(sessions.size, slots.size)
        if (maxSessions > 0) {
            views.setTextViewText(R.id.widget_recent_title, context.getString(R.string.widget_active_sessions))
            views.setViewVisibility(R.id.widget_recent_title, View.VISIBLE)
            views.setViewVisibility(R.id.widget_no_sessions, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_recent_title, View.GONE)
            views.setTextViewText(R.id.widget_no_sessions, context.getString(R.string.widget_no_active_sessions))
            views.setViewVisibility(R.id.widget_no_sessions, View.VISIBLE)
        }

        for (index in slots.indices) {
            val (viewId, _) = slots[index]
            if (index < maxSessions) {
                val session = sessions[index]
                views.setTextViewText(viewId, session.title)
                views.setOnClickPendingIntent(viewId, sessionPendingIntent(context, session))
                views.setViewVisibility(viewId, View.VISIBLE)
            } else {
                views.setViewVisibility(viewId, View.GONE)
            }
        }
    }

    // ============ PendingIntents ============

    private fun actionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = baseActionIntent(context, action)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 服务器行：点击一键直连（复用通知深链的服务启动参数格式）。 */
    private fun connectServerPendingIntent(context: Context, server: WidgetServerInfo): PendingIntent {
        val intent = baseActionIntent(context, ACTION_WIDGET_CONNECT_SERVER).apply {
            putExtra(EXTRA_SERVER_ID, server.id)
            putExtra(EXTRA_SERVER_NAME, server.name)
            putExtra(EXTRA_SERVER_URL, server.url)
        }
        return PendingIntent.getActivity(
            context,
            (REQUEST_CODE_SERVER_BASE + server.id.hashCode()) and 0xffffff,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 有新消息的会话行：直达目标会话（复用通知跳转的深链参数格式）。 */
    private fun sessionPendingIntent(context: Context, session: WidgetSessionInfo): PendingIntent {
        val intent = baseActionIntent(context, ACTION_WIDGET_OPEN_SESSION).apply {
            putExtra(EXTRA_SESSION_ID, session.sessionId)
            putExtra(EXTRA_SERVER_ID, session.serverId)
            putExtra(EXTRA_SERVER_NAME, session.serverName)
            if (session.directory.isNotBlank()) {
                putExtra(EXTRA_SESSION_DIRECTORY, session.directory)
            }
        }
        return PendingIntent.getActivity(
            context,
            (REQUEST_CODE_SESSION_BASE + session.sessionId.hashCode()) and 0xffffff,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun baseActionIntent(context: Context, action: String): Intent {
        return Intent(context, MainActivity::class.java).apply {
            this.action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ACTION, action)
        }
    }

    companion object {
        /** intent extra 的 key，用于标识入口来源（widget / shortcut）。 */
        const val EXTRA_ACTION = "action"

        /** Widget「新建会话」按钮的 action 值。 */
        const val ACTION_WIDGET_NEW_SESSION = "widget_new_session"

        /** 静态快捷方式「新建会话」的 action 值，与 [ACTION_WIDGET_NEW_SESSION] 语义一致。 */
        const val ACTION_SHORTCUT_NEW_SESSION = "shortcut_new_session"

        /** 静态快捷方式「全局搜索」的 action 值。 */
        const val ACTION_SHORTCUT_SEARCH = "shortcut_search"

        /** Widget「全局搜索」按钮的 action 值。 */
        const val ACTION_WIDGET_SEARCH = "widget_search"

        /** Widget「任务中心」按钮的 action 值。 */
        const val ACTION_WIDGET_TASK_CENTER = "widget_task_center"

        /** Widget 服务器行「一键直连」的 action 值。 */
        const val ACTION_WIDGET_CONNECT_SERVER = "widget_connect_server"

        /** Widget 有新消息会话行的 action 值。 */
        const val ACTION_WIDGET_OPEN_SESSION = "widget_open_session"

        /** Widget 传递的服务器/会话参数 key（与 OpenCodeConnectionService 保持一致）。 */
        const val EXTRA_SERVER_ID = "server_id"
        const val EXTRA_SERVER_NAME = "server_name"
        const val EXTRA_SERVER_URL = "server_url"
        const val EXTRA_SESSION_ID = "sessionId"
        const val EXTRA_SESSION_DIRECTORY = "session_directory"

        /** PendingIntent 请求码，用于区分不同入口。 */
        private const val REQUEST_CODE_NEW_SESSION = 1001
        private const val REQUEST_CODE_SEARCH = 1002
        private const val REQUEST_CODE_TASKS = 1003
        private const val REQUEST_CODE_SERVER_BASE = 1501
        private const val REQUEST_CODE_SESSION_BASE = 2001
    }
}