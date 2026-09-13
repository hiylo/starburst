/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : WidgetSnapshotStore.kt
 * Date : 2026/09/12 20:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.3.0
 */
package org.hiylo.opencode.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Widget 快照的持久化存储：应用进程写入、Widget 进程读取。
 *
 * 使用独立的 SharedPreferences 文件（与应用其它偏好隔离），快照为单条 JSON。
 * Widget 的 [OpenCodeWidgetProvider.onUpdate] 在桌面进程被拉起时同步读取，
 * 不依赖应用内存态。
 *
 * @author Hsi Chu
 * @since V1.3.0
 */
@Singleton
class WidgetSnapshotStore @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cached: WidgetSnapshot? = null

    /** 读取最新快照（带进程内缓存，避免 Widget 渲染频繁反序列化）。 */
    fun snapshot(): WidgetSnapshot = cached ?: read(context).also { cached = it }

    /** 写入新快照并更新缓存。 */
    fun write(snapshot: WidgetSnapshot) {
        cached = snapshot
        runCatching {
            prefs.edit()
                .putString(KEY_SNAPSHOT, json.encodeToString(snapshot))
                .apply()
        }
    }

    /** 触发系统回调 [OpenCodeWidgetProvider.onUpdate]，让 Widget 重新渲染。 */
    fun requestUpdate() {
        requestUpdate(context)
    }

    companion object {
        /** 供 Widget Provider 在非 Hilt 环境下同步读取快照。 */
        fun read(context: Context): WidgetSnapshot {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_SNAPSHOT, null) ?: return WidgetSnapshot()
            return runCatching {
                json.decodeFromString<WidgetSnapshot>(raw)
            }.getOrDefault(WidgetSnapshot())
        }

        /** 请求系统刷新所有 OpenCode AppWidget。 */
        fun requestUpdate(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, OpenCodeWidgetProvider::class.java),
                )
                if (ids.isEmpty()) return
                val intent = Intent(context, OpenCodeWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }

        private const val PREFS_NAME = "opencode_widget_snapshot"
        private const val KEY_SNAPSHOT = "snapshot"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
