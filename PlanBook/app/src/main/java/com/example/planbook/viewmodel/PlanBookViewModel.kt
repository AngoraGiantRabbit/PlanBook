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
    /** 主计划本（ADR-0004，全局唯一；复盘挂它） */
    val currentNotebook: Notebook? = null,
    /** 主计划本下的全部子计划本（含隐藏） */
    val notebooks: List<Notebook> = emptyList(),
    /** 活动子计划本（写操作目标；首页新建任务落到它） */
    val activeSubNotebook: Notebook? = null,
    val tasks: List<Task> = emptyList(),
    val weekStart: LocalDate = LocalDate.now().with(WeekFields.of(java.util.Locale.CHINA).dayOfWeek(), 1L),
    /** 周视图底部"灵活待办"区选中的某一天（默认今天） */
    val selectedDate: LocalDate = LocalDate.now(),
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
            combine(
                repository.getMasterNotebook(),
                repository.getSubNotebooksOfMaster(),
                repository.getActiveSubOfMaster()
            ) { master, subs, active -> Triple(master, subs, active) }
                .collect { (master, subs, active) ->
                    _uiState.update {
                        it.copy(currentNotebook = master, notebooks = subs, activeSubNotebook = active)
                    }
                    if (master != null) reloadTasks()
                }
        }
    }

    /** 用当前状态里的主计划本 + 子计划本集合重新加载周视图数据 */
    private fun reloadTasks() {
        val master = _uiState.value.currentNotebook ?: return
        val subIds = _uiState.value.notebooks.map { it.id }
        loadTasks(master.id, subIds, _uiState.value.weekStart)
    }

    private fun loadTasks(masterId: Long, subNotebookIds: List<Long>, weekStart: LocalDate) {
        viewModelScope.launch {
            try {
                repository.ensureAutoReviewTasks(masterId, weekStart)
                val tasks = repository.getExpandedTasksForWeek(masterId, subNotebookIds, weekStart)
                _uiState.update { it.copy(tasks = tasks) }
            } catch (e: Exception) {
                android.util.Log.e("PlanBook", "loadTasks 失败", e)
            }
        }
    }

    /** 首次启动空态：创建主计划本 + 第一个子计划本（ADR-0004 引导） */
    fun createNotebook(name: String) {
        viewModelScope.launch {
            repository.bootstrapNotebook(name)
        }
    }

    /** 新建子计划本（顶栏弹窗入口；颜色由调色板轮转分配） */
    fun createSubNotebook(name: String) {
        viewModelScope.launch {
            val master = repository.getMasterNotebookOnce() ?: return@launch
            repository.createSubNotebook(master.id, name)
        }
    }

    /** 切换活动子计划本（顶栏弹窗选择 = 换写操作目标） */
    fun switchNotebook(notebook: Notebook) {
        viewModelScope.launch {
            repository.setActiveSubNotebook(notebook.id)
        }
        _uiState.update { it.copy(showNotebookSelector = false) }
    }

    fun showNotebookSelector() {
        _uiState.update { it.copy(showNotebookSelector = true) }
    }

    fun hideNotebookSelector() {
        _uiState.update { it.copy(showNotebookSelector = false) }
    }

    /** 重命名子计划本（PRD 4.1.3） */
    fun renameNotebook(notebook: Notebook, newName: String) {
        viewModelScope.launch {
            repository.renameNotebook(notebook.copy(name = newName))
        }
    }

    /** 删除子计划本：至少保留一个；删活动子本时自动换活动（PRD 4.1） */
    fun deleteNotebook(notebook: Notebook) {
        viewModelScope.launch {
            if (_uiState.value.notebooks.size <= 1) return@launch
            repository.deleteNotebook(notebook)
        }
    }

    /** 检测两个子计划本的带时段任务冲突（PRD 4.1.4） */
    suspend fun detectMergeConflicts(a: Long, b: Long) =
        repository.detectMergeConflicts(a, b)

    /** 执行合并：产物为主计划本下的新子计划本（PRD 4.1.4） */
    fun executeMerge(a: Long, b: Long, newName: String, resolutions: Map<Long, com.example.planbook.model.MergeResolution>) {
        viewModelScope.launch {
            repository.executeMerge(a, b, newName, resolutions)
        }
    }

    fun showAddTaskDialog() {
        _uiState.update { it.copy(showAddTaskDialog = true, prefillTask = null) }
    }

    /** 点空白时段时调用：预填日期和时段，落到活动子计划本（PRD 4.2.2） */
    fun showAddTaskDialogWithPrefill(date: String, startHour: Int) {
        val prefill = Task(
            notebookId = _uiState.value.activeSubNotebook?.id ?: 0,
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
            reloadTasks()
            _uiState.update { it.copy(showAddTaskDialog = false, prefillTask = null) }
        }
    }

    /** 翻周：PRD 4.2.1 支持查看不同周 */
    fun changeWeek(weeksDelta: Long) {
        val newStart = _uiState.value.weekStart.plusWeeks(weeksDelta)
        // 选中日跟随移动（保持星期几不变）
        val newSelected = _uiState.value.selectedDate.plusWeeks(weeksDelta)
        _uiState.update { it.copy(weekStart = newStart, selectedDate = newSelected) }
        reloadTasks()
    }

    fun goToThisWeek() {
        val newStart = LocalDate.now().with(WeekFields.of(java.util.Locale.CHINA).dayOfWeek(), 1L)
        _uiState.update { it.copy(weekStart = newStart, selectedDate = LocalDate.now()) }
        reloadTasks()
    }

    /** 选择周视图底部的某一天（灵活待办按天显示） */
    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
            reloadTasks()
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
            reloadTasks()
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
            reloadTasks()
        }
    }

    fun toggleTaskComplete(task: Task) {
        viewModelScope.launch {
            // 展开后的任务 startDate 已是该天；长期任务 startDate 不变
            repository.toggleTaskComplete(task, task.startDate)
            reloadTasks()
        }
    }

    /** 点击任务：加载原始任务（避免展开后的单日 copy 覆盖原始区间），打开编辑器 */
    fun openTaskEditor(task: Task) {
        viewModelScope.launch {
            val original = repository.getTaskById(task.id)
            _uiState.update { it.copy(editingTask = original ?: task) }
        }
    }

    fun saveEditedTask(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
            reloadTasks()
            _uiState.update { it.copy(editingTask = null) }
        }
    }

    fun closeTaskEditor() {
        _uiState.update { it.copy(editingTask = null) }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            reloadTasks()
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
