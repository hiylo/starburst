/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ParseToolFilesMetadataTest.kt
 * Date : 2026/09/26 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseToolFilesMetadataTest {

    @Test
    fun stringFilesDerivePaths() {
        val files = parseToolFilesMetadata(
            buildJsonArray {
                add(JsonPrimitive("A meta3.txt"))
                add(JsonPrimitive("M src/main.go"))
                add(JsonPrimitive("D old.txt"))
            },
        )
        assertEquals(3, files.size)
        assertEquals("meta3.txt", files[0]["relativePath"]?.let { (it as JsonPrimitive).contentOrNull })
        assertEquals("src/main.go", files[1]["filePath"]?.let { (it as JsonPrimitive).contentOrNull })
        assertEquals("old.txt", files[2]["relativePath"]?.let { (it as JsonPrimitive).contentOrNull })
    }

    @Test
    fun objectFilesPassThrough() {
        val obj = buildJsonObject {
            put("filePath", JsonPrimitive("a.txt"))
            put("relativePath", JsonPrimitive("a.txt"))
            put("additions", JsonPrimitive(5))
            put("deletions", JsonPrimitive(2))
        }
        val files = parseToolFilesMetadata(JsonArray(listOf(obj)))
        assertEquals(1, files.size)
        assertTrue(files[0] is JsonObject)
        assertEquals(5, files[0]["additions"]?.let { (it as JsonPrimitive).intOrNull })
    }

    @Test
    fun nonArrayOrEmptyReturnsEmpty() {
        assertTrue(parseToolFilesMetadata(JsonPrimitive("x")).isEmpty())
        assertTrue(parseToolFilesMetadata(null).isEmpty())
        assertTrue(parseToolFilesMetadata(JsonArray(emptyList())).isEmpty())
    }

    @Test
    fun mixedElementsSkipInvalid() {
        val files = parseToolFilesMetadata(
            buildJsonArray {
                add(JsonPrimitive("A ok.txt"))
                add(JsonPrimitive(123)) // 非字符串跳过
                add(JsonObject(emptyMap())) // 空对象原样透传
            },
        )
        assertEquals(2, files.size)
    }
}

class CleanToolOutputTextTest {

    @Test
    fun stripsShellMetadataBlock() {
        val out = "hello\nworld\n<shell_metadata><exit_code>127</exit_code></shell_metadata>"
        assertEquals("hello\nworld", cleanToolOutputText(out))
    }

    @Test
    fun stripsAnsiEscapes() {
        val out = "\u001B[31mred\u001B[0m text"
        assertEquals("red text", cleanToolOutputText(out))
    }

    @Test
    fun stripsFullMetadataBlockWithExtraLines() {
        val out = "out\n<shell_metadata>\ncwd: /x\n</shell_metadata>"
        assertEquals("out", cleanToolOutputText(out))
    }

    @Test
    fun leavesPlainOutputUntouched() {
        val out = "line one\nline two"
        assertEquals(out, cleanToolOutputText(out))
    }

    @Test
    fun resolvePatchStats_fallsBackToPatchTextWhenFileStatsAreZeroPlaceholders() {
        // agent apply_patch：files 为字符串数组 → parseToolFilesMetadata 填 0/0 占位。
        // 修复前整卡不显示 +/- 行数；修复后按 patch 文本统计出真实增删。
        val patch = buildString {
            appendLine("*** Begin Patch")
            appendLine("*** Update File: meta3.txt")
            appendLine("@@ ctx @@")
            appendLine("-old line one")
            appendLine("-old line two")
            appendLine("+new line one")
            appendLine("+new line two")
            appendLine("+new line three")
            appendLine("*** End Patch")
        }
        val files = parseToolFilesMetadata(
            JsonArray(listOf(JsonPrimitive("M meta3.txt"))),
        )
        val (additions, deletions) = resolvePatchStats(files, patch)
        assertEquals(3, additions)
        assertEquals(2, deletions)
    }

    @Test
    fun resolvePatchStats_prefersRealPerFileStatsWhenPresent() {
        // opencode 对象数组形态自带真实统计时，不再用 patch 文本重复推导。
        val files = listOf(
            buildJsonObject {
                put("filePath", JsonPrimitive("/w/a.txt"))
                put("additions", JsonPrimitive(7))
                put("deletions", JsonPrimitive(1))
            },
        )
        val patch = "*** Begin Patch\n+one\n+two\n*** End Patch"
        val (additions, deletions) = resolvePatchStats(files, patch)
        assertEquals(7, additions)
        assertEquals(1, deletions)
    }

    @Test
    fun countUnifiedPatchChanges_countsAddFileBodyAsAdditions() {
        val patch = buildString {
            appendLine("*** Begin Patch")
            appendLine("*** Add File: new.txt")
            appendLine("+alpha")
            appendLine("+beta")
            appendLine("*** End Patch")
        }
        assertEquals(2 to 0, countUnifiedPatchChanges(patch))
    }
}
