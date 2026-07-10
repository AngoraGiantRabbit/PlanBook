package com.example.planbook.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.planbook.data.PlanBookRepository
import com.example.planbook.model.Notebook
import com.example.planbook.model.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** 待办列表 Tab 状态 */
data class TaskListUiState(
    val currentNotebook: Notebook? = null,
    val selectedDate: LocalDate = LocalDate.now(),
    val tasks: List<Task> = emptyList(),
    val loaded: Boolean = false
)

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val repository: PlanBookRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskListUiState())
    val uiState: StateFlow<TaskListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getCurrentNotebook().collect { notebook ->
                _uiState.update { it.copy(currentNotebook = notebook) }
                notebook?.let { loadDayTasks(it.id, _uiState.value.selectedDate) }
            }
        }
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        _uiState.value.currentNotebook?.let { loadDayTasks(it.id, date) }
    }

    private fun loadDayTasks(notebookId: Long, date: LocalDate) {
        _uiState.update { it.copy(loaded = false) }
        viewModelScope.launch {
            val tasks = repository.getTasksForDay(notebookId, date)
            _uiState.update { it.copy(tasks = tasks, loaded = true) }
        }
    }

    fun toggleComplete(task: Task) {
        viewModelScope.launch {
            repository.toggleTaskComplete(task)
            _uiState.value.currentNotebook?.let { loadDayTasks(it.id, _uiState.value.selectedDate) }
        }
    }

    /** 切月后重新加载（日期不变，只是日历视图切换） */
    fun refresh() {
        _uiState.value.currentNotebook?.let { loadDayTasks(it.id, _uiState.value.selectedDate) }
    }
}
