/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : UnifiedPatchTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class UnifiedPatchTest {

    @Test
    fun countsChangesWithoutTreatingFileHeadersAsChanges() {
        val patch = """
            --- a/file.kt
            +++ b/file.kt
            @@ -1,2 +1,3 @@
            -old
            +new
            +extra
        """.trimIndent()

        assertEquals(2 to 1, countUnifiedPatchChanges(patch))
    }
}
