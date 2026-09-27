/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : UnifiedDiffParserTest.kt
 * Date : 2026/09/24 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import org.junit.Assert.assertEquals
import org.junit.Test
class UnifiedDiffParserTest {

    private val sampleDiff = """
        diff --git a/foo.txt b/foo.txt
        index 1234567..89abcde 100644
        --- a/foo.txt
        +++ b/foo.txt
        @@ -1,3 +1,4 @@
         line one
        -line two
         line three
        +line four
        +line five
        diff --git a/README.md b/README.md
        new file mode 100644
        index 0000000..f00f00f
        --- /dev/null
        +++ b/README.md
        @@ -0,0 +1,2 @@
        +# Hello
        +Docs
        diff --git a/old.txt b/old.txt
        deleted file mode 100644
        index f00f00f..0000000
        --- a/old.txt
        +++ /dev/null
        @@ -1,1 +0,0 @@
        -gone
    """.trimIndent()

    @Test
    fun `parses three files with stats and status`() {
        val diffs = parseUnifiedDiff(sampleDiff)
        assertEquals(3, diffs.size)

        val modified = diffs[0]
        assertEquals("foo.txt", modified.file)
        assertEquals("modified", modified.status)
        assertEquals(2, modified.additions)
        assertEquals(1, modified.deletions)
        assertEquals("", modified.before)
        assertEquals("", modified.after)
        assert(modified.patch.contains("diff --git a/foo.txt"))

        val added = diffs[1]
        assertEquals("README.md", added.file)
        assertEquals("added", added.status)
        assertEquals(2, added.additions)
        assertEquals(0, added.deletions)

        val deleted = diffs[2]
        assertEquals("old.txt", deleted.file)
        assertEquals("deleted", deleted.status)
        assertEquals(0, deleted.additions)
        assertEquals(1, deleted.deletions)
    }

    @Test
    fun `empty input returns empty list`() {
        assertEquals(0, parseUnifiedDiff("").size)
        assertEquals(0, parseUnifiedDiff("   ").size)
    }

    @Test
    fun `handles file paths with spaces and slashes`() {
        val raw = """
            diff --git a/src/deep/path with spaces/file.txt b/src/deep/path with spaces/file.txt
            --- a/src/deep/path with spaces/file.txt
            +++ b/src/deep/path with spaces/file.txt
            @@ -1 +1 @@
            -old
            +new
        """.trimIndent()
        val diffs = parseUnifiedDiff(raw)
        assertEquals(1, diffs.size)
        assertEquals("src/deep/path with spaces/file.txt", diffs[0].file)
        assertEquals(1, diffs[0].additions)
        assertEquals(1, diffs[0].deletions)
    }
}
