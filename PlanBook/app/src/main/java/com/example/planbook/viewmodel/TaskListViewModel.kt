package com.example.planbook.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.planbook.data.PlanBookRepository
import com.example.planbook.model.Notebook
import com.example.planbook.model.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
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

    /** 选中日驱动源：任务观察管道按它切换日 */
    private val selectedDateState = MutableStateFlow(TaskListUiState().selectedDate)

    init {
        // 管道 1：主/子/活动子本状态
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
                }
        }
        // 管道 2：当日任务（三页联动）——tasks/task_completions 任何写操作自动重发，
        // 计划本页/复盘页的勾选在此即时反映
        viewModelScope.launch {
            combine(
                repository.getMasterNotebook(),
                repository.getSubNotebooksOfMaster(),
                selectedDateState
            ) { master, subs, date -> Triple(master, subs, date) }
                .flatMapLatest { (master, subs, date) ->
                    if (master == null) flowOf(emptyList())
                    else repository.observeExpandedTasks(
                        master.id,
                        subs.filter { it.isVisible }.map { it.id },
                        listOf(date)
                    )
                }
                .collect { tasks ->
                    _uiState.update { it.copy(tasks = tasks, loaded = true) }
                }
        }
        // 首次触发该日所在周的复盘待办生成（写库后观察管道自动带回）
        viewModelScope.launch {
            val master = repository.getMasterNotebookOnce() ?: return@launch
            repository.ensureAutoReviewTasks(master.id, selectedDateState.value.with(DayOfWeek.MONDAY))
        }
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        selectedDateState.value = date
        // 与旧逻辑保持一致：切换日期时按需生成该日所在周的复盘待办
        viewModelScope.launch {
            val master = repository.getMasterNotebookOnce() ?: return@launch
            repository.ensureAutoReviewTasks(master.id, date.with(DayOfWeek.MONDAY))
        }
    }

    fun toggleComplete(task: Task) {
        viewModelScope.launch {
            repository.toggleTaskComplete(task, _uiState.value.selectedDate.toString())
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
            _uiState.update { it.copy(showAddDialog = false, prefillTask = null) }
        }
    }
}
