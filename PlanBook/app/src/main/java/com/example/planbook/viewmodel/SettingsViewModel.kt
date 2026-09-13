package com.example.planbook.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.planbook.data.PlanBookRepository
import com.example.planbook.model.Notebook
import com.example.planbook.model.ReviewSetting
import com.example.planbook.model.ReviewType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    /** 主计划本（ADR-0004：复盘设置挂主计划本） */
    val currentNotebook: Notebook? = null,
    val reviewSettings: List<ReviewSetting> = emptyList()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: PlanBookRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getMasterNotebook().collect { master ->
                _uiState.update { it.copy(currentNotebook = master) }
                master?.let { loadSettings(it.id) }
            }
        }
    }

    private fun loadSettings(notebookId: Long) {
        viewModelScope.launch {
            repository.getReviewSettings(notebookId).collect { settings ->
                // 保证三类齐全（按 DAILY/WEEKLY/MONTHLY 固定顺序）
                val ordered = ReviewType.entries.mapNotNull { type ->
                    settings.firstOrNull { it.reviewType == type }
                }
                _uiState.update { it.copy(reviewSettings = ordered) }
            }
        }
    }

    fun toggleReview(type: ReviewType, enabled: Boolean) {
        val notebookId = _uiState.value.currentNotebook?.id ?: return
        viewModelScope.launch {
            repository.setReviewEnabled(notebookId, type, enabled)
        }
    }

    fun setReviewTime(type: ReviewType, start: String, end: String) {
        val setting = _uiState.value.reviewSettings.firstOrNull { it.reviewType == type } ?: return
        viewModelScope.launch {
            repository.saveReviewSetting(setting.copy(startTime = start, endTime = end))
        }
    }
}
