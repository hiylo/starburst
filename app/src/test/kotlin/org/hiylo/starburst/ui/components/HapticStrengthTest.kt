/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : HapticStrengthTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class HapticStrengthTest {
    @Test
    fun parsesPersistedStrengthAndFallsBackToMedium() {
        assertEquals(HapticStrength.LIGHT, HapticStrength.from("light"))
        assertEquals(HapticStrength.MEDIUM, HapticStrength.from("medium"))
        assertEquals(HapticStrength.STRONG, HapticStrength.from("strong"))
        assertEquals(HapticStrength.MEDIUM, HapticStrength.from("unknown"))
    }
}
