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
import java.time.temporal.WeekFields
import javax.inject.Inject

data class PlanBookUiState(
    val notebooks: List<Notebook> = emptyList(),
    val currentNotebook: Notebook? = null,
    val tasks: List<Task> = emptyList(),
    val weekStart: LocalDate = LocalDate.now().with(WeekFields.of(java.util.Locale.CHINA).dayOfWeek(), 1L),
    val showNotebookSelector: Boolean = false,
    val showAddTaskDialog: Boolean = false,
    val isLoading: Boolean = false
)

@HiltViewModel
class PlanBookViewModel @Inject constructor(
    private val repository: PlanBookRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlanBookUiState())
    val uiState: StateFlow<PlanBookUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllNotebooks().collect { notebooks ->
                _uiState.update { it.copy(notebooks = notebooks) }
            }
        }
        viewModelScope.launch {
            repository.getCurrentNotebook().collect { notebook ->
                _uiState.update { it.copy(currentNotebook = notebook) }
                notebook?.let { loadTasks(it.id, _uiState.value.weekStart) }
            }
        }
    }

    private fun loadTasks(notebookId: Long, weekStart: LocalDate) {
        viewModelScope.launch {
            repository.ensureAutoReviewTasks(notebookId, weekStart)
            val tasks = repository.getExpandedTasksForWeek(notebookId, weekStart)
            _uiState.update { it.copy(tasks = tasks) }
        }
    }

    fun createNotebook(name: String) {
        viewModelScope.launch {
            repository.createNotebook(name)
        }
    }

    fun switchNotebook(notebook: Notebook) {
        viewModelScope.launch {
            repository.switchCurrentNotebook(notebook.id)
        }
        _uiState.update { it.copy(showNotebookSelector = false) }
    }

    fun showNotebookSelector() {
        _uiState.update { it.copy(showNotebookSelector = true) }
    }

    fun hideNotebookSelector() {
        _uiState.update { it.copy(showNotebookSelector = false) }
    }

    fun showAddTaskDialog() {
        _uiState.update { it.copy(showAddTaskDialog = true) }
    }

    fun hideAddTaskDialog() {
        _uiState.update { it.copy(showAddTaskDialog = false) }
    }

    fun addTask(task: Task) {
        viewModelScope.launch {
            repository.addTask(task)
            _uiState.value.currentNotebook?.let { loadTasks(it.id, _uiState.value.weekStart) }
            _uiState.update { it.copy(showAddTaskDialog = false) }
        }
    }

    fun toggleTaskComplete(task: Task) {
        viewModelScope.launch {
            repository.toggleTaskComplete(task)
            _uiState.value.currentNotebook?.let { loadTasks(it.id, _uiState.value.weekStart) }
        }
    }

    fun onTaskClick(task: Task) {
        // TODO: 打开任务详情/编辑
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            _uiState.value.currentNotebook?.let { loadTasks(it.id, _uiState.value.weekStart) }
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
