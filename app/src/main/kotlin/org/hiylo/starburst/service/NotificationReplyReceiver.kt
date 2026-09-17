/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : NotificationReplyReceiver.kt
 * Date : 2026/09/17 11:48:11
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import org.hiylo.starburst.logging.AppLogger as Log

/**
 * 接收通知内嵌「回复」按钮的 RemoteInput 文本，取消对应通知后转发给
 * [StarBurstConnectionService] 处理（回答问题或发送后续 prompt）。
 *
 * 仅携带 serverId/sessionId 等非敏感 extras，绝不携带密码。
 *
 * @author Hsi Chu
 * @since V1.0
 */
class NotificationReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != StarBurstConnectionService.ACTION_NOTIFICATION_REPLY) return

        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(StarBurstConnectionService.KEY_NOTIFICATION_REPLY)
            ?.toString()
            ?.trim()
        val serverId = intent.getStringExtra(StarBurstConnectionService.EXTRA_SERVER_ID) ?: ""
        val sessionId = intent.getStringExtra(StarBurstConnectionService.EXTRA_SESSION_ID) ?: ""
        val kind = intent.getStringExtra(StarBurstConnectionService.EXTRA_REPLY_KIND)
        val notificationId = intent.getIntExtra(StarBurstConnectionService.EXTRA_REPLY_NOTIFICATION_ID, -1)

        // 回复后立即取消对应通知，避免残留。
        if (notificationId >= 0) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(notificationId)
        }

        if (replyText.isNullOrBlank()) {
            Log.w(TAG, "Notification reply is blank, ignored (server=$serverId session=$sessionId)")
            return
        }

        val serviceIntent = Intent(context, StarBurstConnectionService::class.java).apply {
            action = StarBurstConnectionService.ACTION_HANDLE_REPLY
            putExtra(StarBurstConnectionService.EXTRA_SERVER_ID, serverId)
            putExtra(StarBurstConnectionService.EXTRA_SESSION_ID, sessionId)
            putExtra(StarBurstConnectionService.EXTRA_REPLY_TEXT, replyText)
            putExtra(StarBurstConnectionService.EXTRA_REPLY_KIND, kind)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    companion object {
        /** 日志标签。 */
        private const val TAG = "NotificationReplyReceiver"
    }
}
