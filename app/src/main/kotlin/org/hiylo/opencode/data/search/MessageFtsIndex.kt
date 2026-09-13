/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : MessageFtsIndex.kt
 * Date : 2026/09/11 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.opencode.data.search

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 消息全文搜索的索引与查询仓库。
 *
 * 基于 Android 原生 SQLite 的普通表 + LIKE 子串匹配，为消息文本建立索引，
 * 支持跨会话、跨服务器的关键词搜索，并返回命中的消息定位与高亮片段。
 *
 * 说明：不依赖 SQLite 的 FTS5 扩展模块——部分设备/ROM 的原生 SQLite 未编译 FTS5
 * （会报 `no such module: fts5`），改用 LIKE 对中文子串匹配也更直接有效。
 *
 * 所有数据库操作均在 [Dispatchers.IO] 线程池执行，线程安全。
 *
 * @author Hsi Chu
 * @since V1.3.0
 */
@Singleton
class MessageFtsIndex @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val helper = MessageFtsDatabase(context)

    /**
     * 索引（写入或更新）一条消息。
     *
     * 以 [messageId] 作为主键做 upsert（冲突时替换），避免重复命中。
     *
     * @param serverId 消息所属服务器 ID
     * @param sessionId 消息所属会话 ID
     * @param messageId 消息唯一 ID
     * @param title 会话标题
     * @param content 消息正文文本
     */
    suspend fun index(
        serverId: String,
        sessionId: String,
        messageId: String,
        title: String,
        content: String,
    ) = withContext(Dispatchers.IO) {
        if (messageId.isBlank()) return@withContext
        helper.writableDatabase.insertWithOnConflict(
            TABLE_NAME,
            null,
            ContentValues().apply {
                put(COL_SERVER_ID, serverId)
                put(COL_SESSION_ID, sessionId)
                put(COL_MESSAGE_ID, messageId)
                put(COL_TITLE, title)
                put(COL_CONTENT, content)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    /**
     * 按关键词搜索消息，返回命中列表（按更新时间倒序）。
     *
     * @param query 用户输入的关键词
     * @param limit 返回命中条数上限，默认 50
     * @return 命中结果列表；查询词为空时返回空列表
     */
    suspend fun search(query: String, limit: Int = DEFAULT_LIMIT, serverId: String? = null): List<FtsHit> =
        withContext(Dispatchers.IO) {
        val keyword = query.trim()
        if (keyword.isBlank()) return@withContext emptyList()
        val like = "%${keyword.escapeLike()}%"
        val results = mutableListOf<FtsHit>()
        val args = buildList {
            add(like)
            add(like)
            if (serverId != null) add(serverId)
        }
        helper.readableDatabase.rawQuery(
            """
            SELECT $COL_SERVER_ID, $COL_SESSION_ID, $COL_MESSAGE_ID, $COL_TITLE, $COL_CONTENT
            FROM $TABLE_NAME
            WHERE ($COL_CONTENT LIKE ? ESCAPE '\' OR $COL_TITLE LIKE ? ESCAPE '\')
            ${if (serverId != null) "AND $COL_SERVER_ID = ?" else ""}
            ORDER BY $COL_MESSAGE_ID DESC
            LIMIT ${limit.coerceIn(1, MAX_LIMIT)}
            """.trimIndent(),
            args.toTypedArray(),
        ).use { cursor ->
            val serverIdx = cursor.getColumnIndexOrThrow(COL_SERVER_ID)
            val sessionIdx = cursor.getColumnIndexOrThrow(COL_SESSION_ID)
            val messageIdx = cursor.getColumnIndexOrThrow(COL_MESSAGE_ID)
            val titleIdx = cursor.getColumnIndexOrThrow(COL_TITLE)
            val contentIdx = cursor.getColumnIndexOrThrow(COL_CONTENT)
            while (cursor.moveToNext()) {
                val title = cursor.getString(titleIdx).orEmpty()
                val content = cursor.getString(contentIdx).orEmpty()
                val snippet = buildSnippet(content, keyword)
                results += FtsHit(
                    serverId = cursor.getString(serverIdx).orEmpty(),
                    sessionId = cursor.getString(sessionIdx).orEmpty(),
                    messageId = cursor.getString(messageIdx).orEmpty(),
                    title = title,
                    snippet = snippet.ifBlank { title },
                )
            }
        }
        results
    }

    /**
     * 删除指定会话下的全部索引。
     *
     * 用于会话被删除时清理其消息索引，避免残留失效数据。
     *
     * @param sessionId 要清理的会话 ID
     */
    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        if (sessionId.isBlank()) return@withContext
        helper.writableDatabase.delete(TABLE_NAME, "$COL_SESSION_ID = ?", arrayOf(sessionId))
    }

    /**
     * 清空全部索引。
     */
    suspend fun clear() = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(TABLE_NAME, null, null)
    }

    /** 在正文中定位关键词，截取前后各 [SNIPPET_CONTEXT] 个字符，并用 `<b>`/`</b>` 包裹关键词。 */
    private fun buildSnippet(content: String, keyword: String): String {
        val idx = content.indexOf(keyword, ignoreCase = true)
        if (idx < 0) return content.take(SNIPPET_MAX_CHARS)
        val start = (idx - SNIPPET_CONTEXT).coerceAtLeast(0)
        val end = (idx + keyword.length + SNIPPET_CONTEXT).coerceAtMost(content.length)
        val prefix = if (start > 0) SNIPPET_ELLIPSIS else ""
        val suffix = if (end < content.length) SNIPPET_ELLIPSIS else ""
        val before = content.substring(start, idx)
        val matched = content.substring(idx, idx + keyword.length)
        val after = content.substring(idx + keyword.length, end)
        return "$prefix$before$SNIPPET_OPEN$matched$SNIPPET_CLOSE$after$suffix"
    }

    /** 转义 LIKE 通配符（% 与 _），避免用户输入被当作通配符。 */
    private fun String.escapeLike(): String =
        replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private class MessageFtsDatabase(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                    $COL_SERVER_ID TEXT,
                    $COL_SESSION_ID TEXT,
                    $COL_MESSAGE_ID TEXT PRIMARY KEY,
                    $COL_TITLE TEXT,
                    $COL_CONTENT TEXT
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_message_fts_session ON $TABLE_NAME($COL_SESSION_ID)",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
            onCreate(db)
        }
    }

    companion object {
        /** 数据库文件名。 */
        const val DATABASE_NAME = "message_fts.db"

        private const val DATABASE_VERSION = 2
        private const val TABLE_NAME = "message_fts"
        private const val COL_SERVER_ID = "server_id"
        private const val COL_SESSION_ID = "session_id"
        private const val COL_MESSAGE_ID = "message_id"
        private const val COL_TITLE = "title"
        private const val COL_CONTENT = "content"

        /** snippet 命中词左侧包裹标记。 */
        private const val SNIPPET_OPEN = "<b>"

        /** snippet 命中词右侧包裹标记。 */
        private const val SNIPPET_CLOSE = "</b>"

        /** snippet 省略号。 */
        private const val SNIPPET_ELLIPSIS = "…"

        /** snippet 关键词前后保留的字符数。 */
        private const val SNIPPET_CONTEXT = 20

        /** 未命中时的摘要最大字符数。 */
        private const val SNIPPET_MAX_CHARS = 80

        /** 单次搜索默认返回条数。 */
        private const val DEFAULT_LIMIT = 50

        /** 单次搜索允许的最大返回条数。 */
        private const val MAX_LIMIT = 200
    }
}
