/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : AlertHistoryRepository.kt
 * Date : 2026/09/22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.starburst.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** 一条硬件监控告警历史记录（本地积累，后端无历史端点）。 */
data class AlertHistoryEntry(
    val timestamp: Long,
    val serverId: String,
    val metric: String,
    val value: Double,
    val threshold: Double,
    val state: String,
)

/**
 * 硬件监控告警历史：本地 SQLite 持久化，接收推送与轮询兜底两路写入。
 *
 * 后端告警没有历史端点，WS 推送还会丢帧，因此 App 自行落库积累告警事件（越线/恢复），
 * 并在服务器管理页展示最近记录。
 *
 * @author Hsi Chu
 */
@Singleton
class AlertHistoryRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val database = AlertHistoryDatabase(context.applicationContext)

    /** 记录一条告警事件（越线或恢复）。 */
    suspend fun record(serverId: String, metric: String, value: Double, threshold: Double, state: String) {
        withContext(Dispatchers.IO) {
            database.insert(serverId, metric, value, threshold, state)
        }
    }

    /** 取某服务器最近 [limit] 条告警历史（时间倒序）。 */
    suspend fun latest(serverId: String, limit: Int = 50): List<AlertHistoryEntry> = withContext(Dispatchers.IO) {
        database.latest(serverId, limit)
    }

    /** 清空某服务器的告警历史。 */
    suspend fun clear(serverId: String) {
        withContext(Dispatchers.IO) {
            database.clear(serverId)
        }
    }
}

/** 告警历史的 SQLite 存储（表 `alert_history`，按 server_id + 时间建索引）。 */
internal class AlertHistoryDatabase(
    context: Context,
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE alert_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                server_id TEXT NOT NULL,
                metric TEXT NOT NULL,
                value REAL NOT NULL,
                threshold REAL NOT NULL,
                state TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX alert_history_server_timestamp ON alert_history(server_id, timestamp DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS alert_history")
        onCreate(db)
    }

    fun insert(serverId: String, metric: String, value: Double, threshold: Double, state: String, now: Long = System.currentTimeMillis()) {
        writableDatabase.insertOrThrow(
            "alert_history",
            null,
            ContentValues().apply {
                put("timestamp", now)
                put("server_id", serverId)
                put("metric", metric)
                put("value", value)
                put("threshold", threshold)
                put("state", state)
            },
        )
        writableDatabase.delete(
            "alert_history",
            "timestamp < ?",
            arrayOf((now - RETENTION_MS).toString()),
        )
    }

    fun latest(serverId: String, limit: Int): List<AlertHistoryEntry> {
        val result = mutableListOf<AlertHistoryEntry>()
        readableDatabase.query(
            "alert_history",
            arrayOf("timestamp", "server_id", "metric", "value", "threshold", "state"),
            "server_id = ?",
            arrayOf(serverId),
            null,
            null,
            "timestamp DESC, id DESC",
            limit.toString(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += AlertHistoryEntry(
                    timestamp = cursor.getLong(0),
                    serverId = cursor.getString(1),
                    metric = cursor.getString(2),
                    value = cursor.getDouble(3),
                    threshold = cursor.getDouble(4),
                    state = cursor.getString(5),
                )
            }
        }
        return result
    }

    fun clear(serverId: String) {
        writableDatabase.delete("alert_history", "server_id = ?", arrayOf(serverId))
    }

    companion object {
        const val DATABASE_NAME = "alert_history.db"
        private const val DATABASE_VERSION = 1
        private const val RETENTION_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
