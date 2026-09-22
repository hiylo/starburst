/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : BackendSyncTransport.kt
 * Date : 2026/09/22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.starburst.data.sync

import io.ktor.client.plugins.ClientRequestException
import org.hiylo.starburst.data.api.BackendApi
import java.io.IOException

/**
 * 通过 starburst-backend 的 `/api/sync` 键值存储读写同步包。
 * 后端不做乐观锁校验，冲突检测交给 [decideSync] 的 revision 漂移逻辑。
 */
class BackendSyncTransport(
    private val backendUrl: String,
    private val token: String,
    private val key: String,
    private val api: BackendApi,
) : SyncTransport {
    override suspend fun read(): RemoteSyncFile? {
        return try {
            api.syncGetBundle(backendUrl, token, key)?.let { bundle ->
                RemoteSyncFile(
                    content = bundle.payload,
                    revision = bundle.revision.toString(),
                    resolvedEndpoint = resolvedEndpoint(),
                )
            }
        } catch (e: ClientRequestException) {
            throw SyncHttpException("Unable to read starburst-backend sync bundle (HTTP ${e.response.status.value})")
        } catch (e: IOException) {
            throw SyncHttpException("Unable to read starburst-backend sync bundle: ${e.message}")
        }
    }

    override suspend fun write(content: String, expectedRevision: String?, create: Boolean): String? {
        return try {
            api.syncPutBundle(backendUrl, token, key, content).toString()
        } catch (e: ClientRequestException) {
            throw SyncHttpException("Unable to write starburst-backend sync bundle (HTTP ${e.response.status.value})")
        } catch (e: IOException) {
            throw SyncHttpException("Unable to write starburst-backend sync bundle: ${e.message}")
        }
    }

    private fun resolvedEndpoint(): String = "${backendUrl.trimEnd('/')}/api/sync?key=$key"

    companion object {
        /** 整个 App 的同步包在 starburst-backend 上共用的固定 key。 */
        const val DEFAULT_KEY = "global"
    }
}
