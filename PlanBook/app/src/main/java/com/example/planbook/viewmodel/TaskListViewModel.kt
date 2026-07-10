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
    val loaded: Boolean = false,
    val showAddDialog: Boolean = false,
    val prefillTask: Task? = null
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
            repository.toggleTaskComplete(task, _uiState.value.selectedDate.toString())
            _uiState.value.currentNotebook?.let { loadDayTasks(it.id, _uiState.value.selectedDate) }
        }
    }

    /** 点加号：预填选中日期新建任务 */
    fun showAddDialog() {
        val date = _uiState.value.selectedDate.toString()
        val prefill = Task(
            notebookId = _uiState.value.currentNotebook?.id ?: 0,
            title = "",
            type = com.example.planbook.model.TaskType.ONE_OFF,
            startDate = date,
            endDate = date
        )
        _uiState.update { it.copy(showAddDialog = true, prefillTask = prefill) }
    }

    fun hideAddDialog() {
        _uiState.update { it.copy(showAddDialog = false, prefillTask = null) }
    }

    fun addTask(task: Task) {
        viewModelScope.launch {
            repository.addTask(task)
            _uiState.value.currentNotebook?.let { loadDayTasks(it.id, _uiState.value.selectedDate) }
            _uiState.update { it.copy(showAddDialog = false, prefillTask = null) }
        }
    }
}
