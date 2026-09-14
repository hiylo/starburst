/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : DirectoryPathQueryTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.sessions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DirectoryPathQueryTest {
    @Test
    fun homePrefixExpandsToServerHome() {
        assertEquals(
            DirectoryPathQuery("/home/live", ""),
            parseDirectoryPathQuery("~/", "/home/live"),
        )
        assertEquals(
            DirectoryPathQuery("/home/live", "go"),
            parseDirectoryPathQuery("~/go", "/home/live"),
        )
    }

    @Test
    fun absolutePathSplitsIntoParentAndSegment() {
        assertEquals(DirectoryPathQuery("/", "usr"), parseDirectoryPathQuery("/usr", "/home/live"))
        assertEquals(DirectoryPathQuery("/usr", ""), parseDirectoryPathQuery("/usr/", "/home/live"))
        assertEquals(DirectoryPathQuery("/usr", "local"), parseDirectoryPathQuery("/usr/local", "/home/live"))
    }

    @Test
    fun plainNameRemainsFuzzySearch() {
        assertNull(parseDirectoryPathQuery("project", "/home/live"))
    }
}
