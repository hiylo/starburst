/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : AboutViewModel.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import org.hiylo.starburst.data.update.UpdateRepository
import org.hiylo.starburst.data.update.UpdateState
import org.hiylo.starburst.data.update.AvailableUpdate
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
) : ViewModel() {
    val updateState: StateFlow<UpdateState> = updateRepository.state

    init {
        viewModelScope.launch { updateRepository.restore() }
    }

    fun checkForUpdates() {
        viewModelScope.launch { updateRepository.check(manual = true) }
    }

    fun prepareInstall(release: AvailableUpdate) {
        viewModelScope.launch { updateRepository.prepareInstall(release) }
    }

    fun installerLaunched() {
        updateRepository.markInstallerLaunched()
    }
}
