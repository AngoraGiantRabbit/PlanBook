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
    /** 主计划本（ADR-0004） */
    val currentNotebook: Notebook? = null,
    /** 全部子计划本（查询用） */
    val subNotebooks: List<Notebook> = emptyList(),
    /** 活动子计划本（新建任务落到它） */
    val activeSubNotebook: Notebook? = null,
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
            combine(
                repository.getMasterNotebook(),
                repository.getSubNotebooksOfMaster(),
                repository.getActiveSubOfMaster()
            ) { master, subs, active -> Triple(master, subs, active) }
                .collect { (master, subs, active) ->
                    _uiState.update {
                        it.copy(currentNotebook = master, subNotebooks = subs, activeSubNotebook = active)
                    }
                    master?.let { loadDayTasks(it.id, subs.map { s -> s.id }, _uiState.value.selectedDate) }
                }
        }
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        val master = _uiState.value.currentNotebook ?: return
        loadDayTasks(master.id, _uiState.value.subNotebooks.map { it.id }, date)
    }

    private fun loadDayTasks(masterId: Long, subNotebookIds: List<Long>, date: LocalDate) {
        _uiState.update { it.copy(loaded = false) }
        viewModelScope.launch {
            val tasks = repository.getTasksForDay(masterId, subNotebookIds, date)
            _uiState.update { it.copy(tasks = tasks, loaded = true) }
        }
    }

    fun toggleComplete(task: Task) {
        viewModelScope.launch {
            repository.toggleTaskComplete(task, _uiState.value.selectedDate.toString())
            val master = _uiState.value.currentNotebook ?: return@launch
            loadDayTasks(master.id, _uiState.value.subNotebooks.map { it.id }, _uiState.value.selectedDate)
        }
    }

    /** 点加号：预填选中日期，落到活动子计划本 */
    fun showAddDialog() {
        val date = _uiState.value.selectedDate.toString()
        val prefill = Task(
            notebookId = _uiState.value.activeSubNotebook?.id ?: 0,
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
            val master = _uiState.value.currentNotebook ?: return@launch
            loadDayTasks(master.id, _uiState.value.subNotebooks.map { it.id }, _uiState.value.selectedDate)
            _uiState.update { it.copy(showAddDialog = false, prefillTask = null) }
        }
    }
}
