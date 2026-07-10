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
    val editingTask: Task? = null,
    /** 新建任务时的预填模板（点空白时段时预填日期+时段，PRD 4.2.2） */
    val prefillTask: Task? = null,
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

    /** 重命名计划本（PRD 4.1.3） */
    fun renameNotebook(notebook: Notebook, newName: String) {
        viewModelScope.launch {
            repository.renameNotebook(notebook.copy(name = newName))
        }
    }

    /** 删除计划本：至少保留一个；删除当前计划本时自动切换到第一个（PRD 4.1） */
    fun deleteNotebook(notebook: Notebook) {
        viewModelScope.launch {
            if (_uiState.value.notebooks.size <= 1) return@launch
            repository.deleteNotebook(notebook)
            // 若删除的是当前计划本，切到剩余的第一个
            if (_uiState.value.currentNotebook?.id == notebook.id) {
                val remaining = _uiState.value.notebooks.firstOrNull { it.id != notebook.id }
                remaining?.let { repository.switchCurrentNotebook(it.id) }
            }
        }
    }

    /** 检测两个计划本的带时段任务冲突（PRD 4.1.4） */
    suspend fun detectMergeConflicts(a: Long, b: Long) =
        repository.detectMergeConflicts(a, b)

    /** 执行合并（PRD 4.1.4） */
    fun executeMerge(a: Long, b: Long, newName: String, resolutions: Map<Long, com.example.planbook.model.MergeResolution>) {
        viewModelScope.launch {
            repository.executeMerge(a, b, newName, resolutions)
        }
    }

    fun showAddTaskDialog() {
        _uiState.update { it.copy(showAddTaskDialog = true, prefillTask = null) }
    }

    /** 点空白时段时调用：预填日期和时段（PRD 4.2.2） */
    fun showAddTaskDialogWithPrefill(date: String, startHour: Int) {
        val prefill = Task(
            notebookId = _uiState.value.currentNotebook?.id ?: 0,
            title = "",
            type = com.example.planbook.model.TaskType.ONE_OFF,
            startDate = date,
            endDate = date,
            startTime = "%02d:00".format(startHour),
            endTime = "%02d:00".format(startHour + 1)
        )
        _uiState.update { it.copy(showAddTaskDialog = true, prefillTask = prefill) }
    }

    fun hideAddTaskDialog() {
        _uiState.update { it.copy(showAddTaskDialog = false, prefillTask = null) }
    }

    fun addTask(task: Task) {
        viewModelScope.launch {
            repository.addTask(task)
            _uiState.value.currentNotebook?.let { loadTasks(it.id, _uiState.value.weekStart) }
            _uiState.update { it.copy(showAddTaskDialog = false, prefillTask = null) }
        }
    }

    /** 翻周：PRD 4.2.1 支持查看不同周 */
    fun changeWeek(weeksDelta: Long) {
        val newStart = _uiState.value.weekStart.plusWeeks(weeksDelta)
        _uiState.update { it.copy(weekStart = newStart) }
        _uiState.value.currentNotebook?.let { loadTasks(it.id, newStart) }
    }

    fun goToThisWeek() {
        val newStart = LocalDate.now().with(WeekFields.of(java.util.Locale.CHINA).dayOfWeek(), 1L)
        _uiState.update { it.copy(weekStart = newStart) }
        _uiState.value.currentNotebook?.let { loadTasks(it.id, newStart) }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
            _uiState.value.currentNotebook?.let { loadTasks(it.id, _uiState.value.weekStart) }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
            _uiState.value.currentNotebook?.let { loadTasks(it.id, _uiState.value.weekStart) }
            _uiState.update { it.copy(editingTask = null) }
        }
    }

    /** 临时/每日任务去掉时间变灵活（PRD 4.3.3） */
    fun stripTimeToFlex(task: Task) {
        viewModelScope.launch {
            repository.updateTask(
                task.copy(
                    startTime = null,
                    endTime = null,
                    type = com.example.planbook.model.TaskType.FLEX
                )
            )
            _uiState.value.currentNotebook?.let { loadTasks(it.id, _uiState.value.weekStart) }
        }
    }

    fun toggleTaskComplete(task: Task) {
        viewModelScope.launch {
            repository.toggleTaskComplete(task)
            _uiState.value.currentNotebook?.let { loadTasks(it.id, _uiState.value.weekStart) }
        }
    }

    /** 点击任务：加载原始任务（避免展开后的单日 copy 覆盖跨天范围），打开编辑器 */
    fun openTaskEditor(task: Task) {
        viewModelScope.launch {
            val original = repository.getTaskById(task.id)
            _uiState.update { it.copy(editingTask = original ?: task) }
        }
    }

    fun saveEditedTask(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
            _uiState.value.currentNotebook?.let { loadTasks(it.id, _uiState.value.weekStart) }
            _uiState.update { it.copy(editingTask = null) }
        }
    }

    fun closeTaskEditor() {
        _uiState.update { it.copy(editingTask = null) }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            _uiState.value.currentNotebook?.let { loadTasks(it.id, _uiState.value.weekStart) }
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
