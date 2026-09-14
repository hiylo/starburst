/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : MessageIdGenerator.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import java.security.SecureRandom

internal object MessageIdGenerator {
    private const val RANDOM_LENGTH = 14
    private const val BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private val random = SecureRandom()
    private var lastTimestamp = 0L
    private var counter = 0

    @Synchronized
    fun next(timestamp: Long = System.currentTimeMillis()): String {
        if (timestamp != lastTimestamp) {
            lastTimestamp = timestamp
            counter = 0
        }
        counter = (counter + 1).coerceAtMost(0xFFF)
        val encoded = timestamp * 0x1000L + counter
        val time = encoded.toString(16).padStart(12, '0').takeLast(12)
        val suffix = buildString(RANDOM_LENGTH) {
            repeat(RANDOM_LENGTH) { append(BASE62[random.nextInt(BASE62.length)]) }
        }
        return "msg_${time}${suffix}"
    }
}
