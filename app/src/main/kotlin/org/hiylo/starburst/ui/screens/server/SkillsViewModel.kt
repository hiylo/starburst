/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SkillsViewModel.kt
 * Date : 2026/09/10 19:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.starburst.ui.screens.server

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.hiylo.starburst.data.api.OpenCodeApi
import org.hiylo.starburst.data.api.ServerConnection
import org.hiylo.starburst.data.api.listSkills
import org.hiylo.starburst.domain.model.Skill
import org.hiylo.starburst.logging.AppLogger as Log
import javax.inject.Inject

private const val TAG = "SkillsViewModel"

data class SkillsUiState(
    val skills: List<Skill> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class SkillsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
) : ViewModel() {
    private val conn = ServerConnection.from(
        savedStateHandle.get<String>("serverUrl").orEmpty(),
        savedStateHandle.get<String>("username").orEmpty(),
        savedStateHandle.get<String>("password").orEmpty().ifEmpty { null },
    )

    private val _uiState = MutableStateFlow(SkillsUiState())
    val uiState: StateFlow<SkillsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val skills = api.listSkills(conn).sortedBy { it.name.lowercase() }
                _uiState.update { it.copy(skills = skills, isLoading = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load skills", e)
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load skills") }
            }
        }
    }
}
