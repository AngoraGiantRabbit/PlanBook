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
    val showAddTaskDialog: Boolean = false,
    val editingTask: Task? = null,
    /** #12：编辑中的任务是否属于导入子计划本（只读快照，禁编辑/删除） */
    val editingTaskReadOnly: Boolean = false,
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

    /** 翻周驱动源：任务观察管道按它切换周区间 */
    private val weekStartState = MutableStateFlow(PlanBookUiState().weekStart)

    init {
        // 管道 1：主计划本 / 子计划本 / 活动子本状态
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
                }
        }
        // 管道 2：周任务数据（三页联动）——tasks/task_completions 任何写操作自动重发，
        // 勾选、增删、导入、复盘生成在任一页面发生，此处即时刷新
        viewModelScope.launch {
            combine(
                repository.getMasterNotebook(),
                repository.getSubNotebooksOfMaster(),
                weekStartState
            ) { master, subs, weekStart -> Triple(master, subs, weekStart) }
                .flatMapLatest { (master, subs, weekStart) ->
                    if (master == null) flowOf(emptyList())
                    else repository.observeExpandedTasks(
                        master.id,
                        subs.filter { it.isVisible }.map { it.id },
                        (0..6).map { weekStart.plusDays(it.toLong()) }
                    )
                }
                .collect { tasks -> _uiState.update { it.copy(tasks = tasks) } }
        }
        // 首次触发惰性维护（过期标记/补色/复盘生成），其引发的写会回流管道 2
        viewModelScope.launch {
            val master = repository.getMasterNotebookOnce() ?: return@launch
            repository.ensureDerivedData(master.id, weekStartState.value)
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

    /** 切换活动子计划本（首页 chips 点按 = 换写操作目标，#9） */
    fun switchActiveSub(notebook: Notebook) {
        viewModelScope.launch {
            repository.setActiveSubNotebook(notebook.id)
        }
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
            _uiState.update { it.copy(showAddTaskDialog = false, prefillTask = null) }
        }
    }

    /** 翻周：PRD 4.2.1 支持查看不同周（任务数据由观察管道自动跟随） */
    fun changeWeek(weeksDelta: Long) {
        val newStart = _uiState.value.weekStart.plusWeeks(weeksDelta)
        applyWeek(newStart, _uiState.value.selectedDate.plusWeeks(weeksDelta))
    }

    fun goToThisWeek() {
        val newStart = LocalDate.now().with(WeekFields.of(java.util.Locale.CHINA).dayOfWeek(), 1L)
        applyWeek(newStart, LocalDate.now())
    }

    private fun applyWeek(newStart: LocalDate, newSelected: LocalDate) {
        _uiState.update { it.copy(weekStart = newStart, selectedDate = newSelected) }
        weekStartState.value = newStart
        // 新一周按需生成复盘待办（写库后观察管道自动带回）
        viewModelScope.launch {
            val master = repository.getMasterNotebookOnce() ?: return@launch
            repository.ensureAutoReviewTasks(master.id, newStart)
        }
    }

    /** 选择周视图底部的某一天（灵活待办按天显示） */
    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
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
        }
    }

    fun toggleTaskComplete(task: Task) {
        viewModelScope.launch {
            // 展开后的任务 startDate 已是该天；长期任务 startDate 不变
            repository.toggleTaskComplete(task, task.startDate)
        }
    }

    /** 点击任务：加载原始任务（避免展开后的单日 copy 覆盖原始区间），打开编辑器。
     *  #12：任务属于导入子计划本时标记只读（快照语义，禁编辑/删除，勾选完成不受限）。 */
    fun openTaskEditor(task: Task) {
        viewModelScope.launch {
            val original = repository.getTaskById(task.id)
            val resolved = original ?: task
            val readOnly = _uiState.value.notebooks.any { it.id == resolved.notebookId && it.isImported }
            _uiState.update { it.copy(editingTask = resolved, editingTaskReadOnly = readOnly) }
        }
    }

    fun saveEditedTask(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
            _uiState.update { it.copy(editingTask = null) }
        }
    }

    fun closeTaskEditor() {
        _uiState.update { it.copy(editingTask = null) }
    }

    /** 手动刷新：重新触发惰性维护（过期标记/补色/复盘生成收敛）；UI 由观察管道自动更新 */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val master = repository.getMasterNotebookOnce()
            master?.let { repository.ensureDerivedData(it.id, weekStartState.value) }
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
