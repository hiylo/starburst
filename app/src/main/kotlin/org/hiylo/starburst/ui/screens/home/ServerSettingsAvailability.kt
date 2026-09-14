/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ServerSettingsAvailability.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.home

internal fun resolveServerSettingsReadyIds(
    readyIds: Set<String>,
    connectedIds: Set<String>,
    serverId: String,
    probeSucceeded: Boolean,
): Set<String> {
    return if (probeSucceeded && serverId in connectedIds) {
        readyIds + serverId
    } else {
        readyIds - serverId
    }
}
