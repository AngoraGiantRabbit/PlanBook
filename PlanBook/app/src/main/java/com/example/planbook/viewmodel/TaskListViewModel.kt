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
    val prefillTask: Task? = null,
    /** 条目点击打开的编辑任务（原始任务，非展开副本） */
    val editingTask: Task? = null,
    /** 编辑中的任务是否属于导入子计划本（只读快照） */
    val editingTaskReadOnly: Boolean = false
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
        // 首次触发惰性维护：复盘待办生成 + 长期任务过期标记（ADR-0003/0006，幂等）
        viewModelScope.launch {
            val master = repository.getMasterNotebookOnce() ?: return@launch
            repository.ensureDerivedData(master.id, selectedDateState.value.with(DayOfWeek.MONDAY))
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

    /** 条目点击：加载原始任务（避免展开后的单日 copy 覆盖原始区间），打开编辑器 */
    fun openTaskEditor(task: Task) {
        viewModelScope.launch {
            val original = repository.getTaskById(task.id)
            val resolved = original ?: task
            val readOnly = _uiState.value.subNotebooks.any { it.id == resolved.notebookId && it.isImported }
            _uiState.update { it.copy(editingTask = resolved, editingTaskReadOnly = readOnly) }
        }
    }

    fun saveEditedTask(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
            _uiState.update { it.copy(editingTask = null) }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
            _uiState.update { it.copy(editingTask = null) }
        }
    }

    fun closeTaskEditor() {
        _uiState.update { it.copy(editingTask = null) }
    }
}
